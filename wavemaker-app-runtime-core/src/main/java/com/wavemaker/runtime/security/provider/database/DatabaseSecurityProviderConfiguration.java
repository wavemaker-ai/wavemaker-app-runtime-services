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

package com.wavemaker.runtime.security.provider.database;

import java.util.Objects;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.wavemaker.app.security.models.config.database.DatabaseProviderConfig;
import com.wavemaker.app.security.models.config.rolemapping.RoleQueryType;
import com.wavemaker.runtime.data.dao.DatabaseQueryExecutorFactory;
import com.wavemaker.runtime.security.authenticationprovider.WMDelegatingAuthenticationProvider;
import com.wavemaker.runtime.security.core.AuthoritiesProvider;
import com.wavemaker.runtime.security.enabled.configuration.SecurityEnabledCondition;
import com.wavemaker.runtime.security.model.AuthProviderType;
import com.wavemaker.runtime.security.model.ProviderOrder;
import com.wavemaker.runtime.security.provider.database.authorities.DefaultAuthoritiesProviderImpl;
import com.wavemaker.runtime.security.provider.database.users.DefaultUserProviderImpl;
import com.wavemaker.runtime.security.provider.database.users.UserProvider;

@Configuration
@Conditional({SecurityEnabledCondition.class, DatabaseSecurityProviderCondition.class})
public class DatabaseSecurityProviderConfiguration {

    @Bean(name = "databaseAuthenticationProvider")
    public AuthenticationProvider daoAuthenticationProvider(DatabaseQueryExecutorFactory databaseQueryExecutorFactory) {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(
            databaseUserDetailsService(databaseProviderConfig(), databaseQueryExecutorFactory));
        authenticationProvider.setPasswordEncoder(noOpPasswordEncoder());
        return authenticationProvider;
    }

    @Bean(name = "databaseDelegatingAuthenticationProvider")
    @Order(ProviderOrder.DATABASE_ORDER)
    public WMDelegatingAuthenticationProvider databaseDelegatingAuthenticationProvider(AuthenticationProvider databaseAuthenticationProvider) {
        return new WMDelegatingAuthenticationProvider(databaseAuthenticationProvider, AuthProviderType.DATABASE);
    }

    @Bean(name = "passwordEncoder")
    public PasswordEncoder noOpPasswordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }

    @Bean(name = "jdbcDaoImpl")
    public UserDetailsService databaseUserDetailsService(DatabaseProviderConfig databaseProviderConfig, DatabaseQueryExecutorFactory databaseQueryExecutorFactory) {
        DatabaseUserDetailsService databaseUserDetailsService = new DatabaseUserDetailsService();
        databaseUserDetailsService.setAuthoritiesProvider(defaultAuthoritiesProviderImpl(databaseProviderConfig, databaseQueryExecutorFactory));
        databaseUserDetailsService.setUserProvider(defaultUserProviderImpl(databaseProviderConfig, databaseQueryExecutorFactory));
        return databaseUserDetailsService;
    }

    @Bean(name = "defaultUserProvider")
    public UserProvider defaultUserProviderImpl(DatabaseProviderConfig databaseProviderConfig, DatabaseQueryExecutorFactory databaseQueryExecutorFactory) {
        DefaultUserProviderImpl defaultUserProvider = new DefaultUserProviderImpl();
        defaultUserProvider.setHql(true);
        defaultUserProvider.setUsersByUsernameQuery(databaseProviderConfig.getUsersByUsernameQuery());
        defaultUserProvider.setServiceName(databaseProviderConfig.getModelName());
        defaultUserProvider.setExecutorFactory(databaseQueryExecutorFactory);
        return defaultUserProvider;
    }

    @Bean(name = "defaultAuthoritiesProvider")
    public AuthoritiesProvider defaultAuthoritiesProviderImpl(DatabaseProviderConfig databaseProviderConfig, DatabaseQueryExecutorFactory databaseQueryExecutorFactory) {
        DefaultAuthoritiesProviderImpl defaultAuthoritiesProvider = new DefaultAuthoritiesProviderImpl();
        defaultAuthoritiesProvider.setHql(Objects.equals(databaseProviderConfig.getQueryType(), RoleQueryType.HQL.name()));
        defaultAuthoritiesProvider.setRolePrefix("ROLE_");
        defaultAuthoritiesProvider.setAuthoritiesByUsernameQuery(databaseProviderConfig.getRolesByUsernameQuery());
        defaultAuthoritiesProvider.setServiceName(databaseProviderConfig.getModelName());
        defaultAuthoritiesProvider.setExecutorFactory(databaseQueryExecutorFactory);
        return defaultAuthoritiesProvider;
    }

    @Bean(name = "databaseProviderConfig")
    public DatabaseProviderConfig databaseProviderConfig() {
        return new DatabaseProviderConfig();
    }
}
