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
import org.springframework.security.core.GrantedAuthority;
import org.thingsboard.client.model.Authority;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TbAuthenticationTest {

    @Test
    void getAuthoritiesReturnsSingleAuthorityMatchingPrincipal() throws Exception {
        TbSecurityUser principal = mock(TbSecurityUser.class);
        when(principal.getAuthority()).thenReturn(Authority.TENANT_ADMIN);

        TbAuthentication auth = new TbAuthentication(principal);

        Collection<GrantedAuthority> authorities = auth.getAuthorities();
        assertEquals(1, authorities.size());
        assertEquals("TENANT_ADMIN", authorities.iterator().next().getAuthority());
    }

    @Test
    void getAuthoritiesCachesResult() throws Exception {
        TbSecurityUser principal = mock(TbSecurityUser.class);
        when(principal.getAuthority()).thenReturn(Authority.CUSTOMER_USER);

        TbAuthentication auth = new TbAuthentication(principal);

        auth.getAuthorities();
        auth.getAuthorities();

        verify(principal, times(1)).getAuthority();
    }

    @Test
    void getAuthoritiesWrapsCheckedExceptionAsRuntimeException() throws Exception {
        TbSecurityUser principal = mock(TbSecurityUser.class);
        when(principal.getAuthority()).thenThrow(new Exception("TB unreachable"));

        TbAuthentication auth = new TbAuthentication(principal);

        RuntimeException ex = assertThrows(RuntimeException.class, auth::getAuthorities);
        assertEquals("Failed to resolve user authorities", ex.getMessage());
        assertInstanceOf(Exception.class, ex.getCause());
        assertEquals("TB unreachable", ex.getCause().getMessage());
    }

    @Test
    void getPrincipalReturnsSecurityUser() {
        TbSecurityUser principal = mock(TbSecurityUser.class);
        TbAuthentication auth = new TbAuthentication(principal);

        assertSame(principal, auth.getPrincipal());
    }

    @Test
    void getCredentialsReturnsNull() {
        TbSecurityUser principal = mock(TbSecurityUser.class);
        TbAuthentication auth = new TbAuthentication(principal);

        assertNull(auth.getCredentials());
    }

    @Test
    void isAuthenticatedReturnsTrue() {
        TbSecurityUser principal = mock(TbSecurityUser.class);
        TbAuthentication auth = new TbAuthentication(principal);

        assertTrue(auth.isAuthenticated());
    }

}
