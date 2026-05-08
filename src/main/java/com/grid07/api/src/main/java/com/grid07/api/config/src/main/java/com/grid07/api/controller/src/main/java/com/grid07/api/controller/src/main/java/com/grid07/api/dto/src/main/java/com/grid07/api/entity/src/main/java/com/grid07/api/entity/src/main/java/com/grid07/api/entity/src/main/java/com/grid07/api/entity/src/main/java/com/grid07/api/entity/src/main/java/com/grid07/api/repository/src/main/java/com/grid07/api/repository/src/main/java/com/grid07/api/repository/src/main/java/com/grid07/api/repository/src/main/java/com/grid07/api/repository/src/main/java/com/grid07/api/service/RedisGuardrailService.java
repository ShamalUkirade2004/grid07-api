package com.grid07.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisGuardrailService {

    private final StringRedisTemplate redisTemplate;

    private static final String VIRALITY_KEY     = "post:%d:virality_score";
    private static final String BOT_COUNT_KEY    = "post:%d:bot_count";
    private static final String COOLDOWN_KEY     = "cooldown:bot_%d:human_%d";
    private static final String NOTIF_COOLDOWN   = "notif_cooldown:user_%d";
    private static final String PENDING_NOTIFS   = "user:%d:pending_notifs";

    public static final int  MAX_BOT_REPLIES       = 100;
    public static final int  MAX_DEPTH_LEVEL        = 20;
    public static final long COOLDOWN_MINUTES       = 10;
    public static final long NOTIF_COOLDOWN_MINUTES = 15;

    private static final DefaultRedisScript<Long> ATOMIC_BOT_INCREMENT_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local current = redis.call('GET', KEYS[1])
                    if current and tonumber(current) >= tonumber(ARGV[1]) then
                        return -1
                    end
                    return redis.call('INCR', KEYS[1])
                    """,
                    Long.class
            );

    public long incrementViralityScore(Long postId, InteractionType type) {
        String key = String.format(VIRALITY_KEY, postId);
        Long newScore = redisTemplate.opsForValue().increment(key, type.getPoints());
        log.debug("Virality score for post {} incremented by {} → {}", postId, type.getPoints(), newScore);
        return newScore != null ? newScore : 0L;
    }

    public long getViralityScore(Long postId) {
        String key = String.format(VIRALITY_KEY, postId);
        String val = redisTemplate.opsForValue().get(key);
        return val != null ? Long.parseLong(val) : 0L;
    }

    public boolean tryIncrementBotCount(Long postId) {
        String key = String.format(BOT_COUNT_KEY, postId);
        Long result = redisTemplate.execute(
                ATOMIC_BOT_INCREMENT_SCRIPT,
                List.of(key),
                String.valueOf(MAX_BOT_REPLIES)
        );
        if (result == null || result == -1L) {
            log.warn("Horizontal cap reached for post {}. Bot reply rejected.", postId);
            return false;
        }
        log.debug("Bot count for post {} → {}", postId, result);
        return true;
    }

    public boolean isDepthAllowed(int depthLevel) {
        return depthLevel <= MAX_DEPTH_LEVEL;
    }

    public boolean tryAcquireCooldown(Long botId, Long humanId) {
        String key = String.format(COOLDOWN_KEY, botId, humanId);
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofMinutes(COOLDOWN_MINUTES));
        if (Boolean.FALSE.equals(acquired)) {
            log.warn("Cooldown active: bot {} cannot interact with human {}.", botId, humanId);
            return false;
        }
        return true;
    }

    public void handleNotification(Long userId, String notificationMessage) {
        String cooldownKey = String.format(NOTIF_COOLDOWN, userId);
        String pendingKey  = String.format(PENDING_NOTIFS, userId);

        Boolean cooldownActive = redisTemplate.hasKey(cooldownKey);

        if (Boolean.TRUE.equals(cooldownActive)) {
            redisTemplate.opsForList().rightPush(pendingKey, notificationMessage);
            log.debug("Notification queued for user {}: {}", userId, notificationMessage);
        } else {
            log.info("Push Notification Sent to User {}: {}", userId, notificationMessage);
            redisTempl
