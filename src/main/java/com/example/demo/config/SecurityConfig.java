package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            UserDetailsService userDetailsService,
            @Value("${app.security.remember-me-key}") String rememberMeKey) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/hello", "/hello2", "/login", "/signup", "/error",
                    "/detailed_web.html", "/detailed_ai.html",
                    "/detailed_security.html", "/detailed_game.html").permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**", "/fonts/**").permitAll()
                .requestMatchers("/api/hello").permitAll()
                .requestMatchers("/testdb").hasAnyRole("ADMIN", "MANAGER")
                .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/")
                .failureUrl("/login?error")
                .permitAll())
            .rememberMe(remember -> remember
                .key(rememberMeKey)
                .tokenValiditySeconds(7 * 24 * 60 * 60)
                .userDetailsService(userDetailsService))
            // API 클라이언트는 요청마다 Basic 인증 정보를 보낼 수 있다.
            .httpBasic(Customizer.withDefaults())
            // JSON API는 폼 CSRF 토큰 대신 서버의 인증·권한 규칙으로 보호한다.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            // API는 로그인 HTML로 이동하지 않고 HTTP 401 상태를 반환한다.
            .exceptionHandling(ex -> ex
                // API 요청만 리다이렉트 없이 401 상태를 반환한다.
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    request -> request.getRequestURI().startsWith("/api/"))
                // 그 밖의 화면 요청은 기존처럼 로그인 페이지로 이동한다.
                .defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    request -> !request.getRequestURI().startsWith("/api/")))
            // Basic 인증 설정보다 뒤에 두어 화면 로그아웃은 기존 리다이렉트를 유지한다.
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "remember-me"));
        return http.build();
    }
}
