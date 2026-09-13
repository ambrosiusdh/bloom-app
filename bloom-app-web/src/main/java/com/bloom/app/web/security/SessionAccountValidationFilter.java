package com.bloom.app.web.security;

import com.bloom.app.api.dto.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class SessionAccountValidationFilter extends OncePerRequestFilter {
    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/api/auth/login",
        "/api/auth/logout",
        "/actuator/health"
    );

    private final AuthenticatedSessionService authenticatedSessionService;
    private final ObjectMapper objectMapper;

    public SessionAccountValidationFilter(
            AuthenticatedSessionService authenticatedSessionService,
            ObjectMapper objectMapper) {
        this.authenticatedSessionService = authenticatedSessionService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken);

        if (!authenticated) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            authenticatedSessionService.requireFreshAccount(request);
            filterChain.doFilter(request, response);
        } catch (SessionIdentityException exception) {
            SecurityContextHolder.clearContext();
            writeUnauthorized(response, exception.getMessage());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        return "OPTIONS".equalsIgnoreCase(request.getMethod())
            || PUBLIC_PATHS.contains(path)
            || path.startsWith("/actuator/health/")
            || path.startsWith("/v3/api-docs/")
            || path.startsWith("/swagger-ui/")
            || "/swagger-ui.html".equals(path);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
            response.getOutputStream(),
            ApiResponse.fail(message, HttpServletResponse.SC_UNAUTHORIZED)
        );
    }
}
