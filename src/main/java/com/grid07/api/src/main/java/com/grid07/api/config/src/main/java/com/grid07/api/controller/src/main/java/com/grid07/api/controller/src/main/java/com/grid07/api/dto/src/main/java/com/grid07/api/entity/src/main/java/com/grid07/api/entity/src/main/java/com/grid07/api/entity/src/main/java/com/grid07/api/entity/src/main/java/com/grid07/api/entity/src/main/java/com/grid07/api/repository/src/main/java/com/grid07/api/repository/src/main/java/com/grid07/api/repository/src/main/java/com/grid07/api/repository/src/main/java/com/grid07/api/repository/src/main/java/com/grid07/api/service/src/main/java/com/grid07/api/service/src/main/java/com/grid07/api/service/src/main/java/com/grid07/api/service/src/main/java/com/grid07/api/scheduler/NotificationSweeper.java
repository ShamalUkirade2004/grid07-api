package com.grid07.api.scheduler;

import com.grid07.api.service.RedisGuardrailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationSweeper {

    private final RedisGuardrailService redisGuardrailService;

    @Scheduled(cron = "0 */5 * * * *")
    public void sweepPendingNotifications() {
        log.info("=== NotificationSweeper: starting sweep ===");

        Set<String> keys = redisGuardrailService.getAllPendingNotifKeys();
        if (keys == null || keys.isEmpty()) {
            log.info("NotificationSweeper: no pending notifications found.");
            return;
        }

        for (String key : keys) {
            Long userId = extractUserId(key);
            if (userId == null) continue;

            List<String> pendingNotifs = redisGuardrailService.popAllPendingNotifications(userId);
            if (pendingNotifs.isEmpty()) continue;

            String firstNotif = pendingNotifs.get(0);
            int totalCount = pendingNotifs.size();

            String summary;
            if (totalCount == 1) {
                summary = firstNotif;
            } else {
                String botPart = firstNotif.contains("Bot ") ?
                        firstNotif.substring(0, firstNotif.indexOf(" replied")) : firstNotif;
                int others = totalCount - 1;
                summary = botPart + " and [" + others + "] others interacted with your posts.";
            }

            log.info("Summarized Push Notification for User {}: {}", userId, summary);
        }

        log.info("=== NotificationSweeper: sweep complete ===");
    }

    private Long extractUserId(String key) {
        try {
            String[] parts = key.split(":");
            return Long.parseLong(parts[1]);
        } catch (Exception e) {
            log.error("Failed to extract userId from key: {}", key);
            return null;
        }
    }
}
