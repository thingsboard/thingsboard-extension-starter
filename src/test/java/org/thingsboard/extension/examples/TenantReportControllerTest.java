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
package org.thingsboard.extension.examples;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thingsboard.client.ThingsboardClient;
import org.thingsboard.client.model.EntityCountQuery;
import org.thingsboard.extension.TestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantReportControllerTest {

    private final ThingsboardClient tb = mock(ThingsboardClient.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TenantReportController())
            .setCustomArgumentResolvers(TestUtils.tbArgumentResolver(tb))
            .build();

    @Test
    void returnsEntityCounts() throws Exception {
        when(tb.countEntitiesByQuery(any(EntityCountQuery.class)))
                .thenReturn(10L, 5L, 3L);

        mockMvc.perform(post("/api/extension/report/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.totalDevices").value(10))
                .andExpect(jsonPath("$.totalAssets").value(5))
                .andExpect(jsonPath("$.totalUsers").value(3));
    }

}
