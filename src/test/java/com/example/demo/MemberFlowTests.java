package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.model.domain.Member;
import com.example.demo.model.repository.MemberRepository;
import com.example.demo.model.service.MemberService;
import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional // 테스트에서 만든 회원은 종료 후 롤백한다.
class MemberFlowTests {
    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder encoder;
    @Autowired MemberService memberService;

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
        mvc.perform(get("/mypage")).andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin/members")).andExpect(status().is3xxRedirection())
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

        MvcResult blocked = mvc.perform(get("/mypage")).andExpect(status().is3xxRedirection()).andReturn();
        MockHttpSession session = (MockHttpSession) blocked.getRequest().getSession(false);
        mvc.perform(post("/login").session(session).with(csrf())
                .param("username", username).param("password", "wrong-password"))
            .andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/login").session(session).with(csrf())
                .param("username", username).param("password", "123123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("http://localhost/mypage*"));
        mvc.perform(get("/mypage").param("continue", "").session(session))
            .andExpect(status().isOk()).andExpect(content().string(containsString("내 정보")));
        mvc.perform(get("/").session(session))
            .andExpect(content().string(containsString(username)))
            .andExpect(content().string(containsString("action=\"/logout\"")))
            .andExpect(content().string(not(containsString("href=\"/login\""))));

        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/testdb").session(session)).andExpect(status().isForbidden());
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
        mvc.perform(get("/mypage").cookie(rememberMe))
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

    @Test
    void mypageUsesLoggedInUsernameAndIgnoresRequestedMemberId() throws Exception {
        String loggedInUsername = newUsername();
        String anotherUsername = newUsername();
        signup(loggedInUsername);
        signup(anotherUsername);
        Member loggedInMember = members.findByUsername(loggedInUsername).orElseThrow();
        Member anotherMember = members.findByUsername(anotherUsername).orElseThrow();

        MvcResult login = mvc.perform(post("/login").with(csrf())
                .param("username", loggedInUsername).param("password", "123123"))
            .andExpect(redirectedUrl("/"))
            .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(get("/mypage").param("id", anotherMember.getId().toString()).session(session))
            .andExpect(status().isOk())
            .andExpect(view().name("mypage"))
            .andExpect(model().attribute("member", loggedInMember))
            .andExpect(content().string(containsString(loggedInUsername)))
            .andExpect(content().string(not(containsString(anotherUsername))))
            .andExpect(content().string(containsString("ROLE_USER")));
    }

    @Test
    void adminMemberManagementListsChangesAndDeletesMembers() throws Exception {
        String roleTarget = newUsername();
        String deleteTarget = newUsername();
        signup(roleTarget);
        signup(deleteTarget);
        Member roleMember = members.findByUsername(roleTarget).orElseThrow();
        Member deleteMember = members.findByUsername(deleteTarget).orElseThrow();

        mvc.perform(get("/admin/members").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/members"))
            .andExpect(model().attributeExists("members"))
            .andExpect(content().string(containsString(roleTarget)))
            .andExpect(content().string(containsString(deleteTarget)));

        mvc.perform(post("/admin/members/{id}/role", roleMember.getId())
                .with(user("admin").roles("ADMIN")).with(csrf()).param("role", "ADMIN"))
            .andExpect(redirectedUrl("/admin/members"))
            .andExpect(flash().attribute("message", "권한을 ADMIN (으)로 변경했습니다."));
        assertThat(members.findById(roleMember.getId()).orElseThrow().getRole()).isEqualTo("ADMIN");

        mvc.perform(post("/admin/members/{id}/role", roleMember.getId())
                .with(user("admin").roles("ADMIN")).with(csrf()).param("role", "SUPER"))
            .andExpect(redirectedUrl("/admin/members"))
            .andExpect(flash().attribute("error", "허용되지 않는 권한입니다 : SUPER"));
        assertThat(members.findById(roleMember.getId()).orElseThrow().getRole()).isEqualTo("ADMIN");

        mvc.perform(post("/admin/members/{id}/delete", deleteMember.getId())
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(redirectedUrl("/admin/members"))
            .andExpect(flash().attribute("message", "회원을 삭제했습니다."));
        assertThat(members.existsById(deleteMember.getId())).isFalse();

        mvc.perform(post("/admin/members/{id}/delete", Long.MAX_VALUE)
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(redirectedUrl("/admin/members"))
            .andExpect(flash().attribute("error", "회원이 존재하지 않습니다."));
        mvc.perform(post("/admin/members/{id}/delete", roleMember.getId())
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isForbidden());
    }

    @Test
    void userCannotOpenOrModifyAdminResources() throws Exception {
        String targetUsername = newUsername();
        signup(targetUsername);
        Member target = members.findByUsername(targetUsername).orElseThrow();

        mvc.perform(get("/admin/members").with(user("student1").roles("USER")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/admin/members/{id}/role", target.getId())
                .with(user("student1").roles("USER")).with(csrf()).param("role", "ADMIN"))
            .andExpect(status().isForbidden());
        assertThat(members.findById(target.getId()).orElseThrow().getRole()).isEqualTo("USER");

        mvc.perform(post("/admin/members/{id}/delete", target.getId())
                .with(user("student1").roles("USER")).with(csrf()))
            .andExpect(status().isForbidden());
        assertThat(members.existsById(target.getId())).isTrue();
    }

    @Test
    void testdbAllowsManagerAndAdminButBlocksUser() throws Exception {
        mvc.perform(get("/testdb").with(user("student1").roles("USER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/").with(user("student1").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("href=\"/testdb\""))));

        mvc.perform(get("/testdb").with(user("manager").roles("MANAGER")))
            .andExpect(status().isOk());
        mvc.perform(get("/").with(user("manager").roles("MANAGER")))
            .andExpect(content().string(containsString("href=\"/testdb\"")))
            .andExpect(content().string(containsString("MANAGER")));

        mvc.perform(get("/testdb").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk());
        mvc.perform(get("/admin/members").with(user("manager").roles("MANAGER")))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "page30selfadmin", roles = "ADMIN")
    void adminCannotChangeOrDeleteOwnAccount() {
        Member self = new Member();
        self.setUsername("page30selfadmin");
        self.setPassword(encoder.encode("123123"));
        self.setName("자기 계정 보호 테스트");
        self.setRole("ADMIN");
        Member savedSelf = members.saveAndFlush(self);

        assertThatThrownBy(() -> memberService.changeRole(savedSelf.getId(), "USER"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("자기 자신의 권한 변경·삭제는 할 수 없습니다.");
        assertThat(members.findById(savedSelf.getId()).orElseThrow().getRole()).isEqualTo("ADMIN");

        assertThatThrownBy(() -> memberService.deleteMember(savedSelf.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("자기 자신의 권한 변경·삭제는 할 수 없습니다.");
        assertThat(members.existsById(savedSelf.getId())).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void serviceMethodsBlockUserEvenWhenCalledWithoutAdminUrl() {
        String targetUsername = newUsername();
        Member target = new Member();
        target.setUsername(targetUsername);
        target.setPassword(encoder.encode("123123"));
        target.setName("메서드 보안 테스트");
        target.setRole("USER");
        Member savedTarget = members.saveAndFlush(target);

        assertThatThrownBy(() -> memberService.changeRole(savedTarget.getId(), "ADMIN"))
            .isInstanceOf(AccessDeniedException.class);
        assertThat(members.findById(savedTarget.getId()).orElseThrow().getRole()).isEqualTo("USER");

        assertThatThrownBy(() -> memberService.deleteMember(savedTarget.getId()))
            .isInstanceOf(AccessDeniedException.class);
        assertThat(members.existsById(savedTarget.getId())).isTrue();
    }
}
