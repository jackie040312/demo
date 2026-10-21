package com.example.demo.model.dto;

import com.example.demo.model.domain.Member;

/**
 * API 응답용 DTO.
 * DB 엔티티를 그대로 내보내지 않고 공개해도 되는 회원 정보만 JSON으로 반환한다.
 */
public record MemberResponse(Long id, String username, String name, String role) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
            member.getId(),
            member.getUsername(),
            member.getName(),
            member.getRole());
    }
}
