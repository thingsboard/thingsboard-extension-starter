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
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertNull;

class ThingsboardClientProviderTest {

    private final ThingsboardClientProvider provider =
            new ThingsboardClientProvider("http://localhost:9999", 60, 100);

    @Test
    void returnsNullWhenNoAuthHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertNull(provider.resolveClient(request));
    }

    @Test
    void returnsNullForEmptyBearerToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Authorization", "Bearer   ");
        assertNull(provider.resolveClient(request));
    }

    @Test
    void returnsNullForEmptyApiKey() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Authorization", "ApiKey   ");
        assertNull(provider.resolveClient(request));
    }

    @Test
    void returnsNullForUnknownPrefix() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Authorization", "Basic dXNlcjpwYXNz");
        assertNull(provider.resolveClient(request));
    }

}
