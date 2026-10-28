/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao.query;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.hibernate.query.SyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.wavemaker.runtime.data.model.JavaType;
import com.wavemaker.runtime.data.model.QueryProcedureInput;
import com.wavemaker.runtime.data.model.UpdatableQueryInput;
import com.wavemaker.runtime.data.model.queries.QueryParameter;
import com.wavemaker.runtime.data.model.queries.QueryType;
import com.wavemaker.runtime.data.model.queries.RuntimeQuery;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the "WMQueryExecutor - HQL" scenarios from the runtime-db use-case specification against
 * {@link WMQueryExecutorImpl}'s HQL path: named queries via {@code @NamedQuery} on {@link Employee}, and ad-hoc HQL
 * {@link RuntimeQuery}s with typed parameters (see the section at the end of the class). The native-SQL path lives in
 * {@link WMQueryExecutorImplNativeSqlIntegrationTest}.
 *
 * <p><b>One spec-vs-reality gap found and documented below:</b> "hql-param-mismatch-throws" assumes a missing
 * required parameter throws {@code QueryParameterMismatchException}. That exception class exists in this module
 * but is never thrown anywhere in production code - {@code AppRuntimeParameterProvider.getValue} simply returns
 * {@code null} for a name absent from the params map. The real behavior is documented instead of a false
 * assertion.
 */
class WMQueryExecutorImplHqlIntegrationTest extends AbstractHibernateIntegrationTest {

    private WMQueryExecutorImpl executor;

    @BeforeEach
    void createExecutor() throws ReflectiveOperationException {
        executor = new WMQueryExecutorImpl();
        executor.setTemplate(hibernateTemplate);
        // init() is a private @PostConstruct method - there is no Spring container here to invoke it.
        Method init = WMQueryExecutorImpl.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(executor);
    }

    private Employee newEmployee(String name, boolean active) {
        Employee employee = new Employee();
        employee.setName(name);
        employee.setActive(active);
        hibernateTemplate.save(employee);
        return employee;
    }

    @Test
    void executeNamedQueryResolvesARegisteredNamedQueryBindsParamsAndReturnsAPage() {
        newEmployee("Carol", true);
        newEmployee("Alice", true);
        newEmployee("Bob", false);
        flushAndClear();

        // Page size 1 against 2 matches, so the total comes from the Employee.byActive__count query.
        Page<Employee> page = executor.executeNamedQuery("Employee.byActive", Map.of("active", true), Employee.class,
            PageRequest.of(0, 1, Sort.by("name")));

        assertEquals(2, page.getTotalElements());
        assertEquals(List.of("Alice"), page.getContent().stream().map(Employee::getName).toList());
    }

    @Test
    void executeNamedQueryCountUsesTheRegisteredCountQueryConventionReturningAScalarLongNotEntities() {
        newEmployee("Alice", true);
        newEmployee("Bob", true);
        newEmployee("Carol", false);
        hibernateTemplate.flush();

        Page<Employee> page = executor.executeNamedQuery(
            new QueryProcedureInput<>("Employee.byActive", Map.of("active", true), Employee.class),
            PageRequest.of(0, 1));

        // Employee.byActive__count is a "select count(*) ..." HQL query - the Page's total comes from that
        // scalar count query (2), not from loading and counting entities, and not from Integer.MAX_VALUE.
        assertEquals(2, page.getTotalElements());
    }

    @Test
    void executeNamedQueryPaginatedRunsBothTheDataQueryAndTheCountQueryAndReturnsTheRightSlice() {
        newEmployee("Alice", true);
        newEmployee("Bob", true);
        newEmployee("Carol", true);
        newEmployee("Dave", false);
        flushAndClear();

        Page<Employee> secondPage = executor.executeNamedQuery(
            new QueryProcedureInput<>("Employee.byActive", Map.of("active", true), Employee.class),
            PageRequest.of(1, 2, Sort.by("name")));

        assertEquals(3, secondPage.getTotalElements());
        assertEquals(List.of("Carol"), secondPage.getContent().stream().map(Employee::getName).toList());
    }

    @Test
    void executeNamedQuerySortedDescendingOrdersTheRows() {
        newEmployee("Alice", true);
        newEmployee("Carol", true);
        newEmployee("Bob", true);
        flushAndClear();

        Page<Employee> page = executor.executeNamedQuery("Employee.byActive", Map.of("active", true), Employee.class,
            PageRequest.of(0, 10, Sort.by(Sort.Order.desc("name"))));

        assertEquals(List.of("Carol", "Bob", "Alice"), page.getContent().stream().map(Employee::getName).toList());
    }

    @Test
    void anUnregisteredNamedQueryThrowsAnExceptionNamingTheUnknownQuery() {
        // With Hibernate 5 this was a raw NullPointerException without the query name, from the lookup of the parameter types
        // of a query that is not registered. The lookup now tolerates it and Hibernate reports the unknown query.
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> executor.executeNamedQuery(
            "Employee.doesNotExist", Map.of(), Employee.class, PageRequest.of(0, 10)));

        assertTrue(exception.getMessage() != null && exception.getMessage().contains("Employee.doesNotExist"), exception.getMessage());
    }

    @Test
    void executingANamedUpdateQueryReturnsTheAffectedRowCountNotAPage() {
        newEmployee("Alice", true);
        newEmployee("Bob", true);
        hibernateTemplate.flush();

        int updated = executor.executeNamedQuery(new UpdatableQueryInput("Employee.deactivateAll", Map.of()));
        flushAndClear();

        assertEquals(2, updated);
        Long stillActive = hibernateTemplate.execute(session -> session
            .createQuery("select count(e) from Employee e where e.active = true", Long.class).uniqueResult());
        assertEquals(0L, stillActive);
    }

    @Test
    void aParameterMissingFromTheParamsMapIsSilentlyBoundAsNullRatherThanThrowing() {
        // See class javadoc: QueryParameterMismatchException exists but nothing throws it. A named query with a
        // ":active" placeholder and no "active" key in the params map does not throw - it runs with null bound,
        // matching zero rows (missing != true/false for a NOT NULL boolean-backed column in this schema).
        newEmployee("Alice", true);
        hibernateTemplate.flush();

        Page<Employee> page = executor.executeNamedQuery(
            new QueryProcedureInput<>("Employee.byActive", Map.of(), Employee.class), PageRequest.of(0, 10));

        assertEquals(0, page.getTotalElements());
    }

    // ---- Runtime queries with typed QueryParameters ----
    //
    // These bind real QueryParameters through executeRuntimeQuery, so RuntimeParametersProvider,
    // QueryHelper.prepareParam/convertValue and ParametersProvider.configure all run. Values are given as strings, the
    // way the designer sends them, and converted by the Hibernate-backed converters in JavaTypeConverters.
    // Each query pages with size 1 against 2 matching rows, so the total is only right if the count query binds the
    // same parameters. The same tests for native SQL are in WMQueryExecutorImplNativeSqlIntegrationTest.

    /**
     * Saves, in id order: Alice (active, 45000, 2020-01-15), Alice (inactive, 50000, 2021-06-01), Bob (active, 45000,
     * 2020-01-15) and Carol (inactive, no salary, no hire date). Returns the ids in that order.
     */
    private List<Long> saveTypedParameterFixture() {
        List<Long> ids = List.of(
            newEmployee("Alice", true, "45000", LocalDate.of(2020, 1, 15)),
            newEmployee("Alice", false, "50000", LocalDate.of(2021, 6, 1)),
            newEmployee("Bob", true, "45000", LocalDate.of(2020, 1, 15)),
            newEmployee("Carol", false, null, null));
        flushAndClear();
        return ids;
    }

    private Long newEmployee(String name, boolean active, String salary, LocalDate hireDate) {
        Employee employee = newEmployee(name, active);
        employee.setSalary(salary == null ? null : new BigDecimal(salary));
        employee.setHireDate(hireDate);
        return employee.getId();
    }

    private Page<Object> runWithParameter(String where, QueryParameter parameter) {
        String query = "select e.name as name from Employee e where " + where + " order by e.id";
        return executor.executeRuntimeQuery(
            new RuntimeQuery(query, false, QueryType.SELECT, List.of(parameter)), PageRequest.of(0, 1));
    }

    /** An HQL projection row is a map from alias to value. */
    private static Object valueOf(Object row, String column) {
        return ((Map<?, ?>) row).get(column);
    }

    @Test
    void aStringParameterIsBoundAndCounted() {
        saveTypedParameterFixture();

        Page<Object> page = runWithParameter("e.name = :name", new QueryParameter("name", JavaType.STRING, false, "Alice"));

        assertEquals(2, page.getTotalElements());
        assertEquals("Alice", valueOf(page.getContent().get(0), "name"));
    }

    @Test
    void aBooleanParameterGivenAsAStringIsConvertedAndBound() {
        saveTypedParameterFixture();

        Page<Object> page = runWithParameter("e.active = :active",
            new QueryParameter("active", JavaType.BOOLEAN, false, "true"));

        assertEquals(2, page.getTotalElements());
        assertEquals("Alice", valueOf(page.getContent().get(0), "name"));
    }

    @Test
    void aBigDecimalParameterGivenAsAStringIsConvertedAndBound() {
        saveTypedParameterFixture();

        Page<Object> page = runWithParameter("e.salary = :salary",
            new QueryParameter("salary", JavaType.BIG_DECIMAL, false, "45000.00"));

        assertEquals(2, page.getTotalElements());
        assertEquals("Alice", valueOf(page.getContent().get(0), "name"));
    }

    @Test
    void aDateParameterGivenAsAStringIsConvertedAndBound() {
        saveTypedParameterFixture();

        Page<Object> page = runWithParameter("e.hireDate = :hireDate",
            new QueryParameter("hireDate", JavaType.DATE, false, "2020-01-15"));

        assertEquals(2, page.getTotalElements());
        assertEquals("Alice", valueOf(page.getContent().get(0), "name"));
    }

    @Test
    void aListParameterExpandsIntoAnInClause() {
        List<Long> ids = saveTypedParameterFixture();
        // Bob's and Carol's ids, as strings.
        List<Object> bobAndCarol = List.of(String.valueOf(ids.get(2)), String.valueOf(ids.get(3)));

        Page<Object> page = runWithParameter("e.id in (:ids)", new QueryParameter("ids", JavaType.LONG, true, bobAndCarol));

        assertEquals(2, page.getTotalElements());
        assertEquals("Bob", valueOf(page.getContent().get(0), "name"));
    }

    @Test
    void aNullParameterIsBoundAsSqlNullAndMatchesNothingWithEquals() {
        saveTypedParameterFixture();

        // "= NULL" never matches in SQL, not even Carol's null salary.
        Page<Object> page = runWithParameter("e.salary = :salary",
            new QueryParameter("salary", JavaType.BIG_DECIMAL, false, null));

        assertEquals(0, page.getTotalElements());
        assertEquals(0, page.getContent().size());
    }

    @Test
    void theSecondPageOfAParameterizedQueryReturnsTheNextRowInOrder() {
        saveTypedParameterFixture();
        RuntimeQuery query = new RuntimeQuery("select e.salary as salary from Employee e where e.name = :name order by e.id", false, QueryType.SELECT,
            List.of(new QueryParameter("name", JavaType.STRING, false, "Alice")));

        Page<Object> firstPage = executor.executeRuntimeQuery(query, PageRequest.of(0, 1));
        Page<Object> secondPage = executor.executeRuntimeQuery(query, PageRequest.of(1, 1));

        assertEquals(2, secondPage.getTotalElements());
        assertEquals(1, secondPage.getContent().size());
        assertEquals(0, new BigDecimal("45000").compareTo((BigDecimal) valueOf(firstPage.getContent().get(0), "salary")));
        assertEquals(0, new BigDecimal("50000").compareTo((BigDecimal) valueOf(secondPage.getContent().get(0), "salary")));
    }

    @Test
    void aSortOnARuntimeHqlQueryThatAlreadyHasOrderByCurrentlyFails() {
        // Production bug, pinned: QueryHelper.applySortingForHqlQuery appends " order by ..." without checking for an
        // existing ORDER BY, so the HQL becomes "... order by e.id order by name ASC". The expected behaviour is the
        // requested sort applied (replacing or extending the query's own ORDER BY).
        saveTypedParameterFixture();
        RuntimeQuery query = new RuntimeQuery("select e.name as name from Employee e order by e.id", false,
            QueryType.SELECT, List.of());

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> executor.executeRuntimeQuery(query, PageRequest.of(0, 10, Sort.by("name"))));

        assertInstanceOf(SyntaxException.class, thrown.getCause());
        assertTrue(thrown.getMessage().contains("order by e.id order by"), thrown.getMessage());
    }
}
