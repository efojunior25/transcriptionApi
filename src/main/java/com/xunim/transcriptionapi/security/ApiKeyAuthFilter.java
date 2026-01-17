package com.xunim.transcriptionapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    @Value("${TRANSCRIPTION_API_KEY}")
    private String validApiKey;

    private static final String API_KEY_HEADER = "X-API-Key";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Permite acesso a endpoints públicos (health check, docs, etc.)
        if (isPublicEndpoint(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("Tentativa de acesso sem API Key de: {}", request.getRemoteAddr());
            sendUnauthorizedResponse(response, "API Key obrigatória");
            return;
        }

        if (!validApiKey.equals(apiKey)) {
            log.warn("Tentativa de acesso com API Key inválida de: {}", request.getRemoteAddr());
            sendUnauthorizedResponse(response, "API Key inválida");
            return;
        }

        // API Key válida, continua a requisição
        filterChain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/actuator") ||
                path.startsWith("/swagger") ||
                path.startsWith("/v3/api-docs") ||
                path.startsWith("/h2-console");
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format(
                "{\"error\": \"%s\", \"status\": 401}", message
        ));
    }
}