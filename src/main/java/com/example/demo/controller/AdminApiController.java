package com.example.demo.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.model.domain.Member;
import com.example.demo.model.dto.MemberForm;
import com.example.demo.model.dto.MemberResponse;
import com.example.demo.model.dto.RoleRequest;
import com.example.demo.model.service.MemberService;

/** 관리자 회원 관리 REST API. */
@RestController
@RequestMapping("/api/admin/members")
public class AdminApiController {
    private final MemberService memberService;

    public AdminApiController(MemberService memberService) {
        this.memberService = memberService;
    }

    /** 회원 목록: GET /api/admin/members */
    @GetMapping
    public List<MemberResponse> list(@RequestParam(required = false) String keyword) {
        List<Member> members = memberService.findAll(keyword);
        return members.stream().map(MemberResponse::from).toList();
    }

    /** 회원 한 명: GET /api/admin/members/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<MemberResponse> get(@PathVariable Long id) {
        return memberService.findById(id)
            .map(member -> ResponseEntity.ok(MemberResponse.from(member)))
            .orElse(ResponseEntity.notFound().build());
    }

    /** 회원 등록: POST /api/admin/members (JSON 본문을 MemberForm으로 변환). */
    @PostMapping
    public ResponseEntity<MemberResponse> create(@RequestBody MemberForm form) {
        Member saved = memberService.signup(form);
        return ResponseEntity
            .created(java.net.URI.create("/api/admin/members/" + saved.getId()))
            .body(MemberResponse.from(saved));
    }

    /** 회원 권한 변경: PATCH /api/admin/members/{id}/role */
    @PatchMapping("/{id}/role")
    public ResponseEntity<MemberResponse> changeRole(
            @PathVariable Long id, @RequestBody RoleRequest request) {
        Member member = memberService.changeRole(id, request.role());
        return ResponseEntity.ok(MemberResponse.from(member));
    }

    /** 회원 삭제: DELETE /api/admin/members/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        memberService.deleteMember(id);
        return ResponseEntity.noContent().build();
    }
}
