package com.bloom.app.config;

import com.bloom.app.api.dto.response.ApiResponse;
import com.bloom.app.domain.properties.CorsProperties;
import com.bloom.app.web.security.AuthenticatedSessionService;
import com.bloom.app.web.security.SessionAccountValidationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticatedSessionService authenticatedSessionService,
            ObjectMapper objectMapper) throws Exception {
        SessionAccountValidationFilter sessionAccountValidationFilter =
            new SessionAccountValidationFilter(authenticatedSessionService, objectMapper);

        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(authorizeRequests ->
                authorizeRequests
                    .requestMatchers(
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/api/auth/login",
                        "/api/auth/logout"
                    )
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            )
            .sessionManagement(session -> session
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(unauthorizedEntryPoint(objectMapper))
            )
            .addFilterBefore(sessionAccountValidationFilter, AuthorizationFilter.class)
            .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        configuration.setAllowedMethods(corsProperties.getAllowedMethods());
        configuration.setAllowedHeaders(corsProperties.getAllowedHeaders());
        configuration.setAllowCredentials(corsProperties.getAllowCredentials());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.fail("Unauthorized", HttpServletResponse.SC_UNAUTHORIZED)
            );
        };
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
