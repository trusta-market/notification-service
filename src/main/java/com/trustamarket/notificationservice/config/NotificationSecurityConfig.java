package com.trustamarket.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

// AlertManager webhook + Prometheus scrape 경로만 매칭하는 별도 filter chain.
// common 의 SecurityConfig 가 anyRequest().authenticated() 를 깔고 있어
// 같은 빈에서 permitAll 로 override 하면 "두 chain 이 any request 매칭" 충돌 → securityMatcher 로 분리.
//
// 운영 시 외부 노출되면 별도 secret header / mTLS 추가 필요 (현재 MVP scope 외 — PRD §11 위험).
@Configuration
public class NotificationSecurityConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain notificationSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/v1/alerts/**", "/actuator/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
