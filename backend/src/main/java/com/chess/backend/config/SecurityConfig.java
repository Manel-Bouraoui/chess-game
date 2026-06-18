package com.chess.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.chess.backend.security.OptionalApiKeyAuthFilter;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configure HTTP security and CORS
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            OptionalApiKeyAuthFilter apiKeyAuthFilter) throws Exception {
        http
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // CORS configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // CSRF protection (can be disabled for stateless APIs)
                .csrf(csrf -> csrf.disable())

                // Allow WebSocket connections
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        .requestMatchers("/api/**").permitAll()
                        .anyRequest().permitAll())

                // Stateless session (no cookies for API)
                .sessionManagement(session -> session
                        .sessionFixation().migrateSession()
                        .sessionCreationPolicy(
                                org.springframework.security.config.http.SessionCreationPolicy.STATELESS));

        return http.build();
    }

    /**
     * CORS configuration for frontend and cross-origin requests
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allowed origins
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:5173", // Frontend dev server
                "http://localhost:3000", // Alternative port
                "http://localhost:8080", // Same machine
                "https://localhost", // HTTPS localhost
                "http://127.0.0.1:5173" // IP-based access
        ));

        // Allowed methods
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // Allowed headers
        configuration.setAllowedHeaders(Arrays.asList(
                "Content-Type",
                "Authorization",
                "X-Requested-With",
                "Accept",
                "Accept-Language",
                "Content-Language",
                "Last-Event-ID"));

        // Expose headers to frontend
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "X-Total-Count"));

        // Allow credentials (cookies, auth headers)
        configuration.setAllowCredentials(true);

        // Max age of preflight request cache
        configuration.setMaxAge(3600L);

        // Register configuration for all paths
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

}
