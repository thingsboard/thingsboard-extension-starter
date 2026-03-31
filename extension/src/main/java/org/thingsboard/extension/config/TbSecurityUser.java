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

import org.thingsboard.client.ThingsboardClient;
import org.thingsboard.client.model.User;

/**
 * Lazily-loaded ThingsBoard user for the Spring Security context.
 * Calls {@link ThingsboardClient#getUser()} only on first access.
 * The result is cached for the lifetime of the request.
 */
public class TbSecurityUser {

    private final ThingsboardClient tb;
    private volatile User user;

    public TbSecurityUser(ThingsboardClient tb) {
        this.tb = tb;
    }

    public User getUser() throws Exception {
        User result = user;
        if (result == null) {
            result = tb.getUser();
            user = result;
        }
        return result;
    }

    public String getAuthority() throws Exception {
        return getUser().getAuthority().getValue();
    }

    public String getUserId() throws Exception {
        return getUser().getId().getId().toString();
    }

    public String getTenantId() throws Exception {
        return getUser().getTenantId().getId().toString();
    }

    public String getCustomerId() throws Exception {
        var customerId = getUser().getCustomerId();
        return customerId != null ? customerId.getId().toString() : null;
    }

}
