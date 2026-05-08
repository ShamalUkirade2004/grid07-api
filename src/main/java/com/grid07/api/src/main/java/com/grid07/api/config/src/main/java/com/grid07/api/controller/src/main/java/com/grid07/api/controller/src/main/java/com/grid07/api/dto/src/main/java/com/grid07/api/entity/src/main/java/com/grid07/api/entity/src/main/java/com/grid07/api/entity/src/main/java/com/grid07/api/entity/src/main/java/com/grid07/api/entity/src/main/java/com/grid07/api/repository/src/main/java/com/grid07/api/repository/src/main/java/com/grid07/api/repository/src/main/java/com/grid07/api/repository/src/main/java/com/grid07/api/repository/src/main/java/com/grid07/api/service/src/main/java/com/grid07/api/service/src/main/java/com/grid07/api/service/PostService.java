package com.grid07.api.service;

import com.grid07.api.dto.Dtos;
import com.grid07.api.entity.Post;
import com.grid07.api.entity.PostLike;
import com.grid07.api.repository.PostLikeRepository;
import com.grid07.api.repository.PostRepository;
import com.grid07.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserRepository userRepository;
    private final RedisGuardrailService redisGuardrailService;

    @Transactional
    public Dtos.PostResponse createPost(Dtos.CreatePostRequest request) {
        Post post = Post.builder()
                .authorId(request.getAuthorId())
                .authorType(request.getAuthorType())
                .content(request.getContent())
                .build();

        Post saved = postRepository.save(post);
        log.info("Post created: id={}, authorId={}, authorType={}",
                saved.getId(), saved.getAuthorId(), saved.getAuthorType());

        Dtos.PostResponse response = new Dtos.PostResponse();
        response.setId(saved.getId());
        response.setAuthorId(saved.getAuthorId());
        response.setAuthorType(saved.getAuthorType());
        response.setContent(saved.getContent());
        response.setCreatedAt(saved.getCreatedAt());
        response.setViralityScore(0L);
        return response;
    }

    @Transactional
    public Dtos.PostResponse likePost(Long postId, Dtos.LikePostRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));

        if (postLikeRepository.existsByPostIdAndUserId(postId, request.getUserId())) {
            throw new IllegalStateException("User already liked this post.");
        }

        PostLike like = PostLike.builder()
                .postId(postId)
                .userId(request.getUserId())
                .build();
        postLikeRepository.save(like);

        long newScore = redisGuardrailService.incrementViralityScore(
                postId, RedisGuardrailService.InteractionType.HUMAN_LIKE);

        log.info("Post {} liked by user {}. New virality score: {}", postId, request.getUserId(), newScore);

        Dtos.PostResponse response = new Dtos.PostResponse();
        response.setId(post.getId());
        response.setAuthorId(post.getAuthorId());
        response.setAuthorType(post.getAuthorType());
        response.setContent(post.getContent());
        response.setCreatedAt(post.getCreatedAt());
        response.setViralityScore(newScore);
        return response;
    }

    public Post getPostOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
    }
}
