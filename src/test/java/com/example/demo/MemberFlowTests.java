package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.model.domain.Member;
import com.example.demo.model.repository.MemberRepository;
import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional // 테스트에서 만든 회원은 종료 후 롤백한다.
class MemberFlowTests {
    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder encoder;

    private String newUsername() {
        return "test" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private void signup(String username) throws Exception {
        mvc.perform(post("/signup").with(csrf())
                .param("username", username).param("password", "123123")
                .param("passwordConfirm", "123123").param("name", "테스트 회원")
                .param("role", "ADMIN"))
            .andExpect(redirectedUrl("/login?signup"));
    }

    @Test
    void publicPagesAndAssetsAreAccessibleButMemberListRequiresLogin() throws Exception {
        for (String path : new String[] {"/", "/hello", "/hello2", "/login", "/signup",
                "/detailed_web.html", "/detailed_ai.html", "/detailed_security.html", "/detailed_game.html",
                "/css/bootstrap.min.css", "/js/jquery.min.js", "/images/profile.png", "/fonts/bootstrap-icons.woff2"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        mvc.perform(get("/"))
            .andExpect(content().string(containsString("href=\"/login\"")))
            .andExpect(content().string(not(containsString("action=\"/logout\""))));
        mvc.perform(get("/testdb")).andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/login")).andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/signup")).andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void signupLoginSavedRequestAndLogoutWorkEndToEnd() throws Exception {
        String username = newUsername();
        signup(username);
        Member member = members.findByUsername(username).orElseThrow();
        assertThat(member.getPassword()).isNotEqualTo("123123").startsWith("$2");
        assertThat(encoder.matches("123123", member.getPassword())).isTrue();
        assertThat(member.getRole()).isEqualTo("USER");

        mvc.perform(post("/signup").with(csrf())
                .param("username", username).param("password", "123123")
                .param("passwordConfirm", "123123").param("name", "테스트 회원"))
            .andExpect(status().isOk()).andExpect(view().name("signup"))
            .andExpect(content().string(containsString("이미 사용 중인 아이디입니다.")))
            .andExpect(content().string(containsString("value=\"" + username + "\"")));

        MvcResult blocked = mvc.perform(get("/testdb")).andExpect(status().is3xxRedirection()).andReturn();
        MockHttpSession session = (MockHttpSession) blocked.getRequest().getSession(false);
        mvc.perform(post("/login").session(session).with(csrf())
                .param("username", username).param("password", "wrong-password"))
            .andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/login").session(session).with(csrf())
                .param("username", username).param("password", "123123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("http://localhost/testdb*"));
        mvc.perform(get("/testdb").param("continue", "").session(session))
            .andExpect(status().isOk()).andExpect(content().string(containsString("User List")));
        mvc.perform(get("/").session(session))
            .andExpect(content().string(containsString(username)))
            .andExpect(content().string(containsString("action=\"/logout\"")))
            .andExpect(content().string(not(containsString("href=\"/login\""))));

        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/testdb").session(session)).andExpect(status().isOk());
        mvc.perform(post("/logout").session(session).with(csrf()))
            .andExpect(redirectedUrl("/login?logout"))
            .andExpect(cookie().maxAge("JSESSIONID", 0));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/testdb")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/login?logout"))
            .andExpect(content().string(containsString("로그아웃 되었습니다.")));
    }

    @Test
    void signupChecksConfirmationAndRejectsMissingCsrf() throws Exception {
        String username = newUsername();
        mvc.perform(post("/signup").param("username", username)
                .param("password", "123123").param("passwordConfirm", "123123").param("name", "회원"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/signup").with(csrf()).param("username", username)
                .param("password", "123123").param("passwordConfirm", "different").param("name", "회원"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("비밀번호가 일치하지 않습니다.")));
        assertThat(members.existsByUsername(username)).isFalse();
        mvc.perform(post("/login").with(csrf()).param("username", username).param("password", "123123"))
            .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void samePasswordHasDifferentHashesAndDirectLoginReturnsHome() throws Exception {
        String first = newUsername();
        String second = newUsername();
        signup(first);
        signup(second);
        String firstHash = members.findByUsername(first).orElseThrow().getPassword();
        String secondHash = members.findByUsername(second).orElseThrow().getPassword();
        assertThat(firstHash).isNotEqualTo(secondHash);
        assertThat(encoder.matches("123123", firstHash)).isTrue();
        assertThat(encoder.matches("123123", secondHash)).isTrue();
        mvc.perform(post("/login").with(csrf()).param("username", first).param("password", "123123"))
            .andExpect(redirectedUrl("/"))
            .andExpect(cookie().doesNotExist("remember-me"));
    }

    @Test
    void rememberMeRestoresLoginWithoutSessionAndLogoutClearsCookie() throws Exception {
        String username = newUsername();
        signup(username);
        MvcResult login = mvc.perform(post("/login").with(csrf())
                .param("username", username).param("password", "123123")
                .param("remember-me", "on"))
            .andExpect(redirectedUrl("/"))
            .andExpect(cookie().maxAge("remember-me", 7 * 24 * 60 * 60))
            .andExpect(cookie().httpOnly("remember-me", true))
            .andReturn();
        Cookie rememberMe = login.getResponse().getCookie("remember-me");
        assertThat(rememberMe).isNotNull();
        // 브라우저를 닫은 상황처럼 세션을 버리고, 영구 쿠키만 보내 재접속한다.
        login.getRequest().getSession(false).invalidate();
        mvc.perform(get("/testdb").cookie(rememberMe))
            .andExpect(status().isOk());
        mvc.perform(get("/").cookie(rememberMe))
            .andExpect(content().string(containsString(username)))
            .andExpect(content().string(containsString("action=\"/logout\"")));
        mvc.perform(post("/logout").cookie(rememberMe).with(csrf()))
            .andExpect(redirectedUrl("/login?logout"))
            .andExpect(cookie().maxAge("remember-me", 0))
            .andExpect(cookie().maxAge("JSESSIONID", 0));
        mvc.perform(get("/testdb")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void invalidRememberMeCookieCannotAuthenticate() throws Exception {
        mvc.perform(get("/testdb").cookie(new Cookie("remember-me", "invalid-token")))
            .andExpect(redirectedUrl("/login"))
            .andExpect(cookie().maxAge("remember-me", 0));
    }
}
