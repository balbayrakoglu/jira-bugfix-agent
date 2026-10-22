package com.example.minijira.api;

import com.example.minijira.config.MiniJiraProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class ApiAuthFilter {

    public static final String USER_ATTRIBUTE = "miniJira.user";

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> apiAuthFilterRegistration(MiniJiraProperties properties) {
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(
                new Filter(properties.username(), properties.apiToken()));
        registration.addUrlPatterns("/rest/*");
        return registration;
    }

    static final class Filter extends OncePerRequestFilter {

        private final String username;
        private final byte[] token;

        Filter(String username, String token) {
            this.username = username;
            this.token = token.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (authenticate(request.getHeader("Authorization"))) {
                request.setAttribute(USER_ATTRIBUTE, username);
                chain.doFilter(request, response);
                return;
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("WWW-Authenticate", "Basic realm=\"mini-jira\"");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"errorMessages\":[\"You are not authenticated.\"],\"errors\":{}}");
        }

        boolean authenticate(String header) {
            if (header == null) {
                return false;
            }
            if (header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return equal(header.substring(7).strip());
            }
            if (header.regionMatches(true, 0, "Basic ", 0, 6)) {
                String decoded;
                try {
                    decoded = new String(Base64.getDecoder().decode(header.substring(6).strip()), StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    return false;
                }
                int colon = decoded.indexOf(':');
                return colon > 0 && decoded.substring(0, colon).equals(username) && equal(decoded.substring(colon + 1));
            }
            return false;
        }

        private boolean equal(String provided) {
            return MessageDigest.isEqual(token, provided.getBytes(StandardCharsets.UTF_8));
        }
    }
}
