package com.example.demo.model.dto;

import com.example.demo.model.domain.Post;

/** 발행 시점의 인원을 담아 커밋 후에도 같은 값을 보낸다. */
public record SeatMessage(Long postId, int appliedCount, int capacity, int remaining) {
    public static SeatMessage from(Post post) {
        return new SeatMessage(post.getId(), post.getAppliedCount(), post.getCapacity(), post.getRemaining());
    }
}
