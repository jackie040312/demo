package com.example.demo.model.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.model.domain.Enrollment;
import com.example.demo.model.domain.Member;
import com.example.demo.model.domain.Post;
import com.example.demo.model.repository.EnrollmentRepository;
import com.example.demo.model.repository.MemberRepository;
import com.example.demo.model.repository.PostRepository;

@Service
public class EnrollmentService {
    private final EnrollmentRepository enrollmentRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;

    public EnrollmentService(EnrollmentRepository enrollmentRepository, PostRepository postRepository,
            MemberRepository memberRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.postRepository = postRepository;
        this.memberRepository = memberRepository;
    }

    public List<Enrollment> findMine(String username) {
        return enrollmentRepository.findByMember_UsernameOrderByIdDesc(username);
    }

    /** 바구니 전체를 한 트랜잭션으로 확정한다. 중간에 실패하면 앞선 신청도 롤백한다. */
    @Transactional
    public int confirm(String username, Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            throw new IllegalStateException("바구니가 비어 있습니다.");
        }
        Member member = memberRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalStateException("회원이 존재하지 않습니다."));
        Set<Long> ids = new LinkedHashSet<>(postIds);
        for (Long postId : ids) {
            Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalStateException("삭제된 모집글이 바구니에 있습니다."));
            if (!post.isRecruit()) {
                throw new IllegalStateException("일반 글은 신청할 수 없습니다 : " + post.getTitle());
            }
            if (enrollmentRepository.existsByMemberAndPost(member, post)) {
                throw new IllegalStateException("이미 신청한 모집글입니다 : " + post.getTitle());
            }
            if (post.isFull()) {
                throw new IllegalStateException("정원이 마감되었습니다 : " + post.getTitle());
            }
            Enrollment enrollment = new Enrollment();
            enrollment.setMember(member);
            enrollment.setPost(post);
            enrollment.setCreatedAt(LocalDateTime.now());
            enrollmentRepository.save(enrollment);
            post.setAppliedCount(post.getAppliedCount() + 1);
            postRepository.save(post);
        }
        return ids.size();
    }

    /** 본인 신청만 취소하고, 변경 감지로 모집글의 신청 인원을 줄인다. */
    @Transactional
    public void cancel(Long id, String username) {
        Enrollment enrollment = enrollmentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "신청 내역이 존재하지 않습니다."));
        if (!enrollment.getMember().getUsername().equals(username)) {
            throw new AccessDeniedException("본인의 신청만 취소할 수 있습니다.");
        }
        Post post = enrollment.getPost();
        if (post.getAppliedCount() < 1) {
            throw new IllegalStateException("신청 인원 정보가 올바르지 않습니다.");
        }
        enrollmentRepository.delete(enrollment);
        // 같은 트랜잭션에서 관리 중인 Post이므로 save() 없이 UPDATE된다.
        post.setAppliedCount(post.getAppliedCount() - 1);
    }
}
