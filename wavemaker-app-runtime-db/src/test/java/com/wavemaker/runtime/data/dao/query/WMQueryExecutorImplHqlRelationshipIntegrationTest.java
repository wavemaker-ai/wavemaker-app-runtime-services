/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao.query;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.wavemaker.runtime.data.model.QueryProcedureInput;
import com.wavemaker.runtime.data.model.queries.QueryType;
import com.wavemaker.runtime.data.model.queries.RuntimeQuery;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.entity.Employee;
import com.wavemaker.runtime.data.support.entity.Project;
import com.wavemaker.runtime.data.support.entity.Task;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Re-covers {@code WMQueryExecutorImpl}'s HQL path against the new relationship fixture set -
 * {@link WMQueryExecutorImplHqlIntegrationTest} only ever queries a bare scalar field
 * ({@code Employee.active}/{@code hireDate}); nothing in the original "WMQueryExecutor - HQL" spec coverage
 * proves named/inline HQL correctly traverses an actual association (to-one join, self-reference, or a
 * collection) at execution time, as opposed to the query GENERATORS (covered separately in
 * {@code dao.generators.*Test}), which only prove the HQL TEXT is built correctly, never run it.
 */
class WMQueryExecutorImplHqlRelationshipIntegrationTest extends AbstractHibernateIntegrationTest {

    private WMQueryExecutorImpl executor;

    @BeforeEach
    void createExecutor() throws ReflectiveOperationException {
        executor = new WMQueryExecutorImpl();
        executor.setTemplate(hibernateTemplate);
        Method init = WMQueryExecutorImpl.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(executor);
    }

    private static Project project(String name) {
        Project project = new Project();
        project.setName(name);
        project.setStartDate(LocalDate.of(2024, 1, 1));
        return project;
    }

    private static Task task(Project project, String title, boolean done) {
        Task task = new Task();
        task.setProject(project);
        task.setTitle(title);
        task.setDone(done);
        return task;
    }

    private static Employee employee(String name, Employee manager) {
        Employee employee = new Employee();
        employee.setName(name);
        employee.setActive(true);
        employee.setManager(manager);
        return employee;
    }

    @Test
    void namedHqlJoinAcrossAMandatoryToOneAssociationFiltersCorrectly() {
        // Task.byProjectName ("from Task t where t.project.name = :name") - a registered named query joining
        // across a mandatory to-one association, contrast with Employee.byActive, which filters a plain column.
        Project platformMigration = project("Platform Migration");
        Project portalRevamp = project("Customer Portal Revamp");
        hibernateTemplate.persist(platformMigration);
        hibernateTemplate.persist(portalRevamp);
        hibernateTemplate.persist(task(platformMigration, "Migrate schema", false));
        hibernateTemplate.persist(task(platformMigration, "Set up CI", true));
        hibernateTemplate.persist(task(portalRevamp, "Design UI", false));
        flushAndClear();

        // Page size 1 against 2 matches, so the total comes from the Task.byProjectName__count query (without it,
        // a named query's total is reported as Integer.MAX_VALUE).
        Page<Task> page = executor.executeNamedQuery(
            new QueryProcedureInput<>("Task.byProjectName", Map.of("name", "Platform Migration"), Task.class),
            PageRequest.of(0, 1, Sort.by("title")));

        assertEquals(2, page.getTotalElements());
        assertEquals(List.of("Migrate schema"), page.getContent().stream().map(Task::getTitle).toList());
    }

    @Test
    void inlineHqlTraversesASelfReferencingManyToOneAssociation() {
        // Employee.manager is a self-referencing association (Employee -> Employee) - a structurally different
        // join from Task.project above (that one links two distinct entity types).
        Employee ivan = employee("Ivan", null);
        hibernateTemplate.persist(ivan);
        hibernateTemplate.persist(employee("Alice", ivan));
        hibernateTemplate.persist(employee("Bob", ivan));
        hibernateTemplate.persist(employee("Carol", null));
        flushAndClear();

        RuntimeQuery byManagerName = new RuntimeQuery(
            "from Employee e where e.manager.name = 'Ivan'", false, QueryType.SELECT, List.of());
        Page<Object> page = executor.executeRuntimeQuery(byManagerName, PageRequest.of(0, 1, Sort.by("name")));

        assertEquals(2, page.getTotalElements());
        assertEquals("Alice", ((Employee) page.getContent().get(0)).getName());
    }

    @Test
    void inlineHqlFiltersByCollectionSizeOnAOneToManyAssociation() {
        // size(p.tasks) exercises HQL's collection-valued-association predicate, a different kind of relational
        // filter again from the two to-one joins above - it never fetches the collection itself, just counts it.
        Project twoTasks = project("Platform Migration");
        Project oneTask = project("Customer Portal Revamp");
        hibernateTemplate.persist(twoTasks);
        hibernateTemplate.persist(oneTask);
        hibernateTemplate.persist(task(twoTasks, "Migrate schema", false));
        hibernateTemplate.persist(task(twoTasks, "Set up CI", true));
        hibernateTemplate.persist(task(oneTask, "Design UI", false));
        flushAndClear();

        RuntimeQuery byTaskCount = new RuntimeQuery(
            "from Project p where size(p.tasks) >= 2", false, QueryType.SELECT, List.of());
        Page<Object> page = executor.executeRuntimeQuery(byTaskCount, PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("Platform Migration", ((Project) page.getContent().get(0)).getName());
    }

    @Test
    void inlineBulkUpdateAcrossARelationshipFilterReturnsTheAffectedRowCount() {
        // Real finding: Hibernate 5's bulk-statement HQL translator does not support an association path
        // traversal directly in an UPDATE's WHERE clause the way SELECT allows. "update Task t set t.done =
        // true where t.project.name = '...'" compiles to malformed SQL - confirmed directly:
        // "update Task cross join  set done=true where name='Platform Migration'" (an incomplete/dangling
        // CROSS JOIN with no join target, and an ambiguous unqualified "name" column). The documented, working
        // way to filter a bulk UPDATE/DELETE by a related entity's property is an IN-subquery instead, used here.
        Project project = project("Platform Migration");
        Project otherProject = project("Customer Portal Revamp");
        hibernateTemplate.persist(project);
        hibernateTemplate.persist(otherProject);
        hibernateTemplate.persist(task(project, "Migrate schema", false));
        hibernateTemplate.persist(task(project, "Set up CI", false));
        hibernateTemplate.persist(task(otherProject, "Design UI", false));
        hibernateTemplate.flush();

        RuntimeQuery bulkUpdate = new RuntimeQuery(
            "update Task t set t.done = true "
                + "where t.project in (select p from Project p where p.name = 'Platform Migration')",
            false, QueryType.UPDATE, List.of());
        int updated = executor.executeRuntimeQueryForUpdate(bulkUpdate);
        flushAndClear();

        assertEquals(2, updated);
        List<String> doneTitles = hibernateTemplate.execute(session -> session
            .createQuery("select t.title from Task t where t.done = true order by t.title", String.class).list());
        assertEquals(List.of("Migrate schema", "Set up CI"), doneTitles);
    }
}
