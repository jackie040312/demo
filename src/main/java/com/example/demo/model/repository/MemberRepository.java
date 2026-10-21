package com.example.demo.model.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.model.domain.Member;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByUsername(String username);
    boolean existsByUsername(String username);
    List<Member> findByUsernameContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByIdAsc(
        String usernameKeyword, String nameKeyword);
}
