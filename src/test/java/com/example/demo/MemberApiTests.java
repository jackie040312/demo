package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.model.domain.Member;
import com.example.demo.model.repository.MemberRepository;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class MemberApiTests {
    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder encoder;

    @Test
    void helloApiIsPublicAndReturnsJson() throws Exception {
        mvc.perform(get("/api/hello"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message").value("안녕하세요, REST API!"))
            .andExpect(jsonPath("$.time").exists());
    }

    @Test
    void meApiRequiresLoginAndNeverExposesPassword() throws Exception {
        mvc.perform(get("/api/me"))
            .andExpect(status().isUnauthorized());

        String username = "api" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Member member = new Member();
        member.setUsername(username);
        member.setPassword(encoder.encode("123123"));
        member.setName("API 테스트 회원");
        member.setRole("USER");
        Member saved = members.saveAndFlush(member);

        mvc.perform(get("/api/me").with(httpBasic(username, "wrong-password")))
            .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/me").with(httpBasic(username, "123123")))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(saved.getId()))
            .andExpect(jsonPath("$.username").value(username))
            .andExpect(jsonPath("$.name").value("API 테스트 회원"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(content().string(not(containsString("password"))))
            .andExpect(content().string(not(containsString(saved.getPassword()))));
    }

    @Test
    void adminMemberApiReturnsDtoListAndNotFoundStatus() throws Exception {
        String username = "list" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Member member = new Member();
        member.setUsername(username);
        member.setPassword(encoder.encode("123123"));
        member.setName("목록 API 회원");
        member.setRole("USER");
        Member saved = members.saveAndFlush(member);

        mvc.perform(get("/api/admin/members"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/members").with(user("student1").roles("USER")))
            .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/members").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().string(containsString(username)))
            .andExpect(content().string(not(containsString(saved.getPassword()))));

        mvc.perform(get("/api/admin/members/{id}", saved.getId())
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(saved.getId()))
            .andExpect(jsonPath("$.username").value(username))
            .andExpect(jsonPath("$.name").value("목록 API 회원"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(content().string(not(containsString("password"))));

        mvc.perform(get("/api/admin/members/{id}", Long.MAX_VALUE)
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isNotFound());
    }

    @Test
    void adminMemberApiSearchesUsernameOrName() throws Exception {
        Member usernameMatch = saveMember("student", "검색 대상 A", "USER");
        Member nameMatch = saveMember("other", "Student 이름", "USER");
        Member noMatch = saveMember("irrelevant", "검색 제외", "USER");

        mvc.perform(get("/api/admin/members")
                .param("keyword", "student")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(usernameMatch.getUsername())))
            .andExpect(content().string(containsString(nameMatch.getUsername())))
            .andExpect(content().string(not(containsString(noMatch.getUsername()))))
            .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void adminCanCreateMemberFromJsonWithUserRoleAndEncryptedPassword() throws Exception {
        String username = "post" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String json = """
            {
              "username": "%s",
              "password": "1234",
              "passwordConfirm": "1234",
              "name": "포스트맨",
              "role": "ADMIN"
            }
            """.formatted(username);

        mvc.perform(post("/api/admin/members")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", containsString("/api/admin/members/")))
            .andExpect(jsonPath("$.username").value(username))
            .andExpect(jsonPath("$.name").value("포스트맨"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(content().string(not(containsString("password"))));

        Member saved = members.findByUsername(username).orElseThrow();
        assertEquals("USER", saved.getRole());
        assertNotEquals("1234", saved.getPassword());
        assertTrue(encoder.matches("1234", saved.getPassword()));
    }

    @Test
    void adminMemberCreationRejectsDuplicateAndMissingValues() throws Exception {
        String username = "dup" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Member member = new Member();
        member.setUsername(username);
        member.setPassword(encoder.encode("1234"));
        member.setName("기존 회원");
        member.setRole("USER");
        members.saveAndFlush(member);

        String duplicateJson = """
            {"username":"%s","password":"1234","passwordConfirm":"1234","name":"중복 회원"}
            """.formatted(username);
        mvc.perform(post("/api/admin/members")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(duplicateJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("이미 사용 중인 아이디입니다."));

        mvc.perform(post("/api/admin/members")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"onlyUsername\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("아이디, 비밀번호, 이름은 필수입니다."));
    }

    @Test
    void adminCanChangeRoleWhileInvalidAndSelfChangesAreRejected() throws Exception {
        Member target = saveMember("role", "권한 변경 회원", "USER");

        mvc.perform(patch("/api/admin/members/{id}/role", target.getId())
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(target.getId()))
            .andExpect(jsonPath("$.role").value("ADMIN"))
            .andExpect(content().string(not(containsString("password"))));
        assertEquals("ADMIN", members.findById(target.getId()).orElseThrow().getRole());

        mvc.perform(patch("/api/admin/members/{id}/role", target.getId())
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"SUPER\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(containsString("허용되지 않는 권한")));

        Member signedInAdmin = saveMember("self", "본인 보호 관리자", "ADMIN");
        mvc.perform(patch("/api/admin/members/{id}/role", signedInAdmin.getId())
                .with(user(signedInAdmin.getUsername()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"USER\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("자기 자신의 권한 변경·삭제는 할 수 없습니다."));
    }

    @Test
    void onlyAdminCanDeleteMemberAndSuccessfulDeletionReturnsNoContent() throws Exception {
        Member target = saveMember("delete", "삭제 대상 회원", "USER");

        mvc.perform(delete("/api/admin/members/{id}", target.getId())
                .with(user("student1").roles("USER")))
            .andExpect(status().isForbidden());
        assertTrue(members.existsById(target.getId()));

        mvc.perform(delete("/api/admin/members/{id}", target.getId())
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));
        assertTrue(members.findById(target.getId()).isEmpty());
    }

    private Member saveMember(String prefix, String name, String role) {
        Member member = new Member();
        member.setUsername(prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        member.setPassword(encoder.encode("1234"));
        member.setName(name);
        member.setRole(role);
        return members.saveAndFlush(member);
    }

    @Test
    void apiMemberScreenIsAdminOnly() throws Exception {
        mvc.perform(get("/admin/members-api"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin/members-api").with(user("student1").roles("USER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/admin/members-api").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/members-api"));
    }
}
