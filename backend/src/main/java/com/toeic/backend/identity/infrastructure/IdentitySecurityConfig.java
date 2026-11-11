package com.toeic.backend.identity.infrastructure;

import com.toeic.backend.identity.application.AuthProperties;
import com.toeic.backend.identity.application.SessionAuthenticationService;
import jakarta.servlet.DispatcherType;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class IdentitySecurityConfig {
    private static final Set<String> PUBLIC_POST_PATHS = Set.of(
            "/api/v1/auth/register", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password", "/api/v1/auth/login",
            "/api/v1/auth/google/challenge", "/api/v1/auth/google");
    private static final Set<String> PUBLIC_GET_PATHS = Set.of("/api/health", "/api/health/database");

    static boolean isPublicRequest(String method, String path) {
        return ("POST".equals(method) && PUBLIC_POST_PATHS.contains(path))
                || ("GET".equals(method) && PUBLIC_GET_PATHS.contains(path));
    }

    @Bean
    public SecurityFilterChain identitySecurity(HttpSecurity http, SessionAuthenticationService sessions,
                                                AuthErrorWriter errors) throws Exception {
        return http
                // Credentials are sent only in Authorization, never cookies or HTTP sessions.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(config -> config
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATHS.toArray(String[]::new)).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS.toArray(String[]::new)).permitAll()
                        // Optional bearer auth is still processed; content service filters public/member visibility.
                        .requestMatchers(HttpMethod.GET, "/api/v1/contents", "/api/v1/contents/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/tests", "/api/v1/tests/*").permitAll()
                        .requestMatchers("/api/v1/teacher/tests", "/api/v1/teacher/tests/**").hasAnyAuthority("test:manage:self", "test:manage:any")
                        .requestMatchers("/api/v1/teacher/media").hasAuthority("media:upload")
                        .requestMatchers("/api/v1/teacher/score-conversions").hasAnyAuthority("test:manage:self", "test:manage:any")
                        .requestMatchers("/api/v1/exam-attempts", "/api/v1/exam-attempts/**", "/api/v1/users/me/exam-history", "/api/v1/users/me/exam-progress").hasAnyAuthority("test:take", "practice:manage:self")
                        .requestMatchers("/api/v1/teacher/contents", "/api/v1/teacher/contents/**")
                        .hasAnyAuthority("content:manage:self", "content:manage:any")
                        .requestMatchers("/api/v1/teacher/questions", "/api/v1/teacher/questions/**",
                                "/api/v1/teacher/question-groups", "/api/v1/teacher/question-groups/**")
                        .hasAnyAuthority("question:manage:self", "question:manage:any")
                        .requestMatchers("/api/v1/attempts", "/api/v1/attempts/**").hasAuthority("practice:manage:self")
                        .requestMatchers(HttpMethod.POST, "/api/v1/contents/*/bookmarks").hasAuthority("content:bookmark:self")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/contents/*/bookmarks").hasAuthority("content:bookmark:self")
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me/bookmarks").hasAuthority("content:bookmark:self")
                        .requestMatchers(HttpMethod.POST, "/api/v1/contents/*/history").hasAuthority("content:history:self")
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me/content-history").hasAuthority("content:history:self")
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me").hasAuthority("user:read:self")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/me").access(AuthorizationManagers.allOf(
                                AuthorityAuthorizationManager.hasAuthority("user:read:self"),
                                AuthorityAuthorizationManager.hasAuthority("user:update:self")))
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/me/password")
                        .hasAuthority("user:password:change:self")
                        .requestMatchers(HttpMethod.GET, "/api/v1/questions", "/api/v1/questions/*").access(
                                AuthorizationManagers.allOf(AuthorityAuthorizationManager.hasAuthority("question:read"),
                                        AuthorityAuthorizationManager.hasAnyRole("TEACHER", "ADMIN")))
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(config -> config
                        .authenticationEntryPoint((request, response, exception) -> errors.write(
                                response, 401, "UNAUTHENTICATED", "Vui long dang nhap."))
                        .accessDeniedHandler((request, response, exception) -> errors.write(
                                response, 403, "FORBIDDEN", "Ban khong co quyen truy cap.")))
                .addFilterBefore(new BearerSessionFilter(sessions, errors), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
