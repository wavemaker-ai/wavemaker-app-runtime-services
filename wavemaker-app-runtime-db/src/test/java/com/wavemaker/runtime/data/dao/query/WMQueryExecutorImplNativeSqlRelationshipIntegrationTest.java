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

import com.wavemaker.runtime.data.model.queries.QueryType;
import com.wavemaker.runtime.data.model.queries.RuntimeQuery;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.entity.Project;
import com.wavemaker.runtime.data.support.entity.Task;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Re-covers {@code WMQueryExecutorImpl}'s native-SQL path against the new relationship fixture set - unlike the
 * HQL path (see {@code WMQueryExecutorImplHqlRelationshipIntegrationTest}), native SQL has no Hibernate object-
 * model navigation at all; "relationship" here just means a real multi-table SQL JOIN, written by hand, exactly
 * as a hand-written native query in a generated app would be. The original native-SQL coverage only ever
 * queries a single table ({@code Employee}) - nothing proved the native path's row-to-map extraction, the
 * paginated wrapper-count mechanism, or DML row counting still work correctly once a JOIN is involved.
 */
class WMQueryExecutorImplNativeSqlRelationshipIntegrationTest extends AbstractHibernateIntegrationTest {

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

    @Test
    void nativeSqlJoinAcrossTwoPhysicalTablesReturnsColumnsFromBoth() {
        Project platformMigration = project("Platform Migration");
        Project portalRevamp = project("Customer Portal Revamp");
        hibernateTemplate.persist(platformMigration);
        hibernateTemplate.persist(portalRevamp);
        hibernateTemplate.persist(task(platformMigration, "Migrate schema", false));
        hibernateTemplate.persist(task(platformMigration, "Set up CI", true));
        hibernateTemplate.persist(task(portalRevamp, "Design UI", false));
        flushAndClear();

        // Aliases double-quoted deliberately, same reason as every other native-SQL test this session: an
        // unquoted alias folds to each database's own default case (HSQLDB upper-cases, Postgres lower-cases).
        RuntimeQuery joinQuery = new RuntimeQuery(
            "select t.title as \"title\", p.name as \"project_name\" "
                + "from task t join project p on t.project_id = p.id "
                + "where p.name = 'Platform Migration' order by t.title",
            true, QueryType.SELECT, List.of());
        // Page size 1 against 2 matches, so the total comes from the wrapper count query.
        Page<Object> page = executor.executeRuntimeQuery(joinQuery, PageRequest.of(0, 1));

        assertEquals(2, page.getTotalElements());
        assertEquals(Map.of("title", "Migrate schema", "project_name", "Platform Migration"), page.getContent().get(0));
    }

    @Test
    void nativeSqlGroupByAcrossAJoinReturnsAggregatedRows() {
        Project platformMigration = project("Platform Migration");
        Project portalRevamp = project("Customer Portal Revamp");
        hibernateTemplate.persist(platformMigration);
        hibernateTemplate.persist(portalRevamp);
        hibernateTemplate.persist(task(platformMigration, "Migrate schema", false));
        hibernateTemplate.persist(task(platformMigration, "Set up CI", true));
        hibernateTemplate.persist(task(portalRevamp, "Design UI", false));
        flushAndClear();

        RuntimeQuery groupByQuery = new RuntimeQuery(
            "select p.name as \"project_name\", count(*) as \"task_count\" "
                + "from task t join project p on t.project_id = p.id "
                + "group by p.name",
            true, QueryType.SELECT, List.of());
        // Page size 1 against 2 groups, so the total comes from the wrapper count query.
        assertEquals(2, executor.executeRuntimeQuery(groupByQuery, PageRequest.of(0, 1)).getTotalElements());

        Page<Object> page = executor.executeRuntimeQuery(groupByQuery, PageRequest.of(0, 10));
        Map<String, Long> countsByProject = new java.util.HashMap<>();
        for (Object row : page.getContent()) {
            Map<?, ?> map = (Map<?, ?>) row;
            countsByProject.put((String) map.get("project_name"), ((Number) map.get("task_count")).longValue());
        }
        assertEquals(2L, countsByProject.get("Platform Migration"));
        assertEquals(1L, countsByProject.get("Customer Portal Revamp"));
    }

    @Test
    void aPaginatedNativeSqlJoinQueryDerivesItsTotalFromAWrapperCountQuery() {
        // Contrast with the single-table version of this scenario (WMQueryExecutorImplNativeSqlIntegrationTest):
        // proves the count-query wrapping mechanism still produces the correct total once the underlying query's
        // FROM clause has a JOIN in it, not just a single table name.
        Project project = project("Platform Migration");
        hibernateTemplate.persist(project);
        for (int i = 0; i < 5; i++) {
            hibernateTemplate.persist(task(project, "Task " + i, false));
        }
        flushAndClear();

        RuntimeQuery joinQuery = new RuntimeQuery(
            "select t.title as \"title\" from task t join project p on t.project_id = p.id "
                + "where p.name = 'Platform Migration' order by t.title",
            true, QueryType.SELECT, List.of());
        Page<Object> secondPage = executor.executeRuntimeQuery(joinQuery, PageRequest.of(1, 2));

        assertEquals(5, secondPage.getTotalElements());
        assertEquals(List.of(Map.of("title", "Task 2"), Map.of("title", "Task 3")), secondPage.getContent());
    }

    @Test
    void nativeSqlDmlUpdateFiltersByARelationshipSubqueryAndReturnsTheAffectedRowCount() {
        // Contrast with the HQL version of this scenario (WMQueryExecutorImplHqlRelationshipIntegrationTest):
        // native SQL is raw text handed straight to the JDBC driver, with no Hibernate HQL translation at all,
        // so there's no equivalent to the "update ... where t.project.name = ..." bulk-statement limitation
        // found on the HQL side - a subquery filtering by a related table is just plain, standard SQL here.
        Project project = project("Platform Migration");
        Project otherProject = project("Customer Portal Revamp");
        hibernateTemplate.persist(project);
        hibernateTemplate.persist(otherProject);
        hibernateTemplate.persist(task(project, "Migrate schema", false));
        hibernateTemplate.persist(task(project, "Set up CI", false));
        hibernateTemplate.persist(task(otherProject, "Design UI", false));
        flushAndClear();

        RuntimeQuery update = new RuntimeQuery(
            "update task set done = true "
                + "where project_id = (select id from project where name = 'Platform Migration')",
            true, QueryType.UPDATE, List.of());
        int updated = executor.executeRuntimeQueryForUpdate(update);
        flushAndClear();

        assertEquals(2, updated);
        List<String> doneTitles = hibernateTemplate.execute(session -> session
            .createQuery("select t.title from Task t where t.done = true order by t.title", String.class).list());
        assertEquals(List.of("Migrate schema", "Set up CI"), doneTitles);
    }
}
