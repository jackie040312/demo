package com.example.demo.model.service;

import java.nio.charset.StandardCharsets;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.demo.model.domain.Member;
import com.example.demo.model.dto.MemberForm;
import com.example.demo.model.repository.MemberRepository;

@Service
public class MemberService implements UserDetailsService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Member signup(MemberForm form) {
        validate(form);
        if (memberRepository.existsByUsername(form.getUsername())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        Member member = new Member();
        member.setUsername(form.getUsername());
        member.setPassword(passwordEncoder.encode(form.getPassword()));
        member.setName(form.getName().strip());
        member.setRole("USER");
        try {
            return memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 아이디로 가입한 경우에도 중복 안내를 보여 준다.
            if (memberRepository.existsByUsername(form.getUsername())) {
                throw new IllegalArgumentException("이미 사용 중인 아이디입니다.", e);
            }
            throw e;
        }
    }

    private void validate(MemberForm form) {
        if (form.getUsername() == null || !form.getUsername().matches("[A-Za-z0-9]{4,20}")) {
            throw new IllegalArgumentException("아이디는 영문/숫자 4~20자로 입력해 주세요.");
        }
        String password = form.getPassword();
        if (password == null || password.isBlank() || password.length() < 4
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("비밀번호는 4자 이상, UTF-8 기준 72바이트 이내로 입력해 주세요.");
        }
        if (!password.equals(form.getPasswordConfirm())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        if (form.getName() == null || form.getName().isBlank() || form.getName().length() > 50) {
            throw new IllegalArgumentException("이름은 1~50자로 입력해 주세요.");
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Member member = memberRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("회원을 찾을 수 없습니다."));
        return User.builder()
            .username(member.getUsername())
            .password(member.getPassword())
            .roles(member.getRole())
            .build();
    }
}
