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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Example: count telemetry keys per message (no ThingsBoard API call needed).
 *
 * Wire this in ThingsBoard:
 *   Rule Chain → REST API Call node
 *   POST http://localhost:8090/api/usage/on-telemetry
 *   Headers: Content-Type: application/json, X-Authorization: ApiKey YOUR_API_KEY
 *
 * Trigger: "Post telemetry" message type in the rule chain.
 * Input: the telemetry JSON from msg.getData(), e.g. {"temperature": 25.5, "humidity": 60}.
 * Output: summary JSON with key count.
 *
 * Note: this controller does NOT declare a ThingsboardClient parameter —
 * it simply processes the incoming JSON without calling ThingsBoard APIs.
 * The X-Authorization header is still required by the REST API Call node config,
 * but the controller ignores it.
 */
@RestController
@RequestMapping("/api/usage")
public class UsageTrackingController {

    @PostMapping("/on-telemetry")
    public Map<String, Object> onTelemetry(@RequestBody JsonNode telemetry) {
        int keyCount = telemetry.size();

        return Map.of(
                "status", "ok",
                "keysReceived", keyCount,
                "keys", iterableToList(telemetry.fieldNames())
        );
    }

    private static List<String> iterableToList(Iterator<String> iterator) {
        List<String> list = new ArrayList<>();
        iterator.forEachRemaining(list::add);
        return list;
    }

}
