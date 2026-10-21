package com.example.demo.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.demo.model.domain.Member;
import com.example.demo.model.service.MemberService;

/**
 * 6주차 관리자 전용 화면과 회원 관리 요청을 담당할 컨트롤러.
 * 세부 URL과 기능은 다음 실습 단계에서 추가한다.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {
    private final MemberService memberService;

    public AdminController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/members")
    public String members(Model model) {
        List<Member> members = memberService.findAll();
        model.addAttribute("members", members);
        return "admin/members";
    }

    /** 7주차 제공 화면: JavaScript가 REST API를 호출해 회원 목록을 그린다. */
    @GetMapping("/members-api")
    public String membersApi() {
        return "admin/members-api";
    }

    @PostMapping("/members/{id}/role")
    public String changeRole(@PathVariable Long id, @RequestParam String role,
            RedirectAttributes redirect) {
        try {
            memberService.changeRole(id, role);
            redirect.addFlashAttribute("message", "권한을 " + role + " (으)로 변경했습니다.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/members";
    }

    @PostMapping("/members/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            memberService.deleteMember(id);
            redirect.addFlashAttribute("message", "회원을 삭제했습니다.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/members";
    }
}
