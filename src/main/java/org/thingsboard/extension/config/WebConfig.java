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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ThingsboardClientProvider clientProvider;

    @Value("${cors.allowed-origins:}")
    private String corsAllowedOrigins;

    public WebConfig(ThingsboardClientProvider clientProvider) {
        this.clientProvider = clientProvider;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(clientProvider);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (corsAllowedOrigins.isBlank()) {
            return; // No CORS config — same-origin requests work without CORS headers
        }
        registry.addMapping("/**")
                .allowedOriginPatterns(Arrays.stream(corsAllowedOrigins.split(","))
                        .map(String::trim)
                        .toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("X-Authorization", "Content-Type", "Authorization",
                                "Cache-Control", "Accept")
                .allowCredentials(false)
                .maxAge(3600);
    }

}
