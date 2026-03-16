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

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thingsboard.client.ApiException;
import org.thingsboard.client.ThingsboardClient;

/**
 * Creates a long-lived {@link ThingsboardClient} singleton for use in scheduled tasks,
 * background jobs, and other non-HTTP contexts where no incoming request (and therefore
 * no {@code X-Authorization} header) is available.
 *
 * <p>This bean is <strong>optional</strong> — it is only created when authentication
 * credentials are configured in {@code application.yml} (or via environment variables).
 * When no credentials are configured, the application starts normally without it,
 * and only request-based authentication (via {@code X-Authorization} header) is available.</p>
 *
 * <p>Supports two authentication modes:</p>
 * <ul>
 *   <li><strong>API key</strong> — no expiry, simple pass-through. Set
 *       {@code thingsboard.auth.api-key}.</li>
 *   <li><strong>Username/password</strong> — the client logs in at startup and
 *       auto-refreshes JWT tokens; re-logs in when the refresh token expires. Set
 *       {@code thingsboard.auth.username} and
 *       {@code thingsboard.auth.password}.</li>
 * </ul>
 *
 * <p>If both API key and username are configured, <strong>API key takes precedence</strong>.</p>
 *
 * <p><strong>Note:</strong> In credentials mode, {@code builder().credentials().build()}
 * performs a login call immediately. If ThingsBoard is unreachable at startup, the
 * application will fail to start. This is intentional — a misconfigured background
 * client should surface early rather than fail silently on the first scheduled run.</p>
 */
@Slf4j
@Configuration
@ConditionalOnExpression("'${thingsboard.auth.api-key:}' != '' or '${thingsboard.auth.username:}' != ''")
public class ThingsboardAuthConfig {

    @Bean
    public ThingsboardClient thingsboardClient(
            @Value("${thingsboard.url}") String url,
            @Value("${thingsboard.auth.api-key:}") String apiKey,
            @Value("${thingsboard.auth.username:}") String username,
            @Value("${thingsboard.auth.password:}") String password) throws ApiException {

        if (!apiKey.isBlank()) {
            log.info("Creating ThingsboardClient with API key");
            return ThingsboardClient.builder()
                    .url(url)
                    .apiKey(apiKey)
                    .build();
        }

        log.info("Creating ThingsboardClient with credentials ({})", username);
        return ThingsboardClient.builder()
                .url(url)
                .credentials(username, password)
                .build();
    }
}
