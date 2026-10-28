/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.Session;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import com.wavemaker.runtime.data.dialect.WMHSQLDialect;
import com.wavemaker.runtime.data.dialect.WMPostgresSQLDialect;

/**
 * Bootstraps a real Hibernate {@link SessionFactory} so subclasses can exercise DAO/query/criteria/transform code
 * against a live Hibernate session rather than mocks. There is no Spring {@code ApplicationContext} in this
 * harness - production code (e.g. {@code WMGenericDaoImpl}) is driven under a Spring-managed transaction, so tests
 * use {@link JpaTransactionManager} directly to bind a session per test the same way a real
 * {@code @Transactional} boundary would, and roll it back afterwards for isolation.
 *
 * <p>Targets in-memory HSQLDB (via {@link WMHSQLDialect}) by default - fast, no setup, safe for every test class
 * to get its own throwaway catalog. Pass {@code -Dwm.test.db=postgres} to instead target a real, disposable
 * Postgres container started on demand by Testcontainers, using {@link WMPostgresSQLDialect}: the same DAO/entity
 * code, now proving it also works against a real, dialect-accurate database rather than only HSQLDB's. The only
 * prerequisite is a running Docker daemon - Testcontainers starts the container itself (first class to run in
 * postgres mode pays the startup cost; every class after that reuses the same container - see
 * {@link #postgresContainer}), loading {@code src/test/resources/postgres-init/*.sql} exactly as a real deployment
 * would. Every test class still gets full isolation from every other, and from that seed data, by creating its own
 * uniquely-named Postgres SCHEMA (namespace) to build its tables in and dropping that schema afterward - the Postgres
 * equivalent of HSQLDB's per-class catalog.
 */
public abstract class AbstractHibernateIntegrationTest {

    private static final boolean USE_POSTGRES = "postgres".equalsIgnoreCase(System.getProperty("wm.test.db", "hsqldb"));

    /**
     * For the handful of tests that create their own stored procedures directly via raw JDBC in
     * {@code @BeforeAll} (HSQLDB and Postgres use genuinely different DDL syntax for this - see
     * {@code NativeProcedureExecutorIntegrationTest}) - lets them branch which SQL text to execute.
     */
    protected static boolean isPostgres() {
        return USE_POSTGRES;
    }

    protected static SessionFactoryImplementor sessionFactory;
    protected static EntityManagerFactory entityManagerFactory;
    protected static TestSessionTemplate hibernateTemplate;
    protected static JpaTransactionManager transactionManager;

    // Deliberately NOT a JUnit-managed @Container (which would restart it per test class) - one container is
    // started lazily on first use and reused for every test class in the whole run, since starting a container
    // takes several seconds and ~15 test classes each paying that cost would make the suite very slow.
    // Testcontainers' own Ryuk reaper container kills this automatically when the JVM exits, so there is no
    // explicit stop() call anywhere in this class.
    private static PostgreSQLContainer postgresContainer;
    private static DataSource postgresDataSource;
    private static String postgresSchemaName;

    protected TransactionStatus transactionStatus;

    @BeforeAll
    static void buildSessionFactory() {
        LocalContainerEntityManagerFactoryBean factoryBean = USE_POSTGRES ? postgresConfiguration() : hsqldbConfiguration();
        factoryBean.setPackagesToScan("com.wavemaker.runtime.data.support.entity");
        factoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factoryBean.afterPropertiesSet();

        entityManagerFactory = factoryBean.getObject();
        sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        hibernateTemplate = new TestSessionTemplate(entityManagerFactory);
        transactionManager = new JpaTransactionManager(entityManagerFactory);
    }

    /**
     * The session of the running transaction, which is what {@code SessionFactory.getCurrentSession()} was in the
     * Hibernate 5 harness.
     */
    protected static Session currentSession() {
        return EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory).unwrap(Session.class);
    }

    private static LocalContainerEntityManagerFactoryBean hsqldbConfiguration() {
        String dbName = "testdb" + UUID.randomUUID().toString().replace("-", "");

        // A DriverManager-based connection provider cannot be unwrapped to javax.sql.DataSource, which
        // the transaction manager needs - use HSQLDB's own DataSource.
        // No ";shutdown=true" - that destroys the in-memory catalog when the LAST open connection closes,
        // which happens as soon as hbm2ddl's own schema-export connection closes during buildSessionFactory(),
        // wiping the schema (including the id-generator sequence) before any test runs. Each test class already
        // gets its own uniquely-named catalog, so there is nothing to explicitly shut down.
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:" + dbName);
        dataSource.setUser("sa");
        dataSource.setPassword("");

        LocalContainerEntityManagerFactoryBean factoryBean = new LocalContainerEntityManagerFactoryBean();
        factoryBean.setDataSource(dataSource);
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("hibernate.dialect", WMHSQLDialect.class.getName());
        properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        properties.setProperty("hibernate.query.native.prefer_jdbc_datetime_types", "true");
        factoryBean.setJpaProperties(properties);
        return factoryBean;
    }

    private static LocalContainerEntityManagerFactoryBean postgresConfiguration() {
        if (postgresContainer == null) {
            // withCopyFileToContainer copies postgres-init/*.sql (src/test/resources) into Postgres's own
            // /docker-entrypoint-initdb.d/ convention. Loaded from the test classpath rather than a host path so it
            // resolves regardless of the JVM's working directory (Gradle, IDE runners, etc.).
            postgresContainer = new PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("runtimedb_test")
                .withUsername("runtimedb")
                .withPassword("runtimedb")
                .withCopyFileToContainer(MountableFile.forClasspathResource("postgres-init"),
                    "/docker-entrypoint-initdb.d");
            postgresContainer.start();
        }

        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgresContainer.getJdbcUrl());
        dataSource.setUser(postgresContainer.getUsername());
        dataSource.setPassword(postgresContainer.getPassword());
        postgresDataSource = dataSource;

        // A fresh, uniquely-named schema per test class - the Postgres equivalent of HSQLDB's per-class catalog.
        // Keeps every test class's tables completely isolated from each other AND from the public schema's
        // baked-in seed data (which exists for manual/psql exploration and procedure testing, not for these
        // tests to read). Still needed even with a disposable container, because the container itself is shared
        // across every test class in this run (see postgresContainer's own comment above).
        postgresSchemaName = "test_" + UUID.randomUUID().toString().replace("-", "");

        // hibernate.default_schema (below) only schema-qualifies HIBERNATE'S OWN generated SQL - it does nothing
        // for hand-written native SQL (e.g. WMQueryExecutorImplNativeSqlIntegrationTest's raw "select ... from
        // Employee" queries), which resolve unqualified table names via the JDBC connection's own search_path.
        // Without this, that search_path defaults to "public" - silently reading the seed data baked into that
        // schema instead of this class's own empty one. Confirmed directly: before this fix, native-SQL tests
        // were getting exactly 7 rows back (the public schema's seeded employee count) instead of the 0-2 rows
        // each test actually inserted itself. Setting currentSchema here is what makes every connection from
        // this datasource - Hibernate's own and raw native SQL alike - resolve against this class's schema first.
        dataSource.setCurrentSchema(postgresSchemaName);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA \"" + postgresSchemaName + "\"");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create schema " + postgresSchemaName, e);
        }

        LocalContainerEntityManagerFactoryBean factoryBean = new LocalContainerEntityManagerFactoryBean();
        factoryBean.setDataSource(dataSource);
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("hibernate.dialect", WMPostgresSQLDialect.class.getName());
        properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        properties.setProperty("hibernate.default_schema", postgresSchemaName);
        properties.setProperty("hibernate.query.native.prefer_jdbc_datetime_types", "true");
        factoryBean.setJpaProperties(properties);
        return factoryBean;
    }

    @AfterAll
    static void closeSessionFactory() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
        if (USE_POSTGRES && postgresSchemaName != null) {
            // hbm2ddl's "drop" half of create-drop already dropped every table inside the schema above - this
            // removes the now-empty schema/namespace itself, so a long test run doesn't accumulate one abandoned
            // schema per test class for as long as the shared container stays up. The container itself is
            // intentionally left running (see postgresContainer's own comment above) - not stopped here.
            try (Connection connection = postgresDataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS \"" + postgresSchemaName + "\" CASCADE");
            } catch (SQLException e) {
                throw new IllegalStateException("Could not drop test schema " + postgresSchemaName, e);
            }
        }
    }

    @BeforeEach
    void beginTransaction() {
        transactionStatus = transactionManager.getTransaction(new DefaultTransactionDefinition(
            TransactionDefinition.PROPAGATION_REQUIRED));
    }

    /**
     * Writes pending changes and empties the session, so the next read hits the database instead of returning the
     * same Java object from Hibernate's first-level cache. Call it before any read-back that is meant to check what
     * was actually persisted.
     */
    protected static void flushAndClear() {
        hibernateTemplate.flush();
        hibernateTemplate.clear();
    }

    @AfterEach
    void rollbackTransaction() {
        if (!transactionStatus.isCompleted()) {
            transactionManager.rollback(transactionStatus);
        }
    }
}
