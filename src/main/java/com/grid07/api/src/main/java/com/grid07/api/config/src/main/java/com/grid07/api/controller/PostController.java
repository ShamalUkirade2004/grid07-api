package com.grid07.api.controller;

import com.grid07.api.dto.Dtos;
import com.grid07.api.service.CommentService;
import com.grid07.api.service.PostService;
import com.grid07.api.service.RedisGuardrailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Slf4j
public class PostController {

    private final PostService postService;
    private final CommentService commentService;
    private final RedisGuardrailService redisGuardrailService;

    @PostMapping
    public ResponseEntity<Dtos.ApiResponse<Dtos.PostResponse>> createPost(
            @Valid @RequestBody Dtos.CreatePostRequest request) {
        Dtos.PostResponse post = postService.createPost(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Dtos.ApiResponse.ok("Post created successfully.", post));
    }

    @PostMapping("/{postId}/comments")
    public ResponseEntity<Dtos.ApiResponse<Dtos.CommentResponse>> addComment(
            @PathVariable Long postId,
            @Valid @RequestBody Dtos.CreateCommentRequest request) {
        Dtos.CommentResponse comment = commentService.addComment(postId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Dtos.ApiResponse.ok("Comment added successfully.", comment));
    }

    @PostMapping("/{postId}/like")
    public ResponseEntity<Dtos.ApiResponse<Dtos.PostResponse>> likePost(
            @PathVariable Long postId,
            @Valid @RequestBody Dtos.LikePostRequest request) {
        Dtos.PostResponse post = postService.likePost(postId, request);
        return ResponseEntity.ok(Dtos.ApiResponse.ok("Post liked successfully.", post));
    }

    @GetMapping("/{postId}/virality")
    public ResponseEntity<Dtos.ApiResponse<Long>> getViralityScore(@PathVariable Long postId) {
        long score = redisGuardrailService.getViralityScore(postId);
        return ResponseEntity.ok(Dtos.ApiResponse.ok("Virality score retrieved.", score));
    }
}
