package com.example.demo.model.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.model.domain.Comment;
import com.example.demo.model.domain.Member;
import com.example.demo.model.domain.Post;
import com.example.demo.model.repository.CommentRepository;
import com.example.demo.model.repository.MemberRepository;

@Service
public class CommentService {
    private final CommentRepository commentRepository;
    private final MemberRepository memberRepository;
    private final PostService postService;

    public CommentService(CommentRepository commentRepository, MemberRepository memberRepository,
            PostService postService) {
        this.commentRepository = commentRepository;
        this.memberRepository = memberRepository;
        this.postService = postService;
    }

    public List<Comment> findByPost(Post post) {
        return commentRepository.findByPostOrderByIdAsc(post);
    }

    /** 작성자는 폼 입력 대신 로그인한 계정으로 지정한다. */
    @Transactional
    public Comment write(Long postId, String content, String username) {
        if (content == null || content.isBlank() || content.length() > 500) {
            throw new IllegalArgumentException("댓글은 1~500자로 입력하세요.");
        }
        Post post = postService.findById(postId);
        Member author = memberRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("회원이 존재하지 않습니다."));
        Comment comment = new Comment();
        comment.setContent(content.trim());
        comment.setPost(post);
        comment.setAuthor(author);
        comment.setCreatedAt(LocalDateTime.now());
        return commentRepository.save(comment);
    }

    /** 댓글 작성자 또는 관리자만 삭제하며, URL의 글 번호도 확인한다. */
    @Transactional
    public void delete(Long postId, Long id) {
        Comment comment = commentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글이 존재하지 않습니다."));
        if (!comment.getPost().getId().equals(postId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 글의 댓글이 아닙니다.");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        String loginUsername = authentication == null ? null : authentication.getName();
        if (!isAdmin && !comment.getAuthor().getUsername().equals(loginUsername)) {
            throw new AccessDeniedException("본인이 쓴 댓글만 삭제할 수 있습니다.");
        }
        commentRepository.delete(comment);
    }
}
