package com.example.demo.controller;

import java.util.List;

import com.example.demo.model.domain.TestDB;
import com.example.demo.model.service.TestService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DemoController {
    private final TestService testService;

    public DemoController(TestService testService) {
        this.testService = testService;
    }

    @GetMapping("/hello")
    public String hello(Model model) {
        model.addAttribute("data", "반갑습니다.");
        return "hello";
    }

    @GetMapping("/hello2")
    public String hello2(Model model) {
        model.addAttribute("name", "홍길동님.");
        model.addAttribute("greeting", "반갑습니다.");
        model.addAttribute("today", "오늘.");
        model.addAttribute("weather", "날씨는.");
        model.addAttribute("condition", "매우 좋습니다.");
        return "hello2";
    }

    @GetMapping("/testdb")
    public String getAllTestDBs(Model model) {
        // 24~27페이지: 이름으로 사용자 한 명 조회
        // TestDB test = testService.findByName("홍길동");
        // model.addAttribute("data4", test);

        // 28~29페이지: 전체 사용자를 조회하여 반복 출력
        List<TestDB> users = testService.findAll();
        model.addAttribute("users", users);
        return "testdb";
    }
}
