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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.thingsboard.client.ThingsboardClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TbSecurityFilterTest {

    private final ThingsboardClientProvider clientProvider = mock(ThingsboardClientProvider.class);
    private final TbSecurityFilter filter = new TbSecurityFilter(clientProvider);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void setsAuthenticationWhenClientResolved() throws Exception {
        ThingsboardClient tb = mock(ThingsboardClient.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(clientProvider.resolveClient(request)).thenReturn(tb);

        // Capture the authentication during chain execution
        var authHolder = new Object() { Authentication captured; };
        FilterChain chain = (req, res) -> {
            authHolder.captured = SecurityContextHolder.getContext().getAuthentication();
        };

        filter.doFilterInternal(request, response, chain);

        // Auth was set during chain execution
        assertNotNull(authHolder.captured);
        assertInstanceOf(TbAuthentication.class, authHolder.captured);

        // Auth is cleared after chain completes
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void skipsAuthenticationWhenNoClient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(clientProvider.resolveClient(request)).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
    }

    @Test
    void swallowsExceptionFromClientProvider() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(clientProvider.resolveClient(request)).thenThrow(new RuntimeException("TB unreachable"));

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
    }

}
