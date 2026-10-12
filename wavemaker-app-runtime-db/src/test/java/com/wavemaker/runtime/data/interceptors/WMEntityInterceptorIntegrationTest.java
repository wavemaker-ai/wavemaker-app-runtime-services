/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.interceptors;

import java.util.UUID;

import org.hibernate.FlushMode;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.wavemaker.runtime.data.dao.WMHibernateTemplate;
import org.springframework.orm.jpa.hibernate.HibernateTransactionManager;
import org.springframework.orm.jpa.hibernate.SessionHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.wavemaker.runtime.data.dialect.WMHSQLDialect;
import com.wavemaker.runtime.data.support.ServerVariables;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link WMEntityInterceptor} needs its own {@code SessionFactory} (an {@code Interceptor} is registered once,
 * at {@code Configuration}/{@code SessionFactory} build time) - a separate, self-contained harness rather than
 * the shared {@code AbstractHibernateIntegrationTest} one, which every other test in this module relies on
 * having no interceptor.
 */
class WMEntityInterceptorIntegrationTest {

    private static SessionFactory sessionFactory;
    private static WMHibernateTemplate hibernateTemplate;
    private static HibernateTransactionManager transactionManager;

    private static String currentUser;

    private TransactionStatus transactionStatus;

    @BeforeAll
    static void buildSessionFactoryWithInterceptor() {
        String dbName = "testdb" + UUID.randomUUID().toString().replace("-", "");

        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:" + dbName);
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Configuration configuration = new Configuration()
            .setProperty("hibernate.dialect", WMHSQLDialect.class.getName())
            .setProperty("hibernate.hbm2ddl.auto", "create-drop")
            .setProperty("hibernate.current_session_context_class",
                "org.springframework.orm.jpa.hibernate.SpringSessionContext")
            .setInterceptor(new WMEntityInterceptor())
            .addAnnotatedClass(InterceptedEntity.class)
            .addAnnotatedClass(AuditedEntity.class);
        configuration.getProperties().put("hibernate.connection.datasource", dataSource);

        sessionFactory = configuration.buildSessionFactory();
        hibernateTemplate = new WMHibernateTemplate(sessionFactory);
        transactionManager = new HibernateTransactionManager(sessionFactory);
    }

    @AfterAll
    static void closeSessionFactory() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

    @BeforeEach
    void beginTransaction() {
        currentUser = "alice";
        ServerVariables.use(variableName -> "currentUser".equals(variableName) ? currentUser : null);

        transactionStatus = transactionManager.getTransaction(new DefaultTransactionDefinition(
            TransactionDefinition.PROPAGATION_REQUIRED));
        SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(sessionFactory);
        sessionHolder.getSession().setHibernateFlushMode(FlushMode.AUTO);
    }

    @AfterEach
    void rollbackTransaction() {
        if (!transactionStatus.isCompleted()) {
            transactionManager.rollback(transactionStatus);
        }
        ServerVariables.reset();
    }

    @Test
    void onFlushDirtyDoesNotTouchAFieldNotRegisteredForUpdateScope() {
        InterceptedEntity entity = new InterceptedEntity();
        entity.setName("Alice");
        hibernateTemplate.save(entity);
        hibernateTemplate.flush();
        entity.setCreatedBy("set-after-insert");

        entity.setName("Alice Updated");
        hibernateTemplate.update(entity);
        hibernateTemplate.flush();

        // createdBy is INSERT-scoped only - an UPDATE flush must leave whatever the caller set untouched.
        assertEquals("set-after-insert", entity.getCreatedBy());
        assertEquals("Alice Updated", entity.getName());
    }

    @Test
    void onLoadAppliesReadScopedReplacerWhenTheEntityIsReloadedFromTheDatabase() {
        // readMarker is set to a real, persisted value here - onSave has no READ scope, so this insert leaves
        // it untouched, and it genuinely lands in the database as "value-actually-in-db". Reloading (from a
        // fresh session, not the first-level cache) should overwrite it back to null (PROMPT always resolves
        // to null - see VariableDefinedPropertyProviderTest), proving onLoad's replacer actually ran - if it
        // hadn't, the reload would show "value-actually-in-db" instead, exactly what Hibernate's own hydration
        // would otherwise leave in place.
        InterceptedEntity entity = new InterceptedEntity();
        entity.setName("Alice");
        entity.setReadMarker("value-actually-in-db");
        hibernateTemplate.save(entity);
        hibernateTemplate.flush();
        Long id = entity.getId();
        hibernateTemplate.evict(entity);

        InterceptedEntity reloaded = hibernateTemplate.get(InterceptedEntity.class, id);

        assertNull(reloaded.getReadMarker());
        assertEquals("Alice", reloaded.getName());
    }

    @Test
    void onDeleteNeverAppliesAnyDeleteScopedReplacer() {
        // WMEntityInterceptor.onDelete's body is entirely commented out in the current source - a Scope.DELETE
        // -registered field (deleteMarker here) is wired up by EntityValueReplacerBuilder but never actually
        // fires, unlike INSERT/UPDATE/READ. This test exists to catch that gap being silently "fixed" into a
        // real behavior change without anyone noticing it's a change.
        InterceptedEntity entity = new InterceptedEntity();
        entity.setName("Alice");
        entity.setDeleteMarker("set-before-delete");
        hibernateTemplate.save(entity);
        hibernateTemplate.flush();

        hibernateTemplate.delete(entity);
        hibernateTemplate.flush();

        assertEquals("set-before-delete", entity.getDeleteMarker());
    }

    // ---- What the interceptor actually writes ----
    //
    // The replacer sets the entity field and the interceptor then copies it into Hibernate's state[]; only the second
    // step reaches the INSERT/UPDATE. These tests use AuditedEntity, whose SERVER-typed fields resolve to a real user,
    // and read the columns back with SQL. Hibernate 6 changes the interceptor callbacks (onSave becomes onPersist, ids
    // become Object), so these round trips must keep working.

    /** Reads createdBy, modifiedBy and touchedBy straight from the table. */
    private static Object[] storedAuditColumns(Long id) {
        return hibernateTemplate.execute(session -> (Object[]) session.createNativeQuery(
                "select createdBy, modifiedBy, touchedBy from AuditedEntity where id = :id")
            .setParameter("id", id)
            .uniqueResult());
    }

    private static AuditedEntity insertAudited(String name) {
        AuditedEntity entity = new AuditedEntity();
        entity.setName(name);
        hibernateTemplate.save(entity);
        hibernateTemplate.flush();
        return entity;
    }

    @Test
    void anInsertWritesTheInjectedValueToInsertScopedColumnsOnly() {
        AuditedEntity entity = insertAudited("first");

        assertArrayEquals(new Object[]{"alice", null, "alice"}, storedAuditColumns(entity.getId()));
    }

    @Test
    void anInsertOverwritesAValueTheCallerSetOnAnInjectedColumn() {
        AuditedEntity entity = new AuditedEntity();
        entity.setName("first");
        entity.setCreatedBy("spoofed");
        hibernateTemplate.save(entity);
        hibernateTemplate.flush();

        assertEquals("alice", storedAuditColumns(entity.getId())[0]);
    }

    @Test
    void anUpdateWritesTheCurrentValueToUpdateScopedColumnsAndLeavesInsertOnlyColumnsAlone() {
        AuditedEntity entity = insertAudited("first");

        currentUser = "bob";
        entity.setName("renamed");
        hibernateTemplate.flush();

        assertArrayEquals(new Object[]{"alice", "bob", "bob"}, storedAuditColumns(entity.getId()));
    }

    @Test
    void aFlushWithNoChangesDoesNotRunTheUpdateReplacer() {
        AuditedEntity entity = insertAudited("first");

        currentUser = "bob";
        hibernateTemplate.flush();

        // onFlushDirty only runs for dirty entities, so nothing was updated.
        assertArrayEquals(new Object[]{"alice", null, "alice"}, storedAuditColumns(entity.getId()));
    }
}
