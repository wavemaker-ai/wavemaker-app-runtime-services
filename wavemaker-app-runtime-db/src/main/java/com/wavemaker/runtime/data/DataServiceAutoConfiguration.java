/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc.
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
package com.wavemaker.runtime.data;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.wavemaker.runtime.data.aop.CRUDAspectManager;
import com.wavemaker.runtime.data.aop.CreateMethodInvocationHandler;
import com.wavemaker.runtime.data.aop.DeleteMethodInvocationHandler;
import com.wavemaker.runtime.data.aop.FindAllMethodInvocationHandler;
import com.wavemaker.runtime.data.aop.FindMethodInvocationHandler;
import com.wavemaker.runtime.data.aop.UpdateMethodInvocationHandler;
import com.wavemaker.runtime.data.controller.QueryTestRunController;
import com.wavemaker.runtime.data.dao.DatabaseQueryExecutorFactory;
import com.wavemaker.runtime.data.dao.DefaultDatabaseQueryExecutorFactory;
import com.wavemaker.runtime.data.exception.resolver.DbExceptionResolver;
import com.wavemaker.runtime.data.model.TemporalHistoryEntity;
import com.wavemaker.runtime.data.props.MySqlCloudTokenResolver;
import com.wavemaker.runtime.data.service.QueryDesignServiceImpl;
import com.wavemaker.runtime.data.util.NegatingTypeFilter;

/**
 * The beans every app with runtime-db needs, whether it has a data service or not, and {@code @Transactional} for the
 * generated services. Listed in the WMAutoConfiguration.imports file. The beans of each data service are registered by
 * {@link DataServiceBeansRegistrar}.
 */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
public class DataServiceAutoConfiguration {

    @Bean
    public MySqlCloudTokenResolver mySqlCloudTokenResolver() {
        return new MySqlCloudTokenResolver();
    }

    /*
     * Every provider's own database-backed role/authority mapping (AD, CAS, JWS, LDAP, Opaque, SAML) and the DATABASE
     * provider need this bean, so it is registered for any app with runtime-db, whichever provider authenticates the user.
     */
    @Bean
    public DatabaseQueryExecutorFactory databaseQueryExecutorFactory() {
        return new DefaultDatabaseQueryExecutorFactory();
    }

    @Bean
    public DbExceptionResolver dbExceptionResolver() {
        return new DbExceptionResolver();
    }

    @Bean
    public AssignableTypeFilter historyEntitiesFilter() {
        return new AssignableTypeFilter(TemporalHistoryEntity.class);
    }

    @Bean
    public NegatingTypeFilter nonHistoryEntitiesFilter() {
        return new NegatingTypeFilter(historyEntitiesFilter());
    }

    @Bean
    public CRUDAspectManager crudAspectManager() {
        return new CRUDAspectManager();
    }

    @Bean
    public CreateMethodInvocationHandler createMethodInvocationHandler() {
        return new CreateMethodInvocationHandler();
    }

    @Bean
    public UpdateMethodInvocationHandler updateMethodInvocationHandler() {
        return new UpdateMethodInvocationHandler();
    }

    @Bean
    public FindAllMethodInvocationHandler findAllMethodInvocationHandler() {
        return new FindAllMethodInvocationHandler();
    }

    @Bean
    public FindMethodInvocationHandler findMethodInvocationHandler() {
        return new FindMethodInvocationHandler();
    }

    @Bean
    public DeleteMethodInvocationHandler deleteMethodInvocationHandler() {
        return new DeleteMethodInvocationHandler();
    }

    @Bean
    @Profile("wm_preview")
    public QueryTestRunController querycontroller() {
        return new QueryTestRunController();
    }

    @Bean
    @Profile("wm_preview")
    public QueryDesignServiceImpl queryDesignService() {
        return new QueryDesignServiceImpl();
    }
}
