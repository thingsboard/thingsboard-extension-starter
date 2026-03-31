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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TelemetryUnitConversionControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TelemetryUnitConversionController())
            .build();

    @Test
    void convertsFahrenheitToCelsius() throws Exception {
        mockMvc.perform(post("/api/extension/transform/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temperature_f\": 77.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temperature_c").value(25.0));
    }

    @Test
    void convertsPsiToBar() throws Exception {
        mockMvc.perform(post("/api/extension/transform/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pressure_psi\": 14.7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pressure_bar").value(1.01));
    }

    @Test
    void unknownKeysPassThrough() throws Exception {
        mockMvc.perform(post("/api/extension/transform/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voltage\": 3.3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voltage").value(3.3));
    }

    @Test
    void nonNumericValuesPassThrough() throws Exception {
        mockMvc.perform(post("/api/extension/transform/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temperature_f\": \"invalid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temperature_f").value("invalid"));
    }

    @Test
    void multipleConversionsInOneRequest() throws Exception {
        mockMvc.perform(post("/api/extension/transform/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temperature_f\": 32.0, \"pressure_psi\": 14.7, \"voltage\": 3.3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temperature_c").value(0.0))
                .andExpect(jsonPath("$.pressure_bar").value(1.01))
                .andExpect(jsonPath("$.voltage").value(3.3));
    }

}
