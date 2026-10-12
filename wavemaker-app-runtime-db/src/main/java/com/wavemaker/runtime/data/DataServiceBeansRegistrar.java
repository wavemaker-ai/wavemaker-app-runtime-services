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

import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.orm.jpa.hibernate.HibernateTransactionManager;
import org.springframework.orm.jpa.hibernate.LocalSessionFactoryBean;
import org.springframework.util.StringUtils;

import com.wavemaker.app.db.constants.DBType;
import com.wavemaker.app.db.util.DBUtils;
import com.wavemaker.runtime.data.dao.DaoBeanDefinitions;
import com.wavemaker.runtime.data.dao.HqlQueryExecutor;
import com.wavemaker.runtime.data.dao.NativeSqlQueryExecutor;
import com.wavemaker.runtime.data.dao.WMHibernateTemplate;
import com.wavemaker.runtime.data.dao.procedure.WMProcedureExecutorImpl;
import com.wavemaker.runtime.data.dao.query.WMQueryExecutorImpl;
import com.wavemaker.runtime.data.dao.query.types.wmql.WMQLDefaultTypeHelper;
import com.wavemaker.runtime.data.dao.query.types.wmql.WMQLSqlServerTypeHelper;
import com.wavemaker.runtime.data.datasource.WMDataSource;
import com.wavemaker.runtime.data.interceptors.WMEntityInterceptor;
import com.wavemaker.runtime.data.model.TemporalHistoryEntity;

/**
 * Registers the beans of every data service of an app, replacing the generated {@code service_<id>.spring.xml}.
 * <p>
 * A data service is discovered from its {@code db.<serviceId>.url} property. The registrar also reads
 * {@code db.<serviceId>.packages}, the root package of the generated service. Connection values are registered as
 * {@code ${db.<serviceId>.<key>}} placeholders, not read here, so the property placeholder configurer keeps resolving,
 * decrypting and token-replacing them as it did for the XML.
 * <p>
 * A bean the project already defines under the same name, for example in project-user-spring.xml, is kept as the
 * user's override. It is listed in the WMAutoConfiguration.imports file, with {@link DataServiceAutoConfiguration}.
 */
public class DataServiceBeansRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

    private static final Logger logger = LoggerFactory.getLogger(DataServiceBeansRegistrar.class);

    private static final Pattern SERVICE_URL_PROPERTY = Pattern.compile("^db\\.([^.]+)\\.url$");

    private static final String PAGEABLE_RESOLVER_CLASS = "com.wavemaker.runtime.resolver.WMPageableHandlerMethodArgumentResolver";

    // Hikari is a runtime-only dependency of this module, so it is referenced by name.
    private static final String HIKARI_CONFIG_CLASS = "com.zaxxer.hikari.HikariConfig";

    private static final String HIKARI_DATA_SOURCE_CLASS = "com.zaxxer.hikari.HikariDataSource";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        for (String serviceId : findDataServiceIds()) {
            String basePackage = environment.getProperty(property(serviceId, "packages"));
            if (!StringUtils.hasText(basePackage)) {
                logger.warn("Property {} is not set, skipping the data service {}", property(serviceId, "packages"), serviceId);
                continue;
            }
            logger.info("Registering the beans of data service {} in package {}", serviceId, basePackage);
            register(registry, serviceId, basePackage);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // no-op - bean definitions are registered in postProcessBeanDefinitionRegistry
    }

    private Set<String> findDataServiceIds() {
        Set<String> serviceIds = new LinkedHashSet<>();
        if (environment instanceof ConfigurableEnvironment configurableEnvironment) {
            for (PropertySource<?> propertySource : configurableEnvironment.getPropertySources()) {
                if (propertySource instanceof EnumerablePropertySource<?> enumerable) {
                    for (String name : enumerable.getPropertyNames()) {
                        Matcher matcher = SERVICE_URL_PROPERTY.matcher(name);
                        if (matcher.matches()) {
                            serviceIds.add(matcher.group(1));
                        }
                    }
                }
            }
        }
        return serviceIds;
    }

    private void register(BeanDefinitionRegistry registry, String serviceId, String basePackage) {
        registerDataSource(registry, serviceId);
        registerHibernateProperties(registry, serviceId);
        registerEntityInterceptor(registry, serviceId);
        registerMainSessionFactory(registry, serviceId, basePackage);
        if (hasTemporalEntities(basePackage)) {
            registerHistorySessionFactory(registry, serviceId, basePackage);
        }
        registerTypeHelper(registry, serviceId);
        registerExecutors(registry, serviceId);
        registerIfAbsent(registry, serviceId + "PageableHandlerMethodResolver", pageableResolver(serviceId));
        DaoBeanDefinitions.register(registry, basePackage + ".dao", serviceId);
        scanComponents(registry, basePackage);
    }

    private void registerDataSource(BeanDefinitionRegistry registry, String serviceId) {
        BeanDefinitionBuilder hikariConfig = BeanDefinitionBuilder.genericBeanDefinition(HIKARI_CONFIG_CLASS)
            .addPropertyValue("jdbcUrl", placeholder(serviceId, "url"))
            .addPropertyValue("driverClassName", placeholder(serviceId, "driverClass"))
            .addPropertyValue("username", placeholder(serviceId, "username"))
            .addPropertyValue("password", placeholder(serviceId, "password"))
            .addPropertyValue("maximumPoolSize", placeholder(serviceId, "maxPoolSize"))
            .addPropertyValue("minimumIdle", placeholder(serviceId, "minPoolSize"))
            .addPropertyValue("connectionTimeout", 30000)
            .addPropertyValue("validationTimeout", 5000)
            .addPropertyValue("idleTimeout", 600000)
            .addPropertyValue("maxLifetime", 1800000)
            .addPropertyValue("initializationFailTimeout", -1)
            .addPropertyValue("leakDetectionThreshold", 120000)
            .addPropertyValue("registerMbeans", false);
        registerIfAbsent(registry, serviceId + "HikariConfig", hikariConfig.getBeanDefinition());

        registerIfAbsent(registry, serviceId + "WMManagedDataSource",
            BeanDefinitionBuilder.genericBeanDefinition(HIKARI_DATA_SOURCE_CLASS)
                .addConstructorArgReference(serviceId + "HikariConfig")
                .setDestroyMethodName("close")
                .setLazyInit(true)
                .getBeanDefinition());

        registerIfAbsent(registry, serviceId + "DataSource",
            BeanDefinitionBuilder.genericBeanDefinition(WMDataSource.class)
                .addPropertyValue("dataSourceType", placeholder(serviceId, "dataSourceType"))
                .addPropertyValue("jndiName", placeholder(serviceId, "jndiName"))
                .addPropertyValue("serviceId", serviceId)
                .getBeanDefinition());
    }

    private void registerHibernateProperties(BeanDefinitionRegistry registry, String serviceId) {
        Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", placeholder(serviceId, "dialect"));
        if (StringUtils.hasText(environment.getProperty(property(serviceId, "schemaName")))) {
            properties.setProperty("hibernate.default_schema", "`" + placeholder(serviceId, "schemaName") + "`");
        }
        properties.setProperty("hibernate.show_sql", "false");
        properties.setProperty("hibernate.globally_quoted_identifiers", "false");
        properties.setProperty("hibernate.hbm2ddl.auto", placeholder(serviceId, "hbm2ddl"));
        properties.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
        properties.setProperty("hibernate.schema_update.unique_constraint_strategy", "skip");

        registerIfAbsent(registry, serviceId + "HibernateProperties",
            BeanDefinitionBuilder.genericBeanDefinition(PropertiesFactoryBean.class)
                .addPropertyValue("properties", properties)
                .getBeanDefinition());
    }

    private void registerEntityInterceptor(BeanDefinitionRegistry registry, String serviceId) {
        registerIfAbsent(registry, serviceId + "EntityInterceptor",
            BeanDefinitionBuilder.genericBeanDefinition(WMEntityInterceptor.class).getBeanDefinition());
    }

    private void registerMainSessionFactory(BeanDefinitionRegistry registry, String serviceId, String basePackage) {
        BeanDefinitionBuilder sessionFactory = sessionFactory(serviceId, basePackage, "nonHistoryEntitiesFilter")
            .addPropertyValue("mappingResources", new String[]{serviceId + "-queries.hbm.xml"});
        registerIfAbsent(registry, serviceId + "SessionFactory", sessionFactory.getBeanDefinition());
        registerTransactionManagerAndTemplate(registry, serviceId, "SessionFactory", "", property(serviceId, "transactionTimeout"));
    }

    private void registerHistorySessionFactory(BeanDefinitionRegistry registry, String serviceId, String basePackage) {
        registerIfAbsent(registry, serviceId + "HistorySessionFactory",
            sessionFactory(serviceId, basePackage, "historyEntitiesFilter").getBeanDefinition());
        registerTransactionManagerAndTemplate(registry, serviceId, "HistorySessionFactory", "History",
            property(serviceId, "transactionTimeout"));
    }

    private BeanDefinitionBuilder sessionFactory(String serviceId, String basePackage, String entityTypeFilter) {
        return BeanDefinitionBuilder.genericBeanDefinition(LocalSessionFactoryBean.class)
            .addPropertyReference("dataSource", serviceId + "DataSource")
            .addPropertyValue("packagesToScan", basePackage)
            .addPropertyReference("entityInterceptor", serviceId + "EntityInterceptor")
            .addPropertyValue("entityTypeFilters", new RuntimeBeanReference(entityTypeFilter))
            .addPropertyReference("hibernateProperties", serviceId + "HibernateProperties");
    }

    private void registerTransactionManagerAndTemplate(BeanDefinitionRegistry registry, String serviceId,
                                                       String sessionFactoryName, String infix, String timeoutProperty) {
        registerIfAbsent(registry, serviceId + infix + "TransactionManager",
            BeanDefinitionBuilder.genericBeanDefinition(HibernateTransactionManager.class)
                .addPropertyReference("sessionFactory", serviceId + sessionFactoryName)
                .addPropertyValue("defaultTimeout", "${" + timeoutProperty + "}")
                .getBeanDefinition());
        registerIfAbsent(registry, serviceId + infix + "Template",
            BeanDefinitionBuilder.genericBeanDefinition(WMHibernateTemplate.class)
                .addPropertyReference("sessionFactory", serviceId + sessionFactoryName)
                .getBeanDefinition());
    }

    private void registerTypeHelper(BeanDefinitionRegistry registry, String serviceId) {
        Class<?> typeHelper = isSqlServer(serviceId) ? WMQLSqlServerTypeHelper.class : WMQLDefaultTypeHelper.class;
        registerIfAbsent(registry, serviceId + "WMQLTypeHelper",
            BeanDefinitionBuilder.genericBeanDefinition(typeHelper).setLazyInit(false).getBeanDefinition());
    }

    private boolean isSqlServer(String serviceId) {
        String dbType = environment.getProperty(property(serviceId, "dbType"));
        if (StringUtils.hasText(dbType)) {
            return DBType.fromValue(dbType) == DBType.SQL_SERVER || "SQL_SERVER".equalsIgnoreCase(dbType);
        }
        String url = environment.getProperty(property(serviceId, "url"));
        return StringUtils.hasText(url) && DBUtils.getDBTypeByUrl(url) == DBType.SQL_SERVER;
    }

    private void registerExecutors(BeanDefinitionRegistry registry, String serviceId) {
        registerIfAbsent(registry, serviceId + "WMQueryExecutor",
            BeanDefinitionBuilder.genericBeanDefinition(WMQueryExecutorImpl.class)
                .addPropertyReference("template", serviceId + "Template")
                .getBeanDefinition());
        registerIfAbsent(registry, serviceId + "WMProcedureExecutor",
            BeanDefinitionBuilder.genericBeanDefinition(WMProcedureExecutorImpl.class)
                .addPropertyReference("template", serviceId + "Template")
                .addPropertyValue("serviceId", serviceId)
                .getBeanDefinition());
        registerIfAbsent(registry, serviceId + "NativeQueryExecutor",
            BeanDefinitionBuilder.genericBeanDefinition(NativeSqlQueryExecutor.class)
                .addConstructorArgReference(serviceId + "Template")
                .addConstructorArgReference(serviceId + "TransactionManager")
                .getBeanDefinition());
        registerIfAbsent(registry, serviceId + "HqlQueryExecutor",
            BeanDefinitionBuilder.genericBeanDefinition(HqlQueryExecutor.class)
                .addConstructorArgReference(serviceId + "Template")
                .addConstructorArgReference(serviceId + "TransactionManager")
                .getBeanDefinition());
    }

    private BeanDefinition pageableResolver(String serviceId) {
        return BeanDefinitionBuilder.genericBeanDefinition(PAGEABLE_RESOLVER_CLASS)
            .addPropertyValue("pageParameterName", "page")
            .addPropertyValue("sizeParameterName", "size")
            .addPropertyValue("oneIndexedParameters", true)
            .addPropertyValue("maxPageSize", placeholder(serviceId, "maxPageSize"))
            .getBeanDefinition();
    }

    private void scanComponents(BeanDefinitionRegistry registry, String basePackage) {
        // A component the project already defines under the same bean name is the user's override, so it is kept.
        ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(registry, true, environment) {
            @Override
            protected boolean checkCandidate(String beanName, BeanDefinition beanDefinition) {
                return !registry.containsBeanDefinition(beanName);
            }
        };
        scanner.scan(basePackage + ".controller", basePackage + ".service");
    }

    private boolean hasTemporalEntities(String basePackage) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false, environment);
        scanner.addIncludeFilter(new AssignableTypeFilter(TemporalHistoryEntity.class));
        return !scanner.findCandidateComponents(basePackage).isEmpty();
    }

    /**
     * A bean the project already defines, for example in project-user-spring.xml, is the user's override of the
     * runtime bean, so it is kept.
     */
    private static void registerIfAbsent(BeanDefinitionRegistry registry, String beanName, BeanDefinition beanDefinition) {
        if (registry.containsBeanDefinition(beanName)) {
            logger.info("Bean {} is already defined, keeping the existing definition", beanName);
            return;
        }
        registry.registerBeanDefinition(beanName, beanDefinition);
    }

    private static String property(String serviceId, String key) {
        return "db." + serviceId + "." + key;
    }

    private static String placeholder(String serviceId, String key) {
        return "${" + property(serviceId, key) + "}";
    }
}
