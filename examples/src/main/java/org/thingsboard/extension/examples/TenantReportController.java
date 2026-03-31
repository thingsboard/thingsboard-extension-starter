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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.client.ThingsboardClient;
import org.thingsboard.client.model.EntityCountQuery;
import org.thingsboard.client.model.EntityType;
import org.thingsboard.client.model.EntityTypeFilter;

import java.util.Map;

/**
 * Example: generate a tenant summary report from a dashboard widget button.
 *
 * This controller is designed to be called from a ThingsBoard widget button action.
 * The widget sends the user's JWT automatically, so the report reflects the
 * authenticated user's tenant data.
 *
 * Setup for on-premise:  see examples/widgets/on-premise-button.js
 * Setup for cloud:       see examples/widgets/cloud-button.js
 *
 * The method signature is identical to the API key pattern (BillingController) --
 * only the header value differs (Bearer vs ApiKey). The argument resolver handles both.
 */
@RestController
@RequestMapping("/api/extension/report")
public class TenantReportController {

    @PreAuthorize("hasAuthority('TENANT_ADMIN') or hasAuthority('CUSTOMER_USER')")
    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody JsonNode params,
                                        ThingsboardClient tb) throws Exception {
        long totalDevices = countEntities(tb, EntityType.DEVICE);
        long totalAssets = countEntities(tb, EntityType.ASSET);
        long totalUsers = countEntities(tb, EntityType.USER);

        return Map.of(
                "status", "ok",
                "totalDevices", totalDevices,
                "totalAssets", totalAssets,
                "totalUsers", totalUsers
        );
    }

    private long countEntities(ThingsboardClient tb, EntityType entityType) throws Exception {
        EntityTypeFilter filter = new EntityTypeFilter();
        filter.setEntityType(entityType);
        EntityCountQuery query = new EntityCountQuery();
        query.setEntityFilter(filter);
        return tb.countEntitiesByQuery(query);
    }

}
