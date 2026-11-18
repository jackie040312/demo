package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.model.domain.Member;
import com.example.demo.model.domain.Post;
import com.example.demo.model.repository.EnrollmentRepository;
import com.example.demo.model.repository.MemberRepository;
import com.example.demo.model.repository.PostRepository;
import com.example.demo.model.service.FileStore;

@SpringBootTest(properties = "app.upload-dir=target/week11-test-uploads")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class Week11FlowTests {
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aF9sAAAAASUVORK5CYII=");
    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired PostRepository posts;
    @Autowired EnrollmentRepository enrollments;
    @Autowired PasswordEncoder encoder;
    @Autowired FileStore fileStore;
    private final List<String> storedFiles = new ArrayList<>();

    @AfterEach
    void cleanTestUploads() {
        storedFiles.forEach(fileStore::delete);
    }

    private Member member() {
        Member member = new Member();
        member.setUsername("w11" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        member.setName("11주차 테스트");
        member.setPassword(encoder.encode("123123"));
        member.setRole("USER");
        return members.saveAndFlush(member);
    }

    private Post recruit(Member author, Integer capacity) {
        Post post = new Post();
        post.setAuthor(author);
        post.setTitle("모집글 " + UUID.randomUUID());
        post.setContent("함께 공부할 학생을 모집합니다.");
        post.setCreatedAt(LocalDateTime.now());
        post.setCapacity(capacity);
        return posts.saveAndFlush(post);
    }

    private Post saved(MvcResult result) {
        Long id = Long.valueOf(result.getResponse().getRedirectedUrl().substring("/board/".length()));
        Post post = posts.findById(id).orElseThrow();
        if (post.getPosterFile() != null) storedFiles.add(post.getPosterFile());
        return post;
    }

    @Test
    void writesRecruitmentAndRegularPostsAndValidatesCapacity() throws Exception {
        Member author = member();
        MvcResult result = mvc.perform(post("/board/write").with(user(author.getUsername())).with(csrf())
                .param("title", "정원 5명 모집").param("content", "본문").param("capacity", "5")
                .param("appliedCount", "99"))
            .andExpect(status().is3xxRedirection()).andReturn();
        Post post = saved(result);
        assertThat(post.isRecruit()).isTrue();
        assertThat(post.getRemaining()).isEqualTo(5);
        assertThat(post.getAppliedCount()).isZero();
        mvc.perform(get("/board/{id}", post.getId()).with(user(author.getUsername())))
            .andExpect(status().isOk()).andExpect(content().string(containsString("신청 바구니에 담기")));
        mvc.perform(get("/board"))
            .andExpect(content().string(containsString("모집 0/5")));

        MvcResult regular = mvc.perform(post("/board/write").with(user(author.getUsername())).with(csrf())
                .param("title", "일반 글").param("content", "본문"))
            .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(saved(regular).isRecruit()).isFalse();
        for (String capacity : List.of("0", "101")) {
            mvc.perform(post("/board/write").with(user(author.getUsername())).with(csrf())
                    .param("title", "잘못된 정원").param("content", "본문").param("capacity", capacity))
                .andExpect(status().isOk()).andExpect(view().name("board/form"))
                .andExpect(content().string(containsString("모집 인원은 1~100명으로 입력하세요.")));
        }
    }

    @Test
    void uploadsPosterWithUuidNameAndAllowsAnonymousImageRequests() throws Exception {
        Member author = member();
        MvcResult result = mvc.perform(multipart("/board/write")
                .file(new MockMultipartFile("poster", "../../poster.PNG", "image/png", PNG))
                .with(user(author.getUsername())).with(csrf())
                .param("title", "포스터 모집글").param("content", "본문").param("capacity", "5"))
            .andExpect(status().is3xxRedirection()).andReturn();
        Post post = saved(result);
        assertThat(post.getPosterFile()).matches("[a-f0-9-]{36}\\.png");
        assertThat(Files.readAllBytes(fileStore.getRoot().resolve(post.getPosterFile()))).isEqualTo(PNG);
        mvc.perform(get("/uploads/{file}", post.getPosterFile()))
            .andExpect(status().isOk()).andExpect(content().bytes(PNG));
        mvc.perform(get("/board/{id}", post.getId()))
            .andExpect(content().string(containsString("/uploads/" + post.getPosterFile())))
            .andExpect(content().string(containsString("하면 신청할 수 있습니다.")));
    }

    @Test
    void rejectsFakeImagesAndOversizeFilesButAcceptsThreeMegabytes() throws Exception {
        Member author = member();
        for (MockMultipartFile file : List.of(
                new MockMultipartFile("poster", "script.html", "image/png", PNG),
                new MockMultipartFile("poster", "fake.png", "image/png", "not an image".getBytes()))) {
            mvc.perform(multipart("/board/write").file(file).with(user(author.getUsername())).with(csrf())
                    .param("title", "잘못된 파일").param("content", "본문").param("capacity", "5"))
                .andExpect(status().isOk()).andExpect(view().name("board/form"));
        }
        mvc.perform(multipart("/board/write")
                .file(new MockMultipartFile("poster", "large.png", "image/png", Arrays.copyOf(PNG, 6 * 1024 * 1024)))
                .with(user(author.getUsername())).with(csrf()).param("title", "큰 파일").param("content", "본문"))
            .andExpect(status().is(413)).andExpect(view().name("error/413"))
            .andExpect(content().string(containsString("파일이 너무 큽니다")));
        MvcResult accepted = mvc.perform(multipart("/board/write")
                .file(new MockMultipartFile("poster", "photo.png", "image/png", Arrays.copyOf(PNG, 3 * 1024 * 1024)))
                .with(user(author.getUsername())).with(csrf()).param("title", "3MB 포스터").param("content", "본문")
                .param("capacity", "5"))
            .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(Files.size(fileStore.getRoot().resolve(saved(accepted).getPosterFile()))).isEqualTo(3 * 1024 * 1024);
    }

    @Test
    @SuppressWarnings("unchecked")
    void cartAvoidsDuplicatesKeepsInsertionOrderAndIsSeparateForEachSession() throws Exception {
        Member applicant = member();
        Post first = recruit(applicant, 5);
        Post second = recruit(applicant, 5);
        Post regular = recruit(applicant, null);
        MockHttpSession session = new MockHttpSession();
        for (Long id : List.of(second.getId(), first.getId(), second.getId())) {
            mvc.perform(post("/cart/{id}", id).session(session).with(user(applicant.getUsername())).with(csrf()))
                .andExpect(redirectedUrl("/cart"));
        }
        assertThat((Set<Long>) session.getAttribute("cart")).containsExactly(second.getId(), first.getId());
        MvcResult cart = mvc.perform(get("/cart").session(session).with(user(applicant.getUsername())))
            .andExpect(status().isOk()).andExpect(view().name("cart"))
            .andExpect(model().attributeExists("cartPosts", "enrollments"))
            .andExpect(content().string(containsString("모두 신청 확정"))).andReturn();
        assertThat((List<Post>) cart.getModelAndView().getModel().get("cartPosts"))
            .extracting(Post::getId).containsExactly(second.getId(), first.getId());
        mvc.perform(post("/cart/{id}", regular.getId()).session(session).with(user(applicant.getUsername())).with(csrf()))
            .andExpect(redirectedUrl("/board/" + regular.getId()));
        MockHttpSession another = new MockHttpSession();
        mvc.perform(get("/cart").session(another).with(user(applicant.getUsername())))
            .andExpect(status().isOk()).andExpect(content().string(containsString("바구니가 비어 있습니다.")));
        mvc.perform(post("/cart/{id}/remove", first.getId()).session(session)
                .with(user(applicant.getUsername())).with(csrf()))
            .andExpect(redirectedUrl("/cart"));
        assertThat((Set<Long>) session.getAttribute("cart")).containsExactly(second.getId());
        mvc.perform(post("/logout").session(session).with(user(applicant.getUsername())).with(csrf()).accept("text/html"))
            .andExpect(status().is3xxRedirection());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void cartRequiresLoginAndCsrfAndRejectsMissingPosts() throws Exception {
        Member applicant = member();
        Post post = recruit(applicant, 5);
        mvc.perform(get("/cart")).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/cart/{id}", post.getId()).with(csrf())).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/cart/{id}", post.getId()).with(user(applicant.getUsername())))
            .andExpect(status().isForbidden());
        mvc.perform(post("/cart/confirm").with(user(applicant.getUsername())))
            .andExpect(status().isForbidden());
        mvc.perform(post("/cart/{id}", Long.MAX_VALUE).with(user(applicant.getUsername())).with(csrf()))
            .andExpect(status().isNotFound());
        mvc.perform(post("/cart/confirm").with(user(applicant.getUsername())).with(csrf()))
            .andExpect(redirectedUrl("/cart")).andExpect(flash().attribute("error", "바구니가 비어 있습니다."));
    }

    @Test
    @SuppressWarnings("unchecked")
    void successfulConfirmationClearsCartAndDisplaysOnlyOwnEnrollments() throws Exception {
        Member applicant = member();
        Post post = recruit(applicant, 5);
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/cart/{id}", post.getId()).session(session).with(user(applicant.getUsername())).with(csrf()));
        mvc.perform(post("/cart/confirm").session(session).with(user(applicant.getUsername())).with(csrf()))
            .andExpect(redirectedUrl("/cart"))
            .andExpect(flash().attribute("message", "1개 모집글 신청이 확정되었습니다."));
        assertThat((Set<Long>) session.getAttribute("cart")).isEmpty();
        assertThat(enrollments.findByMember_UsernameOrderByIdDesc(applicant.getUsername())).hasSize(1);
        assertThat(posts.findById(post.getId()).orElseThrow().getAppliedCount()).isEqualTo(1);
        mvc.perform(get("/cart").session(session).with(user(applicant.getUsername())))
            .andExpect(status().isOk()).andExpect(content().string(containsString(post.getTitle())));
        Member other = member();
        mvc.perform(get("/cart").with(user(other.getUsername())))
            .andExpect(content().string(not(containsString(post.getTitle()))));
    }
}
