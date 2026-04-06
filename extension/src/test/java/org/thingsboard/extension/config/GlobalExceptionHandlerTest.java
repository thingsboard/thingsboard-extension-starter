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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.thingsboard.client.ApiException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void apiExceptionMapsThingsboardStatusCode() {
        ApiException ex = new ApiException(404, "Device not found");

        ResponseEntity<Map<String, Object>> response = handler.handleApiException(ex);

        assertEquals(404, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("Not Found", body.get("error"));
        assertEquals(404, body.get("status"));
        assertEquals("ThingsBoard API error: Device not found", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    void apiExceptionWithInvalidCodeReturnsBadGateway() {
        ApiException ex = new ApiException(0, "Connection refused");

        ResponseEntity<Map<String, Object>> response = handler.handleApiException(ex);

        assertEquals(502, response.getStatusCode().value());
        assertEquals("Bad Gateway", response.getBody().get("error"));
    }

    @Test
    void genericExceptionReturns500WithSanitizedMessage() {
        Exception ex = new RuntimeException("java.lang.NullPointerException at com.internal.Secret.method");

        ResponseEntity<Map<String, Object>> response = handler.handleGeneric(ex);

        assertEquals(500, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("Internal Server Error", body.get("error"));
        assertEquals(500, body.get("status"));
        assertEquals("Internal server error", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    void accessDeniedReturns403() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");

        ResponseEntity<Map<String, Object>> response = handler.handleAccessDenied(ex);

        assertEquals(403, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("Forbidden", body.get("error"));
        assertEquals(403, body.get("status"));
        assertEquals("Access denied", body.get("message"));
    }

    @Test
    void authenticationExceptionReturns401() {
        AuthenticationCredentialsNotFoundException ex =
                new AuthenticationCredentialsNotFoundException("Authentication required");

        ResponseEntity<Map<String, Object>> response = handler.handleAuthenticationException(ex);

        assertEquals(401, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("Unauthorized", body.get("error"));
        assertEquals(401, body.get("status"));
        assertEquals("Authentication required", body.get("message"));
    }

    @Test
    void errorBodyContainsAllFourFields() {
        ApiException ex = new ApiException(400, "Bad request");

        ResponseEntity<Map<String, Object>> response = handler.handleApiException(ex);

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(4, body.size());
        assertTrue(body.containsKey("error"));
        assertTrue(body.containsKey("status"));
        assertTrue(body.containsKey("message"));
        assertTrue(body.containsKey("timestamp"));
    }

}
