package com.example.demo.model.dto;

/** to에는 표시 이름이 아닌 로그인 아이디를 넣는다. */
public record Notification(String to, String text, Long postId) {
}
