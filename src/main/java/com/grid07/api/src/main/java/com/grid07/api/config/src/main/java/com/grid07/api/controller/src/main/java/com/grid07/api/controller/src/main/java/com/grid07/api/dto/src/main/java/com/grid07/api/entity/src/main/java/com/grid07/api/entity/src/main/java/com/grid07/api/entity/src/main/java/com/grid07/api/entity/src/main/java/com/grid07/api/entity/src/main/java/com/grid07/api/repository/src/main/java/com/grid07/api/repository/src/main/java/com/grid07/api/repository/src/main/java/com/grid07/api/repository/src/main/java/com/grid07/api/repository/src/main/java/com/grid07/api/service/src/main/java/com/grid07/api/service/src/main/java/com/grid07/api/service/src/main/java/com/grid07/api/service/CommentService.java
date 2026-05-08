package com.grid07.api.service;

import com.grid07.api.dto.Dtos;
import com.grid07.api.entity.Comment;
import com.grid07.api.entity.Post;
import com.grid07.api.exception.GuardrailException;
import com.grid07.api.repository.BotRepository;
import com.grid07.api.repository.CommentRepository;
import com.grid07.api.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final BotRepository botRepository;
    private final RedisGuardrailService redisGuardrailService;

    @Transactional
    public Dtos.CommentResponse addComment(Long postId, Dtos.CreateCommentRequest request) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));

        if (request.getAuthorType() == Comment.AuthorType.BOT) {
            enforceGuardrailsForBot(postId, request);
        }

        Comment comment = Comment.builder()
                .postId(postId)
                .authorId(request.getAuthorId())
                .authorType(request.getAuthorType())
                .content(request.getContent())
                .depthLevel(request.getDepthLevel())
                .build();

        Comment saved = commentRepository.save(comment);
        log.info("Comment saved: id={}, postId={}, authorType={}, depth={}",
                saved.getId(), postId, saved.getAuthorType(), saved.getDepthLevel());

        if (request.getAuthorType() == Comment.AuthorType.BOT) {
            redisGuardrailService.incrementViralityScore(
                    postId, RedisGuardrailService.InteractionType.BOT_REPLY);

            if (post.getAuthorType() == Post.AuthorType.USER) {
                String botName = botRepository.findById(request.getAuthorId())
                        .map(b -> b.getName())
                        .orElse("Unknown Bot");
                String notifMessage = "Bot " + botName + " replied to your post";
                redisGuardrailService.handleNotification(post.getAuthorId(), notifMessage);
            }
        } else {
            redisGuardrailService.incrementViralityScore(
                    postId, RedisGuardrailService.InteractionType.HUMAN_COMMENT);
        }

        return buildResponse(saved);
    }

    private void enforceGuardrailsForBot(Long postId, Dtos.CreateCommentRequest request) {
        if (!redisGuardrailService.isDepthAllowed(request.getDepthLevel())) {
            log.warn("Vertical cap: depth {} exceeds max {} for post {}",
                    request.getDepthLevel(), RedisGuardrailService.MAX_DEPTH_LEVEL, postId);
            throw new GuardrailException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Vertical cap reached: comment depth exceeds maximum allowed level of "
                            + RedisGuardrailService.MAX_DEPTH_LEVEL
            );
        }

        if (request.getBotId() != null && request.getHumanOwnerId() != null) {
            if (!redisGuardrailService.tryAcquireCooldown(request.getBotId(), request.getHumanOwnerId())) {
                throw new GuardrailException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Cooldown active: Bot " + request.getBotId() + " cannot interact with User "
                                + request.getHumanOwnerId() + " for another "
                                + RedisGuardrailService.COOLDOWN_MINUTES + " minutes."
                );
            }
        }

        if (!redisGuardrailService.tryIncrementBotCount(postId)) {
            throw new GuardrailException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Horizontal cap reached: post " + postId + " already has "
                            + RedisGuardrailService.MAX_BOT_REPLIES + " bot replies."
            );
        }
    }

    private Dtos.CommentResponse buildResponse(Comment c) {
        Dtos.CommentResponse r = new Dtos.CommentResponse();
        r.setId(c.getId());
        r.setPostId(c.getPostId());
        r.setAuthorId(c.getAuthorId());
        r.setContent(c.getContent());
        r.setDepthLevel(c.getDepthLevel());
        r.setCreatedAt(c.getCreatedAt());
        return r;
    }
}
