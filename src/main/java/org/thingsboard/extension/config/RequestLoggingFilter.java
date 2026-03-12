/**
 * Copyright © 2026-2026 ThingsBoard, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.extension.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String AUTH_HEADER = "X-Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String API_KEY_PREFIX = "ApiKey ";
    private static final int MAX_BODY_LOG_LENGTH = 500;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("--> {} {} ({})", request.getMethod(), request.getRequestURI(), identifyAuth(request));

        long start = System.currentTimeMillis();
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(request, wrappedResponse);
        } finally {
            long duration = System.currentTimeMillis() - start;
            int status = wrappedResponse.getStatus();

            String body = truncate(new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8));
            if (body.isEmpty()) {
                log.debug("<-- {} ({}ms)", status, duration);
            } else {
                log.debug("<-- {} ({}ms) {}", status, duration, body);
            }

            wrappedResponse.copyBodyToResponse();
        }
    }

    private String identifyAuth(HttpServletRequest request) {
        String value = request.getHeader(AUTH_HEADER);
        if (value == null || value.isBlank()) {
            return "auth: none";
        }
        if (value.startsWith(BEARER_PREFIX)) {
            return "JWT: " + maskCredential(value.substring(BEARER_PREFIX.length()).trim());
        }
        if (value.startsWith(API_KEY_PREFIX)) {
            return "ApiKey: " + maskCredential(value.substring(API_KEY_PREFIX.length()).trim());
        }
        return "auth: unknown";
    }

    private String maskCredential(String credential) {
        if (credential == null || credential.isBlank()) {
            return "none";
        }
        if (credential.length() <= 6) {
            return "***";
        }
        return credential.substring(0, 6) + "***";
    }

    private String truncate(String body) {
        if (body.length() <= MAX_BODY_LOG_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_BODY_LOG_LENGTH) + "...";
    }

}
