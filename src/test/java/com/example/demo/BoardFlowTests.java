package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.model.domain.Member;
import com.example.demo.model.domain.Post;
import com.example.demo.model.repository.MemberRepository;
import com.example.demo.model.repository.PostRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class BoardFlowTests {
    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired PostRepository posts;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager entityManager;

    private String uniqueUsername(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private Member saveMember(String username, String role) {
        Member member = new Member();
        member.setUsername(username);
        member.setPassword(encoder.encode("123123"));
        member.setName(username + " 이름");
        member.setRole(role);
        return members.saveAndFlush(member);
    }

    private Post savePost(Member author, String title) {
        Post post = new Post();
        post.setTitle(title);
        post.setContent("게시글 테스트 내용입니다.");
        post.setAuthor(author);
        post.setCreatedAt(LocalDateTime.now());
        return posts.saveAndFlush(post);
    }

    @Test
    void publicCanOpenBoardListAndDetailButWritingRequiresLogin() throws Exception {
        String username = uniqueUsername("writer");
        Post post = savePost(saveMember(username, "USER"), "공개 게시글 제목");

        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("href=\"/board\"")));
        mvc.perform(get("/board"))
            .andExpect(status().isOk())
            .andExpect(view().name("board/list"))
            .andExpect(model().attributeExists("posts"))
            .andExpect(content().string(containsString("공개 게시글 제목")))
            .andExpect(content().string(not(containsString("href=\"/board/write\""))));
        mvc.perform(get("/board/{id}", post.getId()))
            .andExpect(status().isOk())
            .andExpect(view().name("board/detail"))
            .andExpect(content().string(containsString("공개 게시글 제목")))
            .andExpect(content().string(containsString(username)));
        mvc.perform(get("/css/board.css")).andExpect(status().isOk());

        mvc.perform(get("/board/write"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/board/{id}/edit", post.getId()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void authenticatedMemberCanWriteAndServerValidatesInputAndCsrf() throws Exception {
        String username = uniqueUsername("author");
        saveMember(username, "USER");

        mvc.perform(post("/board/write").with(user(username).roles("USER"))
                .param("title", "CSRF 없는 글").param("content", "저장되면 안 됩니다."))
            .andExpect(status().isForbidden());

        mvc.perform(post("/board/write").with(user(username).roles("USER")).with(csrf())
                .param("title", "   ").param("content", "내용"))
            .andExpect(status().isOk())
            .andExpect(view().name("board/form"))
            .andExpect(content().string(containsString("제목과 내용을 모두 입력하세요.")));

        mvc.perform(post("/board/write").with(user(username).roles("USER")).with(csrf())
                .param("title", "가".repeat(101)).param("content", "내용"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("제목은 100자까지 입력할 수 있습니다.")));

        MvcResult result = mvc.perform(post("/board/write")
                .with(user(username).roles("USER")).with(csrf())
                .param("title", "  새 게시글  ").param("content", "작성 내용"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

        Post saved = posts.findAll().stream()
            .filter(post -> "새 게시글".equals(post.getTitle()))
            .findFirst().orElseThrow();
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/board/" + saved.getId());
        assertThat(saved.getAuthor().getUsername()).isEqualTo(username);
        assertThat(saved.getContent()).isEqualTo("작성 내용");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getViewCount()).isZero();
    }

    @Test
    void onlyWriterCanEditAndWriterOrAdminCanDelete() throws Exception {
        String owner = uniqueUsername("owner");
        String other = uniqueUsername("other");
        saveMember(other, "USER");
        Post post = savePost(saveMember(owner, "USER"), "수정 전 제목");

        mvc.perform(get("/board/{id}/edit", post.getId()).with(user(owner).roles("USER")))
            .andExpect(status().isOk())
            .andExpect(view().name("board/form"))
            .andExpect(model().attributeExists("postForm", "postId"));
        mvc.perform(get("/board/{id}", post.getId()).with(user(owner).roles("USER")))
            .andExpect(content().string(containsString("href=\"/board/" + post.getId() + "/edit\"")));

        mvc.perform(get("/board/{id}/edit", post.getId()).with(user(other).roles("USER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/board/{id}/edit", post.getId()).with(user("admin").roles("ADMIN")))
            .andExpect(status().isForbidden());

        mvc.perform(post("/board/{id}/edit", post.getId())
                .with(user(other).roles("USER")).with(csrf())
                .param("title", "남이 바꾼 제목").param("content", "남이 바꾼 내용"))
            .andExpect(status().isForbidden());
        assertThat(posts.findById(post.getId()).orElseThrow().getTitle()).isEqualTo("수정 전 제목");

        mvc.perform(post("/board/{id}/edit", post.getId())
                .with(user(owner).roles("USER")).with(csrf())
                .param("title", "수정 완료 제목").param("content", "수정 완료 내용"))
            .andExpect(redirectedUrl("/board/" + post.getId()));
        Post edited = posts.findById(post.getId()).orElseThrow();
        assertThat(edited.getTitle()).isEqualTo("수정 완료 제목");
        assertThat(edited.getUpdatedAt()).isNotNull();

        mvc.perform(post("/board/{id}/delete", post.getId())
                .with(user(other).roles("USER")).with(csrf()))
            .andExpect(status().isForbidden());
        assertThat(posts.existsById(post.getId())).isTrue();

        mvc.perform(get("/board/{id}", post.getId()).with(user("admin").roles("ADMIN")))
            .andExpect(content().string(containsString("관리자 삭제")))
            .andExpect(content().string(not(containsString("/edit"))));
        mvc.perform(post("/board/{id}/delete", post.getId())
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(redirectedUrl("/board?deleted"));
        assertThat(posts.existsById(post.getId())).isFalse();
    }

    @Test
    void missingPostShows404Page() throws Exception {
        mvc.perform(get("/board/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    void detailIncrementsAndPersistsViewCountButListAndEditDoNot() throws Exception {
        String username = uniqueUsername("reader");
        Long id = savePost(saveMember(username, "USER"), "조회수 확인 글").getId();

        for (int expectedCount = 1; expectedCount <= 3; expectedCount++) {
            MvcResult result = mvc.perform(get("/board/{id}", id))
                .andExpect(status().isOk())
                .andReturn();
            Post displayed = (Post) result.getModelAndView().getModel().get("post");
            assertThat(displayed.getViewCount()).isEqualTo(expectedCount);
            entityManager.flush();
            entityManager.clear();
            assertThat(posts.findById(id).orElseThrow().getViewCount()).isEqualTo(expectedCount);
        }

        MvcResult list = mvc.perform(get("/board"))
            .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        List<Post> displayedPosts = (List<Post>) list.getModelAndView().getModel().get("posts");
        assertThat(displayedPosts).filteredOn(post -> post.getId().equals(id))
            .extracting(Post::getViewCount).containsExactly(3);

        mvc.perform(get("/board/{id}/edit", id).with(user(username).roles("USER")))
            .andExpect(status().isOk());
        assertThat(posts.findById(id).orElseThrow().getViewCount()).isEqualTo(3);
    }

    @Test
    void myPostsOnlyShowsLoggedInAuthorsPostsNewestFirst() throws Exception {
        String username = uniqueUsername("mywriter");
        Member author = saveMember(username, "USER");
        Post older = savePost(author, "내가 쓴 이전 글");
        savePost(saveMember(uniqueUsername("another"), "USER"), "다른 사람의 비공개 목록 테스트 글");
        Post newer = savePost(author, "내가 쓴 최신 글");

        mvc.perform(get("/board").with(user(username).roles("USER")))
            .andExpect(content().string(containsString("href=\"/board/my\"")));
        MvcResult result = mvc.perform(get("/board/my").with(user(username).roles("USER"))
                .param("username", "another"))
            .andExpect(status().isOk())
            .andExpect(view().name("board/list"))
            .andExpect(model().attribute("listTitle", "내가 쓴 글"))
            .andExpect(content().string(containsString("내가 쓴 최신 글")))
            .andExpect(content().string(containsString("전체 글")))
            .andExpect(content().string(not(containsString("다른 사람의 비공개 목록 테스트 글"))))
            .andReturn();
        @SuppressWarnings("unchecked")
        List<Post> displayed = (List<Post>) result.getModelAndView().getModel().get("posts");
        assertThat(displayed).extracting(Post::getId).containsExactly(newer.getId(), older.getId());
    }

    @Test
    void myPostsRequiresLoginAndHandlesMemberWithNoPosts() throws Exception {
        mvc.perform(get("/board/my"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/board"))
            .andExpect(content().string(not(containsString("href=\"/board/my\""))));

        String username = uniqueUsername("empty");
        saveMember(username, "USER");
        mvc.perform(get("/board/my").with(user(username).roles("USER")))
            .andExpect(status().isOk())
            .andExpect(model().attribute("listTitle", "내가 쓴 글"))
            .andExpect(content().string(containsString("전체 0개")))
            .andExpect(content().string(containsString("아직 글이 없습니다.")));
    }
}
