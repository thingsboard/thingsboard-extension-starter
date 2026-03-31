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
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.thingsboard.client.ThingsboardClient;

import java.io.IOException;

/**
 * Populates the Spring Security context with a lazily-loaded ThingsBoard user.
 * For requests with X-Authorization header, creates TbAuthentication with lazy user loading.
 * For requests without — does nothing (endpoints without @PreAuthorize still work).
 */
@Slf4j
@Component
public class TbSecurityFilter extends OncePerRequestFilter {

    private final ThingsboardClientProvider clientProvider;

    public TbSecurityFilter(ThingsboardClientProvider clientProvider) {
        this.clientProvider = clientProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            ThingsboardClient client = clientProvider.resolveClient(request);
            if (client != null) {
                TbSecurityUser securityUser = new TbSecurityUser(client);
                TbAuthentication authentication = new TbAuthentication(securityUser);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            log.debug("Could not resolve ThingsboardClient for security context: {}", e.getMessage());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

}
