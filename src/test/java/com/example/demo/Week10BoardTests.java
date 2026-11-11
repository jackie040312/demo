package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.model.domain.Comment;
import com.example.demo.model.domain.Member;
import com.example.demo.model.domain.Post;
import com.example.demo.model.repository.CommentRepository;
import com.example.demo.model.repository.MemberRepository;
import com.example.demo.model.repository.PostRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional // 테스트 데이터는 종료 후 롤백해 기존 실습 데이터를 보존한다.
class Week10BoardTests {
    private static final String MEMBER_DELETE_ERROR = "작성한 글이나 댓글이 있는 회원은 삭제할 수 없습니다.";
    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired PostRepository posts;
    @Autowired CommentRepository comments;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager entityManager;

    private String unique() {
        return "week10" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private Member member() {
        Member member = new Member();
        member.setUsername(unique());
        member.setName("댓글 테스트 회원");
        member.setPassword(encoder.encode("123123"));
        member.setRole("USER");
        return members.saveAndFlush(member);
    }

    private Post savePost(Member author, String title, String content) {
        Post post = new Post();
        post.setAuthor(author);
        post.setTitle(title);
        post.setContent(content);
        post.setCreatedAt(LocalDateTime.now());
        return posts.saveAndFlush(post);
    }

    private Comment comment(Post post, Member author, String content) {
        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthor(author);
        comment.setContent(content);
        comment.setCreatedAt(LocalDateTime.now());
        return comments.saveAndFlush(comment);
    }

    @SuppressWarnings("unchecked")
    private Page<Post> page(MvcResult result) {
        return (Page<Post>) result.getModelAndView().getModel().get("page");
    }

    @Test
    void pagingUsesTenNewestPostsAndHandlesNegativeAndOutOfRangePages() throws Exception {
        long previousCount = posts.count();
        Member author = member();
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= 105; i++) {
            ids.add(savePost(author, "페이지 테스트 " + i, "페이징 확인").getId());
        }

        MvcResult first = mvc.perform(get("/board"))
            .andExpect(status().isOk()).andExpect(view().name("board/list"))
            .andExpect(content().string(containsString("page=1")))
            .andReturn();
        assertThat(page(first).getTotalElements()).isEqualTo(previousCount + 105);
        assertThat(page(first).getContent()).extracting(Post::getId)
            .containsExactlyElementsOf(ids.subList(95, 105).reversed());

        MvcResult second = mvc.perform(get("/board").param("page", "1"))
            .andExpect(status().isOk()).andReturn();
        assertThat(page(second).getContent()).extracting(Post::getId)
            .containsExactlyElementsOf(ids.subList(85, 95).reversed());
        MvcResult negative = mvc.perform(get("/board").param("page", "-1"))
            .andExpect(status().isOk()).andReturn();
        assertThat(page(negative).getNumber()).isZero();
        MvcResult outOfRange = mvc.perform(get("/board").param("page", "999"))
            .andExpect(status().isOk()).andReturn();
        assertThat(page(outOfRange)).isEmpty();
        assertThat(outOfRange.getResponse().getContentAsString()).doesNotContain("class=\"pagination");
    }

    @Test
    void searchMatchesTitleOrContentAndKeepsKeywordOnSecondPage() throws Exception {
        Member author = member();
        String keyword = unique();
        List<Long> matchingIds = new ArrayList<>();
        for (int i = 1; i <= 13; i++) {
            matchingIds.add(savePost(author, i % 2 == 0 ? keyword + " 제목 " + i : "일반 제목 " + i,
                i % 2 == 0 ? "일반 내용" : keyword + " 내용 검색").getId());
        }
        savePost(author, "검색에 포함되면 안 되는 글", "다른 내용");

        MvcResult first = mvc.perform(get("/board").param("keyword", "  " + keyword + "  "))
            .andExpect(status().isOk()).andExpect(model().attribute("keyword", keyword))
            .andExpect(content().string(containsString("page=1&amp;keyword=" + keyword)))
            .andReturn();
        assertThat(page(first).getTotalElements()).isEqualTo(13);
        assertThat(page(first).getTotalPages()).isEqualTo(2);
        assertThat(page(first).getContent()).hasSize(10);

        MvcResult second = mvc.perform(get("/board").param("keyword", keyword).param("page", "1"))
            .andExpect(status().isOk()).andReturn();
        assertThat(page(second).getContent()).extracting(Post::getId)
            .containsExactlyElementsOf(matchingIds.subList(0, 3).reversed());

        MvcResult injected = mvc.perform(get("/board").param("keyword", "' OR 1=1 --"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("검색 결과가 없습니다."))).andReturn();
        assertThat(page(injected)).isEmpty();
        MvcResult blank = mvc.perform(get("/board").param("keyword", "   "))
            .andExpect(status().isOk()).andReturn();
        assertThat(page(blank).getTotalElements()).isEqualTo(posts.count());
    }

    @Test
    void commentsUseLoggedInAuthorAreOldestFirstAndRefreshDoesNotDuplicate() throws Exception {
        Member writer = member();
        Member commenter = member();
        Post post = savePost(writer, "댓글 실습 글", "본문");
        Comment older = comment(post, writer, "먼저 쓴 댓글");
        long initialCount = comments.count();

        mvc.perform(get("/board/{id}", post.getId()))
            .andExpect(status().isOk()).andExpect(model().attributeExists("comments"))
            .andExpect(content().string(containsString("하면 댓글을 쓸 수 있습니다.")))
            .andExpect(content().string(not(containsString("댓글 등록"))));
        mvc.perform(post("/board/{id}/comments", post.getId())
                .with(user(commenter.getUsername()).roles("USER")).with(csrf())
                .param("content", "  <script>alert('댓글')</script>  ")
                .param("username", writer.getUsername()).param("author", writer.getId().toString()))
            .andExpect(redirectedUrl("/board/" + post.getId() + "#comments"));
        entityManager.flush();
        entityManager.clear();
        List<Comment> saved = comments.findByPostOrderByIdAsc(posts.findById(post.getId()).orElseThrow());
        assertThat(saved).hasSize(2);
        assertThat(saved.getFirst().getId()).isEqualTo(older.getId());
        assertThat(saved.getLast().getAuthor().getUsername()).isEqualTo(commenter.getUsername());
        assertThat(saved.getLast().getContent()).isEqualTo("<script>alert('댓글')</script>");

        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/board/{id}", post.getId()).with(user(commenter.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert('댓글')</script>"))))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        }
        assertThat(comments.count()).isEqualTo(initialCount + 1);
        mvc.perform(get("/board"))
            .andExpect(content().string(containsString("class=\"comment-count\">[2]</span>")));
    }

    @Test
    void commentInputRequiresLoginCsrfAndOneToFiveHundredCharacters() throws Exception {
        Member author = member();
        Post post = savePost(author, "댓글 입력 검증", "내용");
        long before = comments.count();
        mvc.perform(post("/board/{id}/comments", post.getId()).with(csrf()).param("content", "댓글"))
            .andExpect(redirectedUrl("/login"));
        mvc.perform(post("/board/{id}/comments", post.getId())
                .with(user(author.getUsername()).roles("USER")).param("content", "댓글"))
            .andExpect(status().isForbidden());
        for (String value : new String[] {"", "   ", "가".repeat(501)}) {
            mvc.perform(post("/board/{id}/comments", post.getId())
                    .with(user(author.getUsername()).roles("USER")).with(csrf()).param("content", value))
                .andExpect(redirectedUrl("/board/" + post.getId() + "?commentError#comments"));
        }
        assertThat(comments.count()).isEqualTo(before);
        mvc.perform(get("/board/{id}", post.getId()).param("commentError", ""))
            .andExpect(content().string(containsString("댓글은 1~500자로 입력하세요.")));
        mvc.perform(post("/board/{id}/comments", post.getId())
                .with(user(author.getUsername()).roles("USER")).with(csrf()).param("content", "가".repeat(500)))
            .andExpect(redirectedUrl("/board/" + post.getId() + "#comments"));
        assertThat(comments.count()).isEqualTo(before + 1);
        mvc.perform(post("/board/{id}/comments", Long.MAX_VALUE)
                .with(user(author.getUsername()).roles("USER")).with(csrf()).param("content", "없는 글 댓글"))
            .andExpect(status().isNotFound());
    }

    @Test
    void commentDeletionAllowsWriterOrAdminButNotPostOwnerAndChecksParent() throws Exception {
        Member postOwner = member();
        Member commenter = member();
        Post post = savePost(postOwner, "댓글 삭제 검증", "본문");
        Comment first = comment(post, commenter, "작성자가 삭제할 댓글");
        Comment second = comment(post, commenter, "관리자가 삭제할 댓글");
        Post unrelated = savePost(postOwner, "다른 글", "본문");
        String deleteAction = "action=\"/board/" + post.getId() + "/comments/" + first.getId() + "/delete\"";

        mvc.perform(get("/board/{id}", post.getId()).with(user(postOwner.getUsername()).roles("USER")))
            .andExpect(content().string(not(containsString(deleteAction))));
        mvc.perform(get("/board/{id}", post.getId()).with(user(commenter.getUsername()).roles("USER")))
            .andExpect(content().string(containsString(deleteAction)));
        mvc.perform(post("/board/{pid}/comments/{cid}/delete", post.getId(), first.getId())
                .with(user(postOwner.getUsername()).roles("USER")).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(post("/board/{pid}/comments/{cid}/delete", post.getId(), first.getId())
                .with(user(commenter.getUsername()).roles("USER")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/board/{pid}/comments/{cid}/delete", unrelated.getId(), first.getId())
                .with(user(commenter.getUsername()).roles("USER")).with(csrf()))
            .andExpect(status().isNotFound());
        assertThat(comments.existsById(first.getId())).isTrue();

        mvc.perform(post("/board/{pid}/comments/{cid}/delete", post.getId(), first.getId())
                .with(user(commenter.getUsername()).roles("USER")).with(csrf()))
            .andExpect(redirectedUrl("/board/" + post.getId() + "?commentDeleted#comments"));
        assertThat(comments.existsById(first.getId())).isFalse();
        mvc.perform(get("/board/{id}", post.getId()).with(user("admin").roles("ADMIN")))
            .andExpect(content().string(containsString("관리자 삭제")));
        mvc.perform(post("/board/{pid}/comments/{cid}/delete", post.getId(), second.getId())
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(redirectedUrl("/board/" + post.getId() + "?commentDeleted#comments"));
        assertThat(comments.existsById(second.getId())).isFalse();
        mvc.perform(post("/board/{pid}/comments/{cid}/delete", post.getId(), Long.MAX_VALUE)
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isNotFound());
    }

    @Test
    void deletingPostAlsoDeletesItsCommentsAndKeepsOtherPostsComments() throws Exception {
        Member author = member();
        Member commenter = member();
        Post removed = savePost(author, "댓글과 함께 삭제할 글", "본문");
        Long removedId = removed.getId();
        Long firstComment = comment(removed, commenter, "첫 댓글").getId();
        Long secondComment = comment(removed, author, "둘째 댓글").getId();
        Post kept = savePost(author, "남겨둘 글", "본문");
        Long keptComment = comment(kept, commenter, "남겨둘 댓글").getId();
        entityManager.flush();
        entityManager.clear();

        mvc.perform(post("/board/{id}/delete", removedId)
                .with(user(author.getUsername()).roles("USER")).with(csrf()))
            .andExpect(redirectedUrl("/board?deleted"));
        entityManager.flush();
        entityManager.clear();
        assertThat(posts.existsById(removedId)).isFalse();
        assertThat(comments.existsById(firstComment)).isFalse();
        assertThat(comments.existsById(secondComment)).isFalse();
        assertThat(comments.existsById(keptComment)).isTrue();
        assertThat(members.existsById(commenter.getId())).isTrue();
    }

    @Test
    void memberDeletionWithPostsOrCommentsReturnsMessageAndApi400() throws Exception {
        Member postAuthor = member();
        Member commentAuthor = member();
        Post post = savePost(postAuthor, "회원 삭제 제한 검증", "본문");
        comment(post, commentAuthor, "회원 삭제를 제한할 댓글");
        for (Member target : List.of(postAuthor, commentAuthor)) {
            mvc.perform(post("/admin/members/{id}/delete", target.getId())
                    .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/members"))
                .andExpect(flash().attribute("error", MEMBER_DELETE_ERROR));
            mvc.perform(delete("/api/admin/members/{id}", target.getId()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(MEMBER_DELETE_ERROR));
            assertThat(members.existsById(target.getId())).isTrue();
        }
        assertThat(posts.existsById(post.getId())).isTrue();
        assertThat(comments.findByPostOrderByIdAsc(post)).hasSize(1);
    }

    @Test
    void memberWithoutPostsOrCommentsCanStillBeDeleted() throws Exception {
        Member target = member();
        mvc.perform(delete("/api/admin/members/{id}", target.getId()).with(user("admin").roles("ADMIN")))
            .andExpect(status().isNoContent());
        entityManager.flush();
        assertThat(members.existsById(target.getId())).isFalse();
    }

    @Test
    void myPostsPagesOnlyCurrentAuthorsPostsAndKeepsMyPageLinks() throws Exception {
        Member author = member();
        Member other = member();
        List<Long> ownIds = new ArrayList<>();
        for (int i = 1; i <= 23; i++) {
            ownIds.add(savePost(author, "내 글 페이지 " + i, "본문").getId());
        }
        savePost(other, "다른 회원의 글", "내 글 목록에는 나오면 안 된다.");

        MvcResult first = mvc.perform(get("/board/my").with(user(author.getUsername()).roles("USER"))
                .param("username", other.getUsername()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("pageUrl", "/board/my"))
            .andExpect(model().attribute("listTitle", "내가 쓴 글"))
            .andExpect(content().string(containsString("href=\"/board/my?page=1\"")))
            .andExpect(content().string(not(containsString("다른 회원의 글"))))
            .andExpect(content().string(not(containsString("name=\"type\""))))
            .andReturn();
        assertThat(page(first).getTotalElements()).isEqualTo(23);
        assertThat(page(first).getTotalPages()).isEqualTo(3);
        assertThat(page(first).getContent()).extracting(Post::getId)
            .containsExactlyElementsOf(ownIds.subList(13, 23).reversed());

        MvcResult second = mvc.perform(get("/board/my").param("page", "1")
                .with(user(author.getUsername()).roles("USER")))
            .andExpect(status().isOk()).andReturn();
        assertThat(page(second).getContent()).extracting(Post::getId)
            .containsExactlyElementsOf(ownIds.subList(3, 13).reversed());
        MvcResult last = mvc.perform(get("/board/my").param("page", "2")
                .with(user(author.getUsername()).roles("USER")))
            .andExpect(status().isOk()).andReturn();
        assertThat(page(last).getContent()).extracting(Post::getId)
            .containsExactlyElementsOf(ownIds.subList(0, 3).reversed());
        MvcResult negative = mvc.perform(get("/board/my").param("page", "-1")
                .with(user(author.getUsername()).roles("USER")))
            .andExpect(status().isOk()).andReturn();
        assertThat(page(negative).getNumber()).isZero();
        MvcResult outOfRange = mvc.perform(get("/board/my").param("page", "999")
                .with(user(author.getUsername()).roles("USER")))
            .andExpect(status().isOk()).andReturn();
        assertThat(page(outOfRange)).isEmpty();
        mvc.perform(get("/board/my").param("page", "1"))
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void writerSearchUsesMemberNameAndKeepsSearchTypeOnNextPage() throws Exception {
        Member author = member();
        Member other = member();
        String keyword = unique();
        author.setName("홍길동" + keyword);
        members.saveAndFlush(author);
        List<Long> matchingIds = new ArrayList<>();
        for (int i = 1; i <= 13; i++) {
            matchingIds.add(savePost(author, "작성자 검색 글 " + i, "일반 본문").getId());
        }
        Post titleOnly = savePost(other, keyword + " 제목에만 있는 단어", "다른 회원의 글");

        MvcResult first = mvc.perform(get("/board").param("type", "writer").param("keyword", " " + keyword + " "))
            .andExpect(status().isOk())
            .andExpect(model().attribute("type", "writer"))
            .andExpect(model().attribute("keyword", keyword))
            .andExpect(content().string(containsString("value=\"writer\" selected=\"selected\"")))
            .andExpect(content().string(containsString("page=1&amp;keyword=" + keyword + "&amp;type=writer")))
            .andExpect(content().string(not(containsString("제목에만 있는 단어"))))
            .andReturn();
        assertThat(page(first).getTotalElements()).isEqualTo(13);
        assertThat(page(first).getContent()).hasSize(10);
        MvcResult second = mvc.perform(get("/board").param("type", "writer")
                .param("keyword", keyword).param("page", "1"))
            .andExpect(status().isOk()).andReturn();
        assertThat(page(second).getContent()).extracting(Post::getId)
            .containsExactlyElementsOf(matchingIds.subList(0, 3).reversed());

        MvcResult usernameOnly = mvc.perform(get("/board").param("type", "writer")
                .param("keyword", author.getUsername()))
            .andExpect(status().isOk()).andReturn();
        assertThat(page(usernameOnly)).isEmpty();
        MvcResult fallback = mvc.perform(get("/board").param("type", "unknown").param("keyword", keyword))
            .andExpect(status().isOk()).andExpect(model().attribute("type", "all")).andReturn();
        assertThat(page(fallback).getContent()).extracting(Post::getId).containsExactly(titleOnly.getId());
    }
}
