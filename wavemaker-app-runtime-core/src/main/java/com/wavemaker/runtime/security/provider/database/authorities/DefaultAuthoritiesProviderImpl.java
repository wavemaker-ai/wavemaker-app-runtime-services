/*******************************************************************************
 * Copyright (C) 2022-2023 WaveMaker, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.wavemaker.runtime.security.provider.database.authorities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.wavemaker.runtime.security.core.AuthenticationContext;
import com.wavemaker.runtime.security.core.AuthoritiesProvider;
import com.wavemaker.runtime.security.provider.database.AbstractDatabaseSupport;

/**
 * Created by ArjunSahasranam on 11/3/16.
 */
public class DefaultAuthoritiesProviderImpl extends AbstractDatabaseSupport implements AuthoritiesProvider {

    private static final String USERNAME = "username";
    private static final String COLON_USERNAME = ":username";
    private static final String Q_MARK = "?";

    private String authoritiesByUsernameQuery = "SELECT userid, role FROM User WHERE username = ?";
    private String rolePrefix = "ROLE_";
    private static final String LOGGED_IN_USERNAME = ":LOGGED_IN_USERNAME";

    @Override
    public List<GrantedAuthority> loadAuthorities(AuthenticationContext authenticationContext) {
        Map<String, Object> params = new HashMap<>();
        params.put(USERNAME, authenticationContext.getUsername());
        List<Map<String, Object>> rows = executeQuery(getAuthoritiesByUsernameQuery(), params);
        return getAuthorities(rows);
    }

    public String getAuthoritiesByUsernameQuery() {
        return authoritiesByUsernameQuery;
    }

    public void setAuthoritiesByUsernameQuery(final String authoritiesByUsernameQuery) {
        this.authoritiesByUsernameQuery = authoritiesByUsernameQuery;
        updateAuthoritiesByUsernameQuery();
    }

    public String getRolePrefix() {
        return rolePrefix;
    }

    public void setRolePrefix(final String rolePrefix) {
        this.rolePrefix = rolePrefix;
    }

    private void updateAuthoritiesByUsernameQuery() {
        if (authoritiesByUsernameQuery.contains(LOGGED_IN_USERNAME)) {
            authoritiesByUsernameQuery = authoritiesByUsernameQuery.replace(LOGGED_IN_USERNAME, COLON_USERNAME);
        }
        if (authoritiesByUsernameQuery.contains(Q_MARK)) {
            authoritiesByUsernameQuery = authoritiesByUsernameQuery.replace(Q_MARK, COLON_USERNAME);
        }
    }

    private List<GrantedAuthority> getAuthorities(final List<Map<String, Object>> content) {
        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        if (!content.isEmpty()) {
            for (Map<String, Object> row : content) {
                Object role = row.size() == 1 ? row.get("0") : row.get("1");
                SimpleGrantedAuthority simpleGrantedAuthority = new SimpleGrantedAuthority(getRolePrefix() + role);
                grantedAuthorities.add(simpleGrantedAuthority);
            }
            return grantedAuthorities;
        }
        return grantedAuthorities;
    }

}
