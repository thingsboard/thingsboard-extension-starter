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

import jakarta.servlet.http.HttpServletRequest;
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

import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides ThingsboardClient instances keyed by API key.
 * Also resolves ThingsboardClient parameters in controller methods
 * by reading the X-TB-API-Key header from the incoming request.
 */
@Component
public class ThingsboardClientProvider implements HandlerMethodArgumentResolver {

    private static final String API_KEY_HEADER = "X-TB-API-Key";

    private final String thingsboardUrl;
    private final ConcurrentHashMap<String, ThingsboardClient> clients = new ConcurrentHashMap<>();

    public ThingsboardClientProvider(@Value("${thingsboard.url}") String thingsboardUrl) {
        this.thingsboardUrl = thingsboardUrl;
    }

    public ThingsboardClient getClient(String apiKey) {
        return clients.computeIfAbsent(apiKey, key -> {
            try {
                return ThingsboardClient.builder()
                        .url(thingsboardUrl)
                        .apiKey(key)
                        .build();
            } catch (ApiException e) {
                throw new RuntimeException("Failed to create ThingsBoard client", e);
            }
        });
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return ThingsboardClient.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        String apiKey = request != null ? request.getHeader(API_KEY_HEADER) : null;
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Missing " + API_KEY_HEADER + " header");
        }
        return getClient(apiKey);
    }

}
