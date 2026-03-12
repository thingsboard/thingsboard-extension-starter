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

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.client.ThingsboardClient;

import java.time.Instant;
import java.util.Map;

/**
 * Example: track billing when a new device is created.
 *
 * Wire this in ThingsBoard:
 *   Rule Chain → REST API Call node
 *   POST http://localhost:8090/api/billing/on-device-created
 *   Headers: Content-Type: application/json, X-Authorization: ApiKey YOUR_API_KEY
 *
 * Trigger: "Device Created" message type in the rule chain.
 * Input: the device JSON from msg.getData().
 * Output: confirmation JSON with the billing timestamp.
 */
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    @PostMapping("/on-device-created")
    public Map<String, Object> onDeviceCreated(@RequestBody JsonNode device,
                                               ThingsboardClient tb) throws Exception {
        String deviceId = device.get("id").get("id").asText();
        String deviceName = device.get("name").asText();

        // Save a server-side attribute marking billing activation
        String billingJson = """
                {"billingActive": true, "billingStartedAt": "%s"}
                """.formatted(Instant.now().toString());

        tb.saveDeviceAttributes(deviceId, "SERVER_SCOPE", billingJson);

        return Map.of(
                "status", "ok",
                "deviceId", deviceId,
                "deviceName", deviceName,
                "billingStartedAt", Instant.now().toString()
        );
    }

}
