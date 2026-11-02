package com.toeic.backend.identity.infrastructure;

import com.toeic.backend.identity.application.AuthProperties;
import com.toeic.backend.identity.application.SessionAuthenticationService;
import jakarta.servlet.DispatcherType;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
            "/api/v1/auth/register", "/api/v1/auth/verify-email", "/api/v1/auth/resend-otp", "/api/v1/auth/login");
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
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me").hasAuthority("user:read:self")
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
