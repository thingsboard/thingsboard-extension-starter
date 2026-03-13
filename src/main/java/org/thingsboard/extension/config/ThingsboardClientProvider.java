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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;
import org.thingsboard.client.ApiException;
import org.thingsboard.client.ThingsboardClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * Provides ThingsboardClient instances via the unified X-Authorization header.
 * <p>
 * Supports two authentication schemes:
 * <ul>
 *   <li>{@code X-Authorization: Bearer <jwt>} — creates a JWT-based client via {@code setToken()}</li>
 *   <li>{@code X-Authorization: ApiKey <key>} — creates an API key client via {@code builder().apiKey()}</li>
 * </ul>
 * <p>
 * Also resolves {@link ThingsboardClient} parameters in controller methods automatically.
 * Clients are cached with namespaced keys ({@code "jwt:"} and {@code "apikey:"} prefixes)
 * to prevent collisions between the two auth types.
 */
@Slf4j
@Component
public class ThingsboardClientProvider implements HandlerMethodArgumentResolver {

    private static final String AUTH_HEADER = "X-Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String API_KEY_PREFIX = "ApiKey ";

    private final String thingsboardUrl;
    private final Cache<String, ThingsboardClient> clients;

    public ThingsboardClientProvider(@Value("${thingsboard.url}") String thingsboardUrl,
                                     @Value("${thingsboard.client.cache-ttl:60}") long cacheTtlMinutes,
                                     @Value("${thingsboard.client.cache-max-size:100}") long cacheMaxSize) {
        this.thingsboardUrl = thingsboardUrl;
        this.clients = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(cacheMaxSize)
                .build();
    }

    private ThingsboardClient getClientForApiKey(String apiKey) {
        String cacheKey = "apikey:" + apiKey;
        return clients.get(cacheKey, key -> {
            try {
                return ThingsboardClient.builder()
                        .url(thingsboardUrl)
                        .apiKey(apiKey)
                        .build();
            } catch (ApiException e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Cannot connect to ThingsBoard at " + thingsboardUrl + ": " + e.getMessage());
            }
        });
    }

    private ThingsboardClient getClientForJwt(String token) {
        String cacheKey = "jwt:" + hashToken(token);
        return clients.get(cacheKey, key -> {
            try {
                ThingsboardClient client = ThingsboardClient.builder()
                        .url(thingsboardUrl)
                        .build();
                client.setToken(token);
                return client;
            } catch (ApiException e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Cannot connect to ThingsBoard at " + thingsboardUrl + ": " + e.getMessage());
            }
        });
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return ThingsboardClient.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        String authHeader = request != null ? request.getHeader(AUTH_HEADER) : null;

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            if (!token.isEmpty()) {
                return getClientForJwt(token);
            }
        }

        if (authHeader != null && authHeader.startsWith(API_KEY_PREFIX)) {
            String apiKey = authHeader.substring(API_KEY_PREFIX.length()).trim();
            if (!apiKey.isEmpty()) {
                return getClientForApiKey(apiKey);
            }
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Missing or invalid X-Authorization header. " +
                "Use 'Bearer <token>' for JWT or 'ApiKey <key>' for API key authentication.");
    }

}
