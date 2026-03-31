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
package org.thingsboard.extension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.thingsboard.client.ThingsboardClient;
import org.thingsboard.client.model.Authority;
import org.thingsboard.client.model.User;
import org.thingsboard.extension.config.ThingsboardClientProvider;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test that boots the full Spring context with Spring Security active
 * and verifies @PreAuthorize annotations enforce authority checks correctly.
 * Also verifies lazy loading: getUser() is NOT called for endpoints without @PreAuthorize.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PreAuthorizeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ThingsboardClientProvider clientProvider;

    private ThingsboardClient mockTbClient(Authority authority) throws Exception {
        ThingsboardClient tb = mock(ThingsboardClient.class);
        User user = mock(User.class, RETURNS_DEEP_STUBS);
        when(user.getAuthority()).thenReturn(authority);
        when(user.getId().getId()).thenReturn(UUID.randomUUID());
        when(user.getTenantId().getId()).thenReturn(UUID.randomUUID());
        when(user.getCustomerId()).thenReturn(null);
        when(tb.getUser()).thenReturn(user);
        return tb;
    }

    private void setupProvider(ThingsboardClient tb) throws Exception {
        when(clientProvider.resolveClient(any())).thenReturn(tb);
        when(clientProvider.supportsParameter(any())).thenReturn(true);
        when(clientProvider.resolveArgument(any(), any(), any(), any())).thenReturn(tb);
    }

    @BeforeEach
    void resetMocks() {
        reset(clientProvider);
    }

    @Test
    void tenantAdminCanAccessBillingEndpoint() throws Exception {
        ThingsboardClient tb = mockTbClient(Authority.TENANT_ADMIN);
        setupProvider(tb);

        String deviceJson = """
                {"id": {"id": "abc-123"}, "name": "Sensor-1"}
                """;

        mockMvc.perform(post("/api/extension/billing/on-device-created")
                        .header("X-Authorization", "ApiKey test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deviceJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.deviceName").value("Sensor-1"));
    }

    @Test
    void customerUserIsDeniedFromBillingEndpoint() throws Exception {
        ThingsboardClient tb = mockTbClient(Authority.CUSTOMER_USER);
        setupProvider(tb);

        mockMvc.perform(post("/api/extension/billing/on-device-created")
                        .header("X-Authorization", "Bearer some-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerUserCanAccessReportEndpoint() throws Exception {
        ThingsboardClient tb = mockTbClient(Authority.CUSTOMER_USER);
        setupProvider(tb);
        when(tb.countEntitiesByQuery(any())).thenReturn(42L);

        mockMvc.perform(post("/api/extension/report/generate")
                        .header("X-Authorization", "Bearer some-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void getUserIsNotCalledForEndpointWithoutPreAuthorize() throws Exception {
        ThingsboardClient tb = mockTbClient(Authority.TENANT_ADMIN);
        when(clientProvider.resolveClient(any())).thenReturn(tb);

        mockMvc.perform(post("/api/extension/transform/telemetry")
                        .header("X-Authorization", "ApiKey test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temperature_f\": 77.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temperature_c").value(25.0));

        // Lazy loading: getUser() must NOT be called when no @PreAuthorize is present
        verify(tb, never()).getUser();
    }

    @Test
    void getUserIsCalledForEndpointWithPreAuthorize() throws Exception {
        ThingsboardClient tb = mockTbClient(Authority.TENANT_ADMIN);
        setupProvider(tb);

        String deviceJson = """
                {"id": {"id": "abc-123"}, "name": "Sensor-1"}
                """;

        mockMvc.perform(post("/api/extension/billing/on-device-created")
                        .header("X-Authorization", "ApiKey test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deviceJson))
                .andExpect(status().isOk());

        // @PreAuthorize triggers authority resolution, which calls getUser() exactly once
        verify(tb, times(1)).getUser();
    }

}
