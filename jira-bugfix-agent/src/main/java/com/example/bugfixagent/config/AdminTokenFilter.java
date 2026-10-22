package com.example.bugfixagent.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class AdminTokenFilter {

    static final String HEADER = "X-Admin-Token";

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> adminTokenFilterRegistration(AgentProperties properties) {
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(new Filter(properties.adminToken()));
        registration.addUrlPatterns("/api/*");
        return registration;
    }

    static final class Filter extends OncePerRequestFilter {

        private final byte[] expectedToken;

        Filter(String adminToken) {
            this.expectedToken = adminToken == null || adminToken.isBlank()
                    ? null
                    : adminToken.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String provided = request.getHeader(HEADER);
            boolean authorized = expectedToken != null
                    && provided != null
                    && MessageDigest.isEqual(expectedToken, provided.getBytes(StandardCharsets.UTF_8));
            if (!authorized) {
                response.sendError(HttpStatus.UNAUTHORIZED.value());
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
