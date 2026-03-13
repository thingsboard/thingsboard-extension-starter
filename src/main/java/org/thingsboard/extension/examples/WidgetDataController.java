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
import org.thingsboard.client.model.PageDataDevice;

import java.util.Map;

/**
 * Example: serve data to a ThingsBoard widget using JWT authentication.
 *
 * Wire this in ThingsBoard:
 *   Widget → HTTP Datasource or custom JS fetch
 *   POST http://localhost:8090/api/widget/current-stats
 *   Headers: Content-Type: application/json, X-Authorization: Bearer ${tbAuthToken}
 *
 * The widget provides the user's session JWT automatically via ${tbAuthToken}.
 * The ThingsboardClient is authenticated as that user -- API calls respect the
 * user's tenant and permissions.
 *
 * Note: the method signature is identical to the API key pattern (BillingController) --
 * only the header value differs (Bearer vs ApiKey). The argument resolver handles both.
 *
 * Trigger: any widget action or data load event.
 * Input: optional params JSON from the widget.
 * Output: stats JSON displayed in the widget.
 */
@RestController
@RequestMapping("/api/widget")
public class WidgetDataController {

    @PostMapping("/current-stats")
    public Map<String, Object> currentStats(@RequestBody JsonNode params,
                                            ThingsboardClient tb) throws Exception {
        PageDataDevice devices = tb.getTenantDevices(1, 0, null, null, null, null);

        return Map.of(
                "status", "ok",
                "totalDevices", devices.getTotalElements()
        );
    }

}
