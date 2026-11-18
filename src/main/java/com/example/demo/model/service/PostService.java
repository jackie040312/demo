package com.example.demo.model.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.model.domain.Member;
import com.example.demo.model.domain.Post;
import com.example.demo.model.dto.PostForm;
import com.example.demo.model.repository.MemberRepository;
import com.example.demo.model.repository.PostRepository;
import com.example.demo.model.repository.EnrollmentRepository;

@Service
public class PostService {
    public static final int PAGE_SIZE = 10;

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final FileStore fileStore;
    private final EnrollmentRepository enrollmentRepository;

    public PostService(PostRepository postRepository, MemberRepository memberRepository, FileStore fileStore,
            EnrollmentRepository enrollmentRepository) {
        this.postRepository = postRepository;
        this.memberRepository = memberRepository;
        this.fileStore = fileStore;
        this.enrollmentRepository = enrollmentRepository;
    }

    /** 전체 게시글을 최신 글부터 조회한다. */
    public List<Post> findAll() {
        return postRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    /** 로그인한 회원의 글만 최신순으로 10개씩 조회한다. */
    public Page<Post> findMyPosts(String username, int page) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
            Sort.by(Sort.Direction.DESC, "id"));
        return postRepository.findByAuthor_Username(username, pageable);
    }

    /** 검색 범위에 따라 제목·내용 또는 작성자 이름을 검색한다. */
    public Page<Post> findPage(String type, String keyword, int page) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
            Sort.by(Sort.Direction.DESC, "id"));
        if (keyword == null || keyword.isBlank()) {
            return postRepository.findAll(pageable);
        }
        if ("writer".equals(type)) {
            return postRepository.searchByWriter(keyword.trim(), pageable);
        }
        return postRepository.search(keyword.trim(), pageable);
    }

    /** 로그인한 회원을 작성자로 지정해 새 글을 저장한다. */
    @Transactional
    public Post write(PostForm form, String username) {
        validate(form);
        Member author = memberRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("회원이 존재하지 않습니다."));

        Post post = new Post();
        post.setTitle(form.getTitle().trim());
        post.setContent(form.getContent());
        post.setAuthor(author);
        post.setCreatedAt(LocalDateTime.now());
        post.setCapacity(form.getCapacity());
        String posterFile = fileStore.save(form.getPoster());
        post.setPosterFile(posterFile);
        if (posterFile != null) {
            // DB 저장이 실패하면 이미 저장한 파일도 정리한다.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) fileStore.delete(posterFile);
                }
            });
        }
        return postRepository.save(post);
    }

    /** 세션 바구니에는 ID만 저장하고, 모집글 정보는 DB에서 다시 조회한다. */
    public List<Post> findAllById(Collection<Long> ids) {
        Map<Long, Post> found = postRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(Post::getId, Function.identity()));
        return ids.stream().map(found::get).filter(java.util.Objects::nonNull).toList();
    }

    /** 글 번호로 조회하고, 없으면 404 상태를 반환한다. */
    public Post findById(Long id) {
        return postRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "글이 존재하지 않습니다."));
    }

    /** 상세 화면을 열 때마다 조회수를 1 올리고 DB에 반영한다. */
    @Transactional
    public Post read(Long id) {
        Post post = findById(id);
        post.setViewCount(post.getViewCount() + 1);
        return post;
    }

    /** 수정 화면은 작성자 본인만 열 수 있다. */
    public Post findForEdit(Long id) {
        Post post = findById(id);
        checkWriter(post);
        return post;
    }

    /** 작성자 본인의 글만 수정한다. */
    public Post edit(Long id, PostForm form) {
        Post post = findForEdit(id);
        validate(form);
        post.setTitle(form.getTitle().trim());
        post.setContent(form.getContent());
        post.setUpdatedAt(LocalDateTime.now());
        return postRepository.save(post);
    }

    /** 작성자 본인 또는 관리자가 글을 삭제한다. */
    @Transactional
    public void delete(Long id) {
        Post post = findById(id);
        if (!isAdmin()) {
            checkWriter(post);
        }
        if (enrollmentRepository.existsByPost(post)) {
            throw new IllegalStateException("신청자가 있는 모집글은 삭제할 수 없습니다.");
        }
        postRepository.delete(post);
        String posterFile = post.getPosterFile();
        if (posterFile != null) {
            // 롤백되면 글과 포스터를 유지하고, DB 커밋 후에만 파일을 삭제한다.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fileStore.delete(posterFile);
                }
            });
        }
    }

    private void validate(PostForm form) {
        if (form.getCapacity() != null && (form.getCapacity() < 1 || form.getCapacity() > 100)) {
            throw new IllegalArgumentException("모집 인원은 1~100명으로 입력하세요.");
        }
        if (form.getTitle() == null || form.getTitle().isBlank()
                || form.getContent() == null || form.getContent().isBlank()) {
            throw new IllegalArgumentException("제목과 내용을 모두 입력하세요.");
        }
        if (form.getTitle().trim().length() > 100) {
            throw new IllegalArgumentException("제목은 100자까지 입력할 수 있습니다.");
        }
    }

    private void checkWriter(Post post) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String loginUsername = authentication == null ? null : authentication.getName();
        if (!post.getAuthor().getUsername().equals(loginUsername)) {
            throw new AccessDeniedException("본인이 쓴 글만 수정·삭제할 수 있습니다.");
        }
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
