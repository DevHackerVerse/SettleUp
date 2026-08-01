package com.settleup.config;

import com.settleup.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration.
 *
 * Policy:
 *  - Stateless (JWT; no HTTP sessions)
 *  - CSRF disabled (REST API — no browser form submissions)
 *  - /api/v1/auth/** is public; everything else requires a valid JWT
 *  - WebSocket endpoint /ws/** is public at the HTTP level
 *    (WS-level auth handled separately when needed)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt with default strength (10 rounds) — spec requirement
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF not needed for a stateless JWT API
            .csrf(AbstractHttpConfigurer::disable)

            // No sessions — pure stateless
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                // Auth endpoints are public
                .requestMatchers("/api/v1/auth/**").permitAll()
                // WebSocket handshake is public at HTTP level
                .requestMatchers("/ws/**").permitAll()
                // Actuator health is public
                .requestMatchers("/actuator/health").permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            // JWT filter runs before the standard username/password filter
            .addFilterBefore(jwtAuthFilter,
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
