/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import com.wavemaker.commons.WMRuntimeException;
import com.wavemaker.runtime.commons.WMAppContext;
import com.wavemaker.runtime.data.dao.query.WMQueryExecutorImpl;
import com.wavemaker.runtime.data.model.DesignServiceResponse;
import com.wavemaker.runtime.data.model.JavaType;
import com.wavemaker.runtime.data.model.ReferenceType;
import com.wavemaker.runtime.data.model.queries.QueryParameter;
import com.wavemaker.runtime.data.model.queries.QueryType;
import com.wavemaker.runtime.data.model.queries.RuntimeQuery;
import com.wavemaker.runtime.data.model.returns.ReturnProperty;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.TestEmployeeDao;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the "design-query-test-response" and "design-query-syntax-error" scenarios from the runtime-db use-case
 * spec, against the real {@code QueryDesignServiceImpl.testRunQuery(serviceId, RuntimeQuery, Pageable)} - the
 * spec calls this method {@code testQuery(customQuery, testParams)}, but that overload does not exist; the real
 * signature is used here.
 *
 * <p>{@code QueryDesignServiceImpl}/{@code AbstractDesignService} look up their collaborators (a
 * {@code PlatformTransactionManager}, a {@code WMQueryExecutor}, a {@code SessionFactoryImplementor}) as named
 * Spring beans via the {@code WMAppContext} static singleton, since in production it always runs inside a real
 * generated app's Spring container. There is no such container anywhere in this module's test harness, so this
 * test constructs the {@code WMAppContext} singleton via reflection (its constructor is private and only ever
 * invoked by Spring in production) and feeds it a {@link StaticApplicationContext} carrying real (not mocked)
 * collaborators wired to this harness's own {@code SessionFactory}/{@code WMSessionTemplate} - the same pattern
 * used in {@code datasource/WMDataSourceTest} for a similar Spring-only class.
 *
 * <p><b>Real behavior turned out to differ from the spec's "design-query-syntax-error" scenario:</b> the spec
 * assumes {@code QueryDesignServiceImpl} catches a query parse exception and returns a structured error
 * response instead of propagating it. Reading the real method shows no such try/catch anywhere in
 * {@code testRunQuery}/{@code _runQuery}/{@code AbstractDesignService.executeInTransaction} - a malformed HQL
 * string simply propagates as whatever exception Hibernate's parser throws. The test below asserts that real,
 * unhandled-propagation behavior instead of the spec's assumed graceful handling.
 */
class QueryDesignServiceImplIntegrationTest extends AbstractHibernateIntegrationTest {

    private static final String SERVICE_ID = "test";

    @BeforeAll
    static void wireWmAppContextWithRealCollaborators() throws Exception {
        Constructor<WMAppContext> constructor = WMAppContext.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        if (WMAppContext.getInstance() == null) {
            constructor.newInstance();
        }

        WMQueryExecutorImpl queryExecutor = new WMQueryExecutorImpl();
        queryExecutor.setTemplate(hibernateTemplate);
        Method init = WMQueryExecutorImpl.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(queryExecutor);

        StaticApplicationContext context = new StaticApplicationContext();
        context.getDefaultListableBeanFactory().registerSingleton(SERVICE_ID + "TransactionManager", transactionManager);
        context.getDefaultListableBeanFactory().registerSingleton(SERVICE_ID + "WMQueryExecutor", queryExecutor);
        context.getDefaultListableBeanFactory().registerSingleton(SERVICE_ID + "SessionFactory", sessionFactory);

        WMAppContext.getInstance().setApplicationContext(context);
    }

    private static RuntimeQuery hqlRuntimeQuery(String queryString) {
        RuntimeQuery query = new RuntimeQuery();
        query.setQueryString(queryString);
        query.setNativeSql(false);
        query.setType(QueryType.SELECT);
        return query;
    }

    @Test
    void testRunQueryExecutesAgainstTheLiveDatasourceAndReturnsRowsWithSchemaInfo() {
        TestEmployeeDao dao = new TestEmployeeDao(hibernateTemplate);
        dao.create(employee("Alice"));
        dao.create(employee("Bob"));
        // AbstractDesignService.executeInTransaction runs with PROPAGATION_REQUIRES_NEW, suspending this test's
        // own per-method transaction and opening a separate one on a separate connection - so the rows above
        // must actually be committed first, or the service's own transaction (on its own connection) would not
        // see them under normal transaction isolation.
        transactionManager.commit(transactionStatus);

        QueryDesignServiceImpl service = new QueryDesignServiceImpl();
        RuntimeQuery query = hqlRuntimeQuery("from Employee e order by e.name");

        DesignServiceResponse response = service.testRunQuery(SERVICE_ID, query, PageRequest.of(0, 10));

        assertTrue(response.getResults() instanceof org.springframework.data.domain.Page);
        org.springframework.data.domain.Page<?> page = (org.springframework.data.domain.Page<?>) response.getResults();
        assertEquals(2, page.getTotalElements());
        assertFalse(response.getReturnProperties().isEmpty());
    }

    @Test
    void testRunQueryWithASyntaxErrorPropagatesTheParseExceptionRatherThanReturningAStructuredError() {
        QueryDesignServiceImpl service = new QueryDesignServiceImpl();
        RuntimeQuery invalidQuery = hqlRuntimeQuery("from Employee where where where");

        assertThrows(Exception.class, () -> service.testRunQuery(SERVICE_ID, invalidQuery, PageRequest.of(0, 10)));
    }

    private static Employee employee(String name) {
        Employee employee = new Employee();
        employee.setName(name);
        return employee;
    }

    // ---- Native SQL, DML and HQL column metadata ----
    //
    // testRunQuery for native SELECT (column types are guessed from the Java types of the first row's values), native
    // and HQL DML (an update count with fixed metadata), and HQL projections (metadata from Query.getReturnTypes() and
    // getReturnAliases(), both removed in Hibernate 6). Also that executeQuery refuses DML.
    //
    // The service runs every query in its own REQUIRES_NEW transaction, so each test commits its rows first, and the
    // clean-up below deletes them again (including rows committed by the tests above).

    @AfterEach
    void deleteCommittedEmployees() {
        TransactionStatus cleanup = transactionManager.getTransaction(new DefaultTransactionDefinition());
        hibernateTemplate.bulkUpdate("delete from Employee");
        transactionManager.commit(cleanup);
    }

    /** Saves and commits Alice (active, 45000, 2020-01-15) and Bob (inactive, 60000, 2021-03-01). */
    private void saveAndCommitAliceAndBob() {
        saveEmployee("Alice", true, "45000", LocalDate.of(2020, 1, 15));
        saveEmployee("Bob", false, "60000", LocalDate.of(2021, 3, 1));
        transactionManager.commit(transactionStatus);
    }

    private static void saveEmployee(String name, boolean active, String salary, LocalDate hireDate) {
        Employee employee = employee(name);
        employee.setActive(active);
        employee.setSalary(new BigDecimal(salary));
        employee.setHireDate(hireDate);
        hibernateTemplate.save(employee);
    }

    private static RuntimeQuery query(String queryString, boolean nativeSql, QueryType type,
                                      QueryParameter... parameters) {
        return new RuntimeQuery(queryString, nativeSql, type, List.of(parameters));
    }

    private static Map<String, String> typeRefsByName(List<ReturnProperty> properties) {
        Map<String, String> typeRefs = new LinkedHashMap<>();
        properties.forEach(property -> typeRefs.put(property.getName(), property.getFieldType().getTypeRef()));
        return typeRefs;
    }

    private static BigDecimal salaryOf(String name) {
        return hibernateTemplate.execute(session -> session
            .createQuery("select e.salary from Employee e where e.name = :name", BigDecimal.class)
            .setParameter("name", name)
            .uniqueResult());
    }

    @Test
    void aNativeSelectReturnsRowValuesAndInfersColumnTypesFromTheFirstRow() {
        saveAndCommitAliceAndBob();
        RuntimeQuery nativeSelect = query("select name as \"name\", salary as \"salary\", hireDate as \"hireDate\", "
            + "active as \"active\" from Employee order by name", true, QueryType.SELECT);

        DesignServiceResponse response = new QueryDesignServiceImpl().testRunQuery(SERVICE_ID, nativeSelect,
            PageRequest.of(0, 10));

        Page<?> page = (Page<?>) response.getResults();
        assertEquals(2, page.getTotalElements());
        Map<?, ?> alice = (Map<?, ?>) page.getContent().get(0);
        assertEquals("Alice", alice.get("name"));
        assertEquals(0, new BigDecimal("45000").compareTo((BigDecimal) alice.get("salary")));
        assertEquals(Boolean.TRUE, alice.get("active"));

        // These are the raw JDBC types, not the entity's: a DATE column comes back as java.sql.Date, not LocalDate.
        // A Hibernate upgrade that changes the native read types changes the metadata the designer generates.
        assertEquals(Map.of(
                "name", "java.lang.String",
                "salary", "java.math.BigDecimal",
                "hireDate", "java.sql.Date",
                "active", "java.lang.Boolean"),
            typeRefsByName(response.getReturnProperties()));
        response.getReturnProperties()
            .forEach(property -> assertEquals(ReferenceType.PRIMITIVE, property.getFieldType().getType()));
    }

    @Test
    void aNativeUpdateWithATypedParameterReturnsTheRowCountAndChangesTheData() {
        saveAndCommitAliceAndBob();
        RuntimeQuery nativeUpdate = query("update Employee set salary = :salary where active = true", true,
            QueryType.UPDATE, new QueryParameter("salary", JavaType.BIG_DECIMAL, false, "50000"));

        DesignServiceResponse response = new QueryDesignServiceImpl().testRunQuery(SERVICE_ID, nativeUpdate,
            PageRequest.of(0, 10));

        assertEquals(1, response.getResults());
        assertEquals(1, response.getReturnProperties().size());
        ReturnProperty meta = response.getReturnProperties().get(0);
        assertNull(meta.getName());
        assertEquals("java.lang.Integer", meta.getFieldType().getTypeRef());
        assertEquals(0, new BigDecimal("50000").compareTo(salaryOf("Alice")));
        assertEquals(0, new BigDecimal("60000").compareTo(salaryOf("Bob")));
    }

    @Test
    void anHqlDeleteReturnsTheRowCountAndRemovesTheRows() {
        saveAndCommitAliceAndBob();
        RuntimeQuery hqlDelete = query("delete from Employee e where e.active = :active", false, QueryType.DELETE,
            new QueryParameter("active", JavaType.BOOLEAN, false, "false"));

        DesignServiceResponse response = new QueryDesignServiceImpl().testRunQuery(SERVICE_ID, hqlDelete,
            PageRequest.of(0, 10));

        assertEquals(1, response.getResults());
        assertEquals(List.of("Alice"), hibernateTemplate.execute(session -> session
            .createQuery("select e.name from Employee e", String.class).list()));
    }

    @Test
    void anHqlProjectionReportsEachAliasWithTheEntityAttributesJavaType() {
        saveAndCommitAliceAndBob();
        RuntimeQuery hqlProjection = query("select e.name as name, e.salary as salary, e.hireDate as hireDate "
            + "from Employee e order by e.name", false, QueryType.SELECT);

        DesignServiceResponse response = new QueryDesignServiceImpl().testRunQuery(SERVICE_ID, hqlProjection,
            PageRequest.of(0, 10));

        assertEquals(Map.of(
                "name", "java.lang.String",
                "salary", "java.math.BigDecimal",
                "hireDate", "java.time.LocalDate"),
            typeRefsByName(response.getReturnProperties()));
        Map<?, ?> alice = (Map<?, ?>) ((Page<?>) response.getResults()).getContent().get(0);
        assertEquals("Alice", alice.get("name"));
        assertEquals(LocalDate.of(2020, 1, 15), alice.get("hireDate"));
    }

    @Test
    void executeQueryRefusesDmlAndLeavesTheDataUnchanged() {
        saveAndCommitAliceAndBob();
        RuntimeQuery nativeDelete = query("delete from Employee", true, QueryType.DELETE);

        assertThrows(WMRuntimeException.class,
            () -> new QueryDesignServiceImpl().executeQuery(SERVICE_ID, nativeDelete, PageRequest.of(0, 10)));

        Long remaining = hibernateTemplate.execute(session -> session
            .createQuery("select count(e) from Employee e", Long.class).uniqueResult());
        assertEquals(2L, remaining);
    }
}
