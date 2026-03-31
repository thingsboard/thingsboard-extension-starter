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

import org.junit.jupiter.api.Test;
import org.thingsboard.client.ThingsboardClient;
import org.thingsboard.client.model.Authority;
import org.thingsboard.client.model.User;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TbSecurityUserTest {

    @Test
    void lazilyFetchesUserOnFirstAccess() throws Exception {
        ThingsboardClient tb = mock(ThingsboardClient.class);
        User user = mock(User.class, RETURNS_DEEP_STUBS);
        when(user.getAuthority()).thenReturn(Authority.TENANT_ADMIN);
        when(tb.getUser()).thenReturn(user);

        TbSecurityUser securityUser = new TbSecurityUser(tb);

        verify(tb, never()).getUser();

        assertEquals("TENANT_ADMIN", securityUser.getAuthority());
        verify(tb, times(1)).getUser();

        // Second access uses cached result
        securityUser.getAuthority();
        verify(tb, times(1)).getUser();
    }

    @Test
    void returnsUserFields() throws Exception {
        ThingsboardClient tb = mock(ThingsboardClient.class);
        User user = mock(User.class, RETURNS_DEEP_STUBS);
        when(user.getAuthority()).thenReturn(Authority.CUSTOMER_USER);
        when(user.getId().getId()).thenReturn(java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"));
        when(user.getTenantId().getId()).thenReturn(java.util.UUID.fromString("22222222-2222-2222-2222-222222222222"));
        when(user.getCustomerId().getId()).thenReturn(java.util.UUID.fromString("33333333-3333-3333-3333-333333333333"));
        when(tb.getUser()).thenReturn(user);

        TbSecurityUser securityUser = new TbSecurityUser(tb);

        assertEquals("CUSTOMER_USER", securityUser.getAuthority());
        assertEquals("11111111-1111-1111-1111-111111111111", securityUser.getUserId());
        assertEquals("22222222-2222-2222-2222-222222222222", securityUser.getTenantId());
        assertEquals("33333333-3333-3333-3333-333333333333", securityUser.getCustomerId());
    }

    @Test
    void nullCustomerIdReturnsNull() throws Exception {
        ThingsboardClient tb = mock(ThingsboardClient.class);
        User user = mock(User.class, RETURNS_DEEP_STUBS);
        when(user.getAuthority()).thenReturn(Authority.TENANT_ADMIN);
        when(user.getCustomerId()).thenReturn(null);
        when(tb.getUser()).thenReturn(user);

        TbSecurityUser securityUser = new TbSecurityUser(tb);
        assertNull(securityUser.getCustomerId());
    }

}
