package com.toeic.backend.identity.infrastructure;

import com.toeic.backend.identity.application.AuthException;
import com.toeic.backend.identity.application.SessionAuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerSessionFilter extends OncePerRequestFilter {
    private final SessionAuthenticationService sessions;
    private final AuthErrorWriter errors;

    public BearerSessionFilter(SessionAuthenticationService sessions, AuthErrorWriter errors) {
        this.sessions = sessions;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return IdentitySecurityConfig.isPublicRequest(request.getMethod(), path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var headers = Collections.list(request.getHeaders("Authorization"));
        if (!headers.isEmpty()) {
            try {
                var header = headers.getFirst();
                if (headers.size() != 1 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    throw AuthException.invalidToken();
                }
                var principal = sessions.authenticate(header.substring(7));
                var authorities = principal.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
                var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            } catch (AuthException exception) {
                SecurityContextHolder.clearContext();
                errors.write(response, 401, exception.getCode(), exception.getMessage());
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
