package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.hamcrest.Matchers.containsString;

import java.time.LocalDateTime;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.demo.model.domain.Member;
import com.example.demo.model.domain.Post;
import com.example.demo.model.repository.EnrollmentRepository;
import com.example.demo.model.repository.MemberRepository;
import com.example.demo.model.repository.PostRepository;
import com.example.demo.model.service.EnrollmentService;
import com.example.demo.model.service.FileStore;

/** 테스트에 @Transactional을 붙이지 않아 서비스의 실제 커밋·롤백을 검증한다. */
@SpringBootTest(properties = "app.upload-dir=target/week11-test-uploads")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class EnrollmentTransactionTests {
    @Autowired MockMvc mvc;
    @Autowired EnrollmentService service;
    @Autowired EnrollmentRepository enrollments;
    @Autowired PostRepository posts;
    @Autowired MemberRepository members;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired FileStore fileStore;
    private final List<String> storedFiles = new ArrayList<>();
    private Member first;
    private Member second;
    private Post a;
    private Post b;
    private Post c;
    private Post regular;

    @BeforeEach
    void committedFixture() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            first = newMember();
            second = newMember();
            a = newPost(5, "모집 A");
            b = newPost(5, "모집 B");
            c = newPost(1, "모집 C");
            regular = newPost(null, "일반 글");
        });
    }

    private Member newMember() {
        Member member = new Member();
        member.setUsername("tx11" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        member.setName("트랜잭션 검증용 회원");
        member.setPassword("test-only-unused-password");
        member.setRole("USER");
        return members.saveAndFlush(member);
    }

    private Post newPost(Integer capacity, String title) {
        Post post = new Post();
        post.setTitle(title);
        post.setContent("실제 트랜잭션 검증용 데이터");
        post.setAuthor(first);
        post.setCapacity(capacity);
        post.setCreatedAt(LocalDateTime.now());
        return posts.saveAndFlush(post);
    }

    @AfterEach
    void removeOnlyThisTestsFixture() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            enrollments.deleteAll(enrollments.findByMember_UsernameOrderByIdDesc(first.getUsername()));
            enrollments.deleteAll(enrollments.findByMember_UsernameOrderByIdDesc(second.getUsername()));
            enrollments.flush();
            posts.deleteAllById(List.of(a.getId(), b.getId(), c.getId(), regular.getId()));
            posts.flush();
            members.deleteAllById(List.of(first.getId(), second.getId()));
        });
        storedFiles.forEach(fileStore::delete);
    }

    @Test
    void commitsAllEnrollmentsAndRejectsDuplicateWithoutChangingCounts() {
        assertThat(service.confirm(first.getUsername(), List.of(a.getId(), b.getId()))).isEqualTo(2);
        assertThat(enrollments.findByMember_UsernameOrderByIdDesc(first.getUsername())).hasSize(2);
        assertThat(posts.findById(a.getId()).orElseThrow().getAppliedCount()).isEqualTo(1);
        assertThat(posts.findById(b.getId()).orElseThrow().getAppliedCount()).isEqualTo(1);
        assertThatThrownBy(() -> service.confirm(first.getUsername(), List.of(a.getId())))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("이미 신청한 모집글");
        assertThat(posts.findById(a.getId()).orElseThrow().getAppliedCount()).isEqualTo(1);
        assertThatThrownBy(() -> service.confirm(first.getUsername(), List.of()))
            .isInstanceOf(IllegalStateException.class).hasMessage("바구니가 비어 있습니다.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void fullLastRecruitmentRollsBackEarlierInsertAndPreservesCart() throws Exception {
        service.confirm(first.getUsername(), List.of(a.getId()));
        MockHttpSession session = new MockHttpSession();
        for (Long id : List.of(b.getId(), c.getId())) {
            mvc.perform(post("/cart/{id}", id).session(session).with(user(first.getUsername())).with(csrf()))
                .andExpect(redirectedUrl("/cart"));
        }
        service.confirm(second.getUsername(), List.of(c.getId()));

        mvc.perform(post("/cart/confirm").session(session).with(user(first.getUsername())).with(csrf()))
            .andExpect(redirectedUrl("/cart"))
            .andExpect(flash().attribute("error", "정원이 마감되었습니다 : 모집 C"));
        assertThat((Set<Long>) session.getAttribute("cart")).containsExactly(b.getId(), c.getId());
        assertThat(posts.findById(b.getId()).orElseThrow().getAppliedCount()).isZero();
        assertThat(posts.findById(c.getId()).orElseThrow().getAppliedCount()).isEqualTo(1);
        assertThat(enrollments.findByMember_UsernameOrderByIdDesc(first.getUsername()))
            .extracting(enrollment -> enrollment.getPost().getId()).containsExactly(a.getId());

        mvc.perform(post("/cart/{id}/remove", c.getId()).session(session).with(user(first.getUsername())).with(csrf()));
        mvc.perform(post("/cart/confirm").session(session).with(user(first.getUsername())).with(csrf()))
            .andExpect(flash().attribute("message", "1개 모집글 신청이 확정되었습니다."));
        assertThat(posts.findById(b.getId()).orElseThrow().getAppliedCount()).isEqualTo(1);
        assertThat((Set<Long>) session.getAttribute("cart")).isEmpty();
    }

    @Test
    void deletedOrNonRecruitmentPostRollsBackEarlierEnrollmentToo() {
        assertThatThrownBy(() -> service.confirm(first.getUsername(), List.of(b.getId(), Long.MAX_VALUE)))
            .isInstanceOf(IllegalStateException.class).hasMessage("삭제된 모집글이 바구니에 있습니다.");
        assertThat(posts.findById(b.getId()).orElseThrow().getAppliedCount()).isZero();
        assertThat(enrollments.findByMember_UsernameOrderByIdDesc(first.getUsername())).isEmpty();
        assertThatThrownBy(() -> service.confirm(first.getUsername(), List.of(b.getId(), regular.getId())))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("일반 글은 신청할 수 없습니다");
        assertThat(posts.findById(b.getId()).orElseThrow().getAppliedCount()).isZero();
        assertThat(enrollments.findByMember_UsernameOrderByIdDesc(first.getUsername())).isEmpty();
    }

    @Test
    void onlyApplicantCanCancelAndFullRecruitmentReopens() throws Exception {
        service.confirm(second.getUsername(), List.of(c.getId()));
        Long id = enrollments.findByMember_UsernameOrderByIdDesc(second.getUsername()).getFirst().getId();
        assertThat(posts.findById(c.getId()).orElseThrow().isFull()).isTrue();
        mvc.perform(get("/cart").with(user(second.getUsername())))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/cart/enrollments/" + id + "/cancel")));

        // 글 작성자와 관리자라도 다른 사람의 신청을 취소할 수 없다.
        mvc.perform(post("/cart/enrollments/{id}/cancel", id).with(user(first.getUsername())).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(post("/cart/enrollments/{id}/cancel", id).with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(post("/cart/enrollments/{id}/cancel", id).with(user(second.getUsername())))
            .andExpect(status().isForbidden());
        mvc.perform(post("/cart/enrollments/{id}/cancel", id).with(csrf()))
            .andExpect(redirectedUrl("/login"));
        assertThat(posts.findById(c.getId()).orElseThrow().getAppliedCount()).isEqualTo(1);
        assertThat(enrollments.existsById(id)).isTrue();

        mvc.perform(post("/cart/enrollments/{id}/cancel", id).with(user(second.getUsername())).with(csrf()))
            .andExpect(redirectedUrl("/cart"))
            .andExpect(flash().attribute("message", "신청이 취소되었습니다."));
        assertThat(enrollments.existsById(id)).isFalse();
        Post reopened = posts.findById(c.getId()).orElseThrow();
        assertThat(reopened.getAppliedCount()).isZero();
        assertThat(reopened.isFull()).isFalse();
        assertThat(reopened.getRemaining()).isEqualTo(1);
        mvc.perform(post("/cart/enrollments/{id}/cancel", id).with(user(second.getUsername())).with(csrf()))
            .andExpect(status().isNotFound());
        assertThat(service.confirm(first.getUsername(), List.of(c.getId()))).isEqualTo(1);
    }

    private String attachPoster(Long postId) {
        byte[] png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aF9sAAAAASUVORK5CYII=");
        String storedName = fileStore.save(new MockMultipartFile("poster", "poster.png", "image/png", png));
        storedFiles.add(storedName);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Post post = posts.findById(postId).orElseThrow();
            post.setPosterFile(storedName);
        });
        return storedName;
    }

    @Test
    void recruitmentWithApplicantsCannotBeDeletedAndKeepsPoster() throws Exception {
        String poster = attachPoster(b.getId());
        service.confirm(second.getUsername(), List.of(b.getId()));
        mvc.perform(post("/board/{id}/delete", b.getId()).with(user(first.getUsername())).with(csrf()))
            .andExpect(redirectedUrl("/board/" + b.getId() + "?deleteError"));
        mvc.perform(post("/board/{id}/delete", b.getId()).with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(redirectedUrl("/board/" + b.getId() + "?deleteError"));
        mvc.perform(post("/board/{id}/delete", b.getId()).with(user(second.getUsername())).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(get("/board/{id}", b.getId()).param("deleteError", ""))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("신청자가 있는 모집글은 삭제할 수 없습니다.")));
        assertThat(posts.existsById(b.getId())).isTrue();
        assertThat(enrollments.findByMember_UsernameOrderByIdDesc(second.getUsername())).hasSize(1);
        assertThat(Files.exists(fileStore.getRoot().resolve(poster))).isTrue();
    }

    @Test
    void memberWithEnrollmentsCannotBeDeletedFromScreenOrApi() throws Exception {
        service.confirm(second.getUsername(), List.of(a.getId()));
        String error = "신청 내역이 있는 회원은 삭제할 수 없습니다.";
        mvc.perform(post("/admin/members/{id}/delete", second.getId()).with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(redirectedUrl("/admin/members"))
            .andExpect(flash().attribute("error", error));
        mvc.perform(delete("/api/admin/members/{id}", second.getId()).with(user("admin").roles("ADMIN")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(error));
        assertThat(members.existsById(second.getId())).isTrue();
        assertThat(posts.findById(a.getId()).orElseThrow().getAppliedCount()).isEqualTo(1);

        Long id = enrollments.findByMember_UsernameOrderByIdDesc(second.getUsername()).getFirst().getId();
        service.cancel(id, second.getUsername());
        mvc.perform(delete("/api/admin/members/{id}", second.getId()).with(user("admin").roles("ADMIN")))
            .andExpect(status().isNoContent());
        assertThat(members.existsById(second.getId())).isFalse();
    }

    @Test
    void posterIsDeletedOnlyAfterPostDeletionCommits() throws Exception {
        String poster = attachPoster(b.getId());
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            try {
                mvc.perform(post("/board/{id}/delete", b.getId()).with(user(first.getUsername())).with(csrf()))
                    .andExpect(redirectedUrl("/board?deleted"));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            tx.setRollbackOnly();
        });
        assertThat(posts.existsById(b.getId())).isTrue();
        assertThat(Files.exists(fileStore.getRoot().resolve(poster))).isTrue();

        mvc.perform(post("/board/{id}/delete", b.getId()).with(user(first.getUsername())).with(csrf()))
            .andExpect(redirectedUrl("/board?deleted"));
        assertThat(posts.existsById(b.getId())).isFalse();
        assertThat(Files.exists(fileStore.getRoot().resolve(poster))).isFalse();
    }
}
