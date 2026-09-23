package com.example.demo.model.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.model.domain.TestDB;
import com.example.demo.model.repository.TestRepository;

@Service
public class TestService {
    private final TestRepository testRepository;

    public TestService(TestRepository testRepository) {
        this.testRepository = testRepository;
    }

    // 23페이지의 단일 사용자 조회 기능
    public TestDB findByName(String name) {
        return testRepository.findByName(name);
    }

    public List<TestDB> findAll() {
        return testRepository.findAll();
    }
}
