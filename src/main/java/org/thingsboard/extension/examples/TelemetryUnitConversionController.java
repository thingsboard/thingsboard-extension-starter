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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

/**
 * Example: transform telemetry values using unit conversion rules.
 *
 * Wire this in ThingsBoard:
 *   Rule Chain → REST API Call node
 *   POST http://localhost:8090/api/extension/transform/telemetry
 *   Headers: Content-Type: application/json, X-Authorization: ApiKey YOUR_API_KEY
 *
 * Trigger: "Post telemetry" message type in the rule chain.
 * Input: the telemetry JSON from msg.getData(), e.g. {"temperature_f": 77.0, "pressure_psi": 14.7}.
 * Output: transformed JSON with converted values, e.g. {"temperature_c": 25.0, "pressure_bar": 1.01}.
 *
 * Keys without a matching rule pass through unchanged.
 *
 * Note: this controller does NOT declare a ThingsboardClient parameter —
 * it simply processes the incoming JSON without calling ThingsBoard APIs.
 * The X-Authorization header is still required by the REST API Call node config,
 * but the controller ignores it.
 */
@RestController
@RequestMapping("/api/extension/transform")
public class TelemetryUnitConversionController {

    private static final Map<String, Rule> RULES = Map.of(
            "temperature_f", new Rule("temperature_c", f -> (f - 32) * 5.0 / 9.0),
            "pressure_psi", new Rule("pressure_bar", psi -> psi * 0.0689476)
    );

    @PostMapping("/telemetry")
    public Map<String, Object> transformTelemetry(@RequestBody ObjectNode telemetry) {
        Map<String, Object> result = new LinkedHashMap<>();

        Iterator<Map.Entry<String, JsonNode>> fields = telemetry.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            JsonNode value = field.getValue();

            Rule rule = RULES.get(key);
            if (rule != null && value.isNumber()) {
                double converted = rule.convert().applyAsDouble(value.doubleValue());
                double rounded = BigDecimal.valueOf(converted)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                result.put(rule.outputKey(), rounded);
            } else {
                result.put(key, value);
            }
        }

        return result;
    }

    private record Rule(String outputKey, DoubleUnaryOperator convert) {}

}
