package com.example.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.example.demo.model.dto.MemberForm;
import com.example.demo.model.service.MemberService;

@Controller
public class MemberController {
    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/signup")
    public String signupForm(@ModelAttribute MemberForm memberForm) {
        return "signup";
    }

    @PostMapping("/signup")
    public String signup(@ModelAttribute MemberForm memberForm, Model model) {
        try {
            memberService.signup(memberForm);
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "signup";
        }
        return "redirect:/login?signup";
    }
}
