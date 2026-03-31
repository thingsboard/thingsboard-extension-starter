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

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security Authentication backed by a lazily-loaded ThingsBoard user.
 * Authorities are resolved on first call to {@link #getAuthorities()}.
 */
public class TbAuthentication extends AbstractAuthenticationToken {

    private final TbSecurityUser principal;
    private volatile Collection<GrantedAuthority> resolvedAuthorities;

    public TbAuthentication(TbSecurityUser principal) {
        super(null);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        Collection<GrantedAuthority> result = resolvedAuthorities;
        if (result == null) {
            try {
                result = List.of(new SimpleGrantedAuthority(principal.getAuthority()));
            } catch (Exception e) {
                throw new RuntimeException("Failed to resolve user authorities", e);
            }
            resolvedAuthorities = result;
        }
        return result;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public TbSecurityUser getPrincipal() {
        return principal;
    }

}
