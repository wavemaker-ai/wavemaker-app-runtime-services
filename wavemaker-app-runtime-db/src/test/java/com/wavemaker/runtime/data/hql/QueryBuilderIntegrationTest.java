/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.hql;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.dao.query.providers.AppRuntimeParameterProvider;
import com.wavemaker.runtime.data.dao.query.providers.ParametersProvider;
import com.wavemaker.runtime.data.dao.query.types.wmql.WMQLDefaultTypeHelper;
import com.wavemaker.runtime.data.filter.WMQueryInfo;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.entity.Employee;
import com.wavemaker.runtime.data.support.entity.Project;
import com.wavemaker.runtime.data.support.entity.Task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The other test classes in this package assert the exact generated HQL string; this one instead proves that
 * string actually runs correctly against a real Hibernate session - in particular for {@code withFilter(String)},
 * which routes through the full {@code HqlParser}/{@code WMQueryGrammarInterceptor} pipeline rather than the
 * builder's own simple string assembly, so a pure string-equality check wouldn't be very convincing on its own.
 * <p>
 * Parameters are bound via {@link AppRuntimeParameterProvider} - the same mechanism
 * {@code WMGenericDaoImpl}/{@code HqlQueryHelper} use in production - rather than binding
 * {@code WMQueryParamInfo#getValue()} directly: a literal parsed out of a raw filter string (as opposed to one
 * built from {@code withFilterCondition}) carries a declared {@code JavaType} that needs converting through
 * before binding, or Hibernate throws trying to bind the raw parsed string against a non-String column.
 */
class QueryBuilderIntegrationTest extends AbstractHibernateIntegrationTest {

    @BeforeEach
    void seedEmployees() {
        for (String name : List.of("Alice", "Bob")) {
            Employee employee = new Employee();
            employee.setName(name);
            employee.setActive("Alice".equals(name));
            employee.setSalary(new BigDecimal("Alice".equals(name) ? "100" : "200"));
            hibernateTemplate.save(employee);
        }
        flushAndClear();
    }

    private static void configureParams(Session session, Query<?> query, WMQueryInfo queryInfo) {
        ParametersProvider provider =
            new AppRuntimeParameterProvider(queryInfo, session.getTypeHelper(), new WMQLDefaultTypeHelper());
        provider.configure(session, query);
    }

    private static <T> List<T> executeSelect(Session session, WMQueryInfo queryInfo, Class<T> resultType) {
        Query<T> query = session.createQuery(queryInfo.getQuery(), resultType);
        configureParams(session, query, queryInfo);
        return query.list();
    }

    private static int executeUpdate(Session session, WMQueryInfo queryInfo) {
        Query<?> query = session.createQuery(queryInfo.getQuery());
        configureParams(session, query, queryInfo);
        return query.executeUpdate();
    }

    @Test
    void selectWithFilterConditionExecutesAndReturnsTheMatchingRow() {
        WMQueryInfo queryInfo = SelectQueryBuilder.newBuilder(Employee.class)
            .withFilterCondition("name", "Alice")
            .build();

        List<Employee> result = hibernateTemplate.execute(session -> executeSelect(session, queryInfo, Employee.class));

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).getName());
    }

    @Test
    void selectWithRawFilterStringParsesAndExecutesCorrectly() {
        WMQueryInfo queryInfo = SelectQueryBuilder.newBuilder(Employee.class)
            .withFilter("active = true")
            .build();

        List<Employee> result = hibernateTemplate.execute(session -> executeSelect(session, queryInfo, Employee.class));

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).getName());
    }

    @Test
    void updateQueryExecutesAgainstRealRows() {
        WMQueryInfo queryInfo = new UpdateQueryBuilder(Employee.class)
            .withSetter("active", false)
            .withFilterCondition("name", "Alice")
            .build();

        int updated = hibernateTemplate.execute(session -> executeUpdate(session, queryInfo));

        assertEquals(1, updated);

        WMQueryInfo verify = SelectQueryBuilder.newBuilder(Employee.class).withFilterCondition("active", true).build();
        List<Employee> stillActive = hibernateTemplate.execute(session -> executeSelect(session, verify, Employee.class));
        assertTrue(stillActive.isEmpty());
    }

    @Test
    void deleteQueryExecutesAgainstRealRows() {
        WMQueryInfo queryInfo = new DeleteQueryBuilder(Employee.class)
            .withFilterCondition("name", "Bob")
            .build();

        int deleted = hibernateTemplate.execute(session -> executeUpdate(session, queryInfo));

        assertEquals(1, deleted);

        WMQueryInfo verify = SelectQueryBuilder.newBuilder(Employee.class).build();
        List<Employee> remaining = hibernateTemplate.execute(session -> executeSelect(session, verify, Employee.class));
        assertEquals(1, remaining.size());
        assertFalse(remaining.stream().anyMatch(e -> "Bob".equals(e.getName())));
    }

    @Test
    void aProjectionQueryReturnsOnlyTheProjectedValues() {
        WMQueryInfo queryInfo = SelectQueryBuilder.newBuilder(Employee.class)
            .withFields(List.of("name", "salary"))
            .withFilterCondition("active", true)
            .build();

        List<Object[]> rows = hibernateTemplate.execute(session -> executeSelect(session, queryInfo, Object[].class));

        assertEquals(1, rows.size());
        assertEquals(2, rows.get(0).length);
        assertEquals("Alice", rows.get(0)[0]);
        assertEquals(0, new BigDecimal("100").compareTo((BigDecimal) rows.get(0)[1]));
    }

    @Test
    void theCountQueryRunsAndCountsTheMatchingRows() {
        WMQueryInfo countQuery = SelectQueryBuilder.newBuilder(Employee.class)
            .withFilter("active = true")
            .buildCountQuery()
            .orElseThrow();

        List<Long> count = hibernateTemplate.execute(session -> executeSelect(session, countQuery, Long.class));

        assertEquals(List.of(1L), count);
    }

    @Test
    void aRelationshipFilterConditionCurrentlyCrashesInsideHibernate() {
        // Bug: withFilterCondition names the parameter "wm_filter_" + the field, so "project.name" gives the
        // placeholder :wm_filter_project.name, which is not a valid HQL parameter name, and Hibernate's parser fails
        // with a NullPointerException. Select, update and delete share this code. No production caller passes a
        // dotted field today (findById, findBy and the temporal DAO pass plain fields).
        seedTwoProjectsWithATaskEach();
        WMQueryInfo queryInfo = SelectQueryBuilder.newBuilder(Task.class)
            .withFilterCondition("project.name", "Platform Migration")
            .build();

        assertThrows(NullPointerException.class,
            () -> hibernateTemplate.execute(session -> executeSelect(session, queryInfo, Task.class)));
    }

    @Test
    void aRelationshipPathInAFilterStringWorks() {
        // The working alternative: withFilter goes through the WMQL parser, which numbers its parameters.
        seedTwoProjectsWithATaskEach();
        WMQueryInfo queryInfo = SelectQueryBuilder.newBuilder(Task.class)
            .withFilter("project.name = 'Platform Migration'")
            .build();

        List<Task> result = hibernateTemplate.execute(session -> executeSelect(session, queryInfo, Task.class));

        assertEquals(1, result.size());
        assertEquals("Migrate schema", result.get(0).getTitle());
    }

    private void seedTwoProjectsWithATaskEach() {
        Project platformMigration = project("Platform Migration");
        Project portalRevamp = project("Customer Portal Revamp");
        hibernateTemplate.persist(platformMigration);
        hibernateTemplate.persist(portalRevamp);
        hibernateTemplate.persist(task(platformMigration, "Migrate schema"));
        hibernateTemplate.persist(task(portalRevamp, "Design UI"));
        flushAndClear();
    }

    private static Project project(String name) {
        Project project = new Project();
        project.setName(name);
        project.setStartDate(LocalDate.of(2024, 1, 1));
        return project;
    }

    private static Task task(Project project, String title) {
        Task task = new Task();
        task.setProject(project);
        task.setTitle(title);
        task.setDone(false);
        return task;
    }
}
