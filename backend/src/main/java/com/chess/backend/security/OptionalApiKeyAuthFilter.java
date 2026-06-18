package com.chess.backend.security;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * When {@code security.api-key} is non-empty, requires matching {@code X-API-Key}
 * on {@code /api/**} except health/test endpoints. Empty property disables the check.
 */
@Component
public class OptionalApiKeyAuthFilter extends OncePerRequestFilter {

    @Value("${security.api-key:}")
    private String apiKey;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (apiKey == null || apiKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        if (isExempt(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("X-API-Key");
        if (apiKey.equals(header)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Unauthorized\"}");
    }

    private boolean isExempt(String uri) {
        return uri.startsWith("/ws")
                || "/api/health".equals(uri)
                || "/api/test".equals(uri)
                || uri.startsWith("/actuator/health");
    }
}
