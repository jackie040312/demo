package com.example.demo.model.dto;

/** 권한 변경 요청 JSON인 {"role":"ADMIN"}을 받는 DTO. */
public record RoleRequest(String role) {
}
