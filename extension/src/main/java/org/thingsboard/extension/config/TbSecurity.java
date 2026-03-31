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

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Convenience methods for accessing the current ThingsBoard security user.
 *
 * <p>Usage in controller methods:</p>
 * <pre>
 * TbSecurityUser user = TbSecurity.getCurrentUser();
 * UUID tenantId = user.getTenantId();
 * Authority authority = user.getAuthority();
 * </pre>
 *
 * <p>Only works within an authenticated request (i.e., when the
 * {@code X-Authorization} header is present and valid). Throws
 * {@link IllegalStateException} if called outside an authenticated context.</p>
 */
public final class TbSecurity {

    private TbSecurity() {
    }

    /**
     * Returns the current request's {@link TbSecurityUser}, which provides
     * lazy access to the ThingsBoard user, authority, tenant ID, etc.
     *
     * @throws IllegalStateException if no authenticated user is present
     */
    public static TbSecurityUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof TbAuthentication tbAuth) {
            return tbAuth.getPrincipal();
        }
        throw new IllegalStateException("No authenticated ThingsBoard user in security context");
    }

}
