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
package com.wavemaker.app.security.models.config.ad;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.wavemaker.app.security.models.annotation.NonProfilizableProperty;
import com.wavemaker.app.security.models.annotation.ProfilizableProperty;
import com.wavemaker.app.security.models.config.AbstractProviderConfig;
import com.wavemaker.app.security.models.config.rolemapping.RoleMappingConfig;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(title = "Active Directory Security Provider")
public class ActiveDirectoryProviderConfig extends AbstractProviderConfig {

    public static final String DIRECTORY = "AD";

    @ProfilizableProperty(value = "${security.providers.ad.url}", description = "Active Directory LDAP URL (e.g. ldap://hostname:389/)")
    private String url;

    @ProfilizableProperty(value = "${security.providers.ad.domain}", description = "AD domain name (e.g. example.com)")
    private String domain;

    @ProfilizableProperty(value = "${security.providers.ad.rootDn}", description = "Root DN for user searches")
    private String rootDn;

    @ProfilizableProperty(value = "${security.providers.ad.userSearchPattern}", description = "LDAP filter for user lookup; {0} is username")
    private String userSearchPattern;

    @NonProfilizableProperty(value = "${security.providers.ad.testUsername}", description = "Username for LDAP connectivity test")
    private String testUsername;

    @NonProfilizableProperty(value = "${security.providers.ad.testPassword}", description = "Password for LDAP connectivity test")
    private String testPassword;

    @ProfilizableProperty(value = "${security.providers.ad.roleMappingEnabled}", description = "Map AD groups to application roles")
    private boolean roleMappingEnabled;

    @ProfilizableProperty(value = "${security.providers.ad.roleProvider}", description = "Role assignment source",
        supportedValues = {"Active Directory", "Database"})
    private String roleProvider;

    @JsonPropertyDescription("For roleMappingConfig, there can be only two allowed types ActiveDirectoryRoleMappingConfig, DatabaseRoleMappingConfig")
    private RoleMappingConfig roleMappingConfig;

    @Override
    public String getType() {
        return DIRECTORY;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getRootDn() {
        return rootDn;
    }

    public void setRootDn(final String rootDn) {
        this.rootDn = rootDn;
    }

    public String getUserSearchPattern() {
        return userSearchPattern;
    }

    public void setUserSearchPattern(final String userSearchPattern) {
        this.userSearchPattern = userSearchPattern;
    }

    public String getTestUsername() {
        return testUsername;
    }

    public void setTestUsername(final String testUsername) {
        this.testUsername = testUsername;
    }

    public String getTestPassword() {
        return testPassword;
    }

    public void setTestPassword(final String testPassword) {
        this.testPassword = testPassword;
    }

    public String getRoleProvider() {
        return roleProvider;
    }

    public void setRoleProvider(String roleProvider) {
        this.roleProvider = roleProvider;
    }

    public boolean isRoleMappingEnabled() {
        return roleMappingEnabled;
    }

    public void setRoleMappingEnabled(boolean roleMappingEnabled) {
        this.roleMappingEnabled = roleMappingEnabled;
    }

    @Override
    public RoleMappingConfig getRoleMappingConfig() {
        return roleMappingConfig;
    }

    public void setRoleMappingConfig(RoleMappingConfig roleMappingConfig) {
        this.roleMappingConfig = roleMappingConfig;
    }
}
