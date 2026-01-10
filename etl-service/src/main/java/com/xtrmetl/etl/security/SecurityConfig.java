package com.xtrmetl.etl.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 애플리케이션의 HTTP 보안 필터 체인을 구성한다.
     *
     * 구성 내용: CSRF 비활성화, "/api/**" 경로에 대해 인증 요구, 다른 모든 요청 허용, HTTP Basic 인증 활성화.
     *
     * @param http 구성에 사용되는 HttpSecurity 인스턴스
     * @return 구성된 SecurityFilterChain 인스턴스
     * @throws Exception 보안 구성을 적용하는 동안 오류가 발생한 경우
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(new AntPathRequestMatcher("/api/**")).authenticated()
                .anyRequest().permitAll()
            )
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}