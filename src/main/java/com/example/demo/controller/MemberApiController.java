package com.example.demo.controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.model.domain.Member;
import com.example.demo.model.dto.MemberResponse;
import com.example.demo.model.service.MemberService;

/** HTML 화면 대신 JSON 데이터를 응답하는 7주차 REST API 컨트롤러. */
@RestController
@RequestMapping("/api")
public class MemberApiController {
    private final MemberService memberService;

    public MemberApiController(MemberService memberService) {
        this.memberService = memberService;
    }

    /** 첫 번째 공개 API: GET /api/hello */
    @GetMapping("/hello")
    public Map<String, Object> hello() {
        return Map.of(
            "message", "안녕하세요, REST API!",
            "time", LocalDateTime.now().withNano(0));
    }

    /** 로그인한 회원의 정보만 응답하며 비밀번호 해시는 포함하지 않는다. */
    @GetMapping("/me")
    public MemberResponse me(Principal principal) {
        Member member = memberService.findByUsername(principal.getName());
        return MemberResponse.from(member);
    }
}
