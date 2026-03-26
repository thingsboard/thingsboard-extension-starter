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

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    private String identifyAuth(String headerValue) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (headerValue != null) {
            request.addHeader("X-Authorization", headerValue);
        }
        Method method = RequestLoggingFilter.class.getDeclaredMethod("identifyAuth", jakarta.servlet.http.HttpServletRequest.class);
        method.setAccessible(true);
        return (String) method.invoke(filter, request);
    }

    private String maskCredential(String credential) throws Exception {
        Method method = RequestLoggingFilter.class.getDeclaredMethod("maskCredential", String.class);
        method.setAccessible(true);
        return (String) method.invoke(filter, credential);
    }

    @Test
    void noAuthHeaderReturnsNone() throws Exception {
        assertEquals("auth: none", identifyAuth(null));
    }

    @Test
    void blankAuthHeaderReturnsNone() throws Exception {
        assertEquals("auth: none", identifyAuth("   "));
    }

    @Test
    void bearerTokenIdentifiedAndMasked() throws Exception {
        assertEquals("JWT: abc123***", identifyAuth("Bearer abc123longtoken"));
    }

    @Test
    void apiKeyIdentifiedAndMasked() throws Exception {
        assertEquals("ApiKey: mykey1***", identifyAuth("ApiKey mykey1longvalue"));
    }

    @Test
    void unknownAuthFormatReturnsUnknown() throws Exception {
        assertEquals("auth: unknown", identifyAuth("Basic dXNlcjpwYXNz"));
    }

    @Test
    void shortCredentialFullyMasked() throws Exception {
        assertEquals("***", maskCredential("short"));
    }

    @Test
    void exactlySixCharsFullyMasked() throws Exception {
        assertEquals("***", maskCredential("abcdef"));
    }

    @Test
    void sevenCharsShowsSixPlusMask() throws Exception {
        assertEquals("abcdef***", maskCredential("abcdefg"));
    }

    @Test
    void nullCredentialReturnsNone() throws Exception {
        assertEquals("none", maskCredential(null));
    }

    @Test
    void blankCredentialReturnsNone() throws Exception {
        assertEquals("none", maskCredential("  "));
    }

    @Test
    void filterSkipsNonApiPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/swagger-ui.html");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertEquals(200, response.getStatus());
    }

}
