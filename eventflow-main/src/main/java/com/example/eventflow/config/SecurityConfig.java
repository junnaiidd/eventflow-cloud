package com.example.eventflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import com.example.eventflow.security.CsrfCookieFilter;

/**
 * Session-cookie authentication for the single-page UI. Every /api endpoint needs a login; the rules
 * below add the role restrictions. Ownership checks (organizer may only touch their own events) live
 * in the services via {@code AccessPolicy}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository)
            throws Exception {
        http
            .securityContext(ctx -> ctx.securityContextRepository(contextRepository))
            .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .ignoringRequestMatchers("/h2-console/**"))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())) // needed by the local H2 console only
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/analytics/**", "/api/checkin/**", "/api/events/analytics")
                        .hasAnyRole("ORGANIZER", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/events").hasAnyRole("ORGANIZER", "ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/events/*").hasAnyRole("ORGANIZER", "ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/events/*").hasAnyRole("ORGANIZER", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/events/*/poster").hasAnyRole("ORGANIZER", "ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/events/*/poster").hasAnyRole("ORGANIZER", "ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/events/*/registrations").hasAnyRole("ORGANIZER", "ADMIN")
                    .requestMatchers("/api/events/*/certificates", "/api/events/*/certificates/**")
                        .hasAnyRole("ORGANIZER", "ADMIN")
                    .requestMatchers("/api/**").authenticated()
                    // static files (index.html, js, css), the actuator health probe and the local H2 console
                    .anyRequest().permitAll());
        return http.build();
    }
}
