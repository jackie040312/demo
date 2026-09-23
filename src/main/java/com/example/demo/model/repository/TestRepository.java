package com.example.demo.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.model.domain.TestDB;

@Repository
public interface TestRepository extends JpaRepository<TestDB, Long> {
    TestDB findByName(String name);
}
