package com.grid07.api.dto;

import com.grid07.api.entity.Comment;
import com.grid07.api.entity.Post;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

public class Dtos {

    @Data
    public static class CreatePostRequest {
        @NotNull
        private Long authorId;
        @NotNull
        private Post.AuthorType authorType;
        @NotBlank
        private String content;
    }

    @Data
    public static class CreateCommentRequest {
        @NotNull
        private Long authorId;
        @NotNull
        private Comment.AuthorType authorType;
        @NotBlank
        private String content;
        private int depthLevel = 1;
        private Long botId;
        private Long humanOwnerId;
    }

    @Data
    public static class LikePostRequest {
        @NotNull
        private Long userId;
    }

    @Data
    public static class PostResponse {
        private Long id;
        private Long authorId;
        private Post.AuthorType authorType;
        private String content;
        private LocalDateTime createdAt;
        private long viralityScore;
    }

    @Data
    public static class CommentResponse {
        private Long id;
        private Long postId;
        private Long authorId;
        private String content;
        private int depthLevel;
        private LocalDateTime createdAt;
    }

    @Data
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public static <T> ApiResponse<T> ok(T data) {
            ApiResponse<T> r = new ApiResponse<>();
            r.success = true;
            r.data = data;
            return r;
        }

        public static <T> ApiResponse<T> ok(String message, T data) {
            ApiResponse<T> r = new ApiResponse<>();
            r.success = true;
            r.message = message;
            r.data = data;
            return r;
        }

        public static <T> ApiResponse<T> error(String message) {
            ApiResponse<T> r = new ApiResponse<>();
            r.success = false;
            r.message = message;
            return r;
        }
    }
}
