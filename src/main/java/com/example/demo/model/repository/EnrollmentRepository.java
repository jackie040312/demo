package com.example.demo.model.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.model.domain.Enrollment;
import com.example.demo.model.domain.Member;
import com.example.demo.model.domain.Post;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    boolean existsByMemberAndPost(Member member, Post post);
    boolean existsByPost(Post post);
    boolean existsByMember(Member member);
    List<Enrollment> findByMember_UsernameOrderByIdDesc(String username);
}
