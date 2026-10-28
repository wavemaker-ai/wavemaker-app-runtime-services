/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao.callbacks;

import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.commons.variable.VariableType;
import com.wavemaker.runtime.data.dao.procedure.parameters.ResolvableParam;
import com.wavemaker.runtime.data.dao.procedure.parameters.RuntimeParameter;
import com.wavemaker.runtime.data.model.JavaType;
import com.wavemaker.runtime.data.model.procedures.ProcedureParameter;
import com.wavemaker.runtime.data.model.procedures.ProcedureParameterType;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.entity.Project;
import com.wavemaker.runtime.data.support.entity.Task;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Re-covers {@code NativeProcedureExecutor} against the new relationship fixture set - the original coverage
 * only ever exercises procedures whose own SQL body touches a single table ({@code Employee}). These two
 * procedures instead have a JOIN/relationship-filtered subquery inside the procedure body itself, proving the
 * {@code CallableStatement}-level IN/OUT binding and affected-row handling still work correctly once the
 * procedure's own SQL - not just the caller's query - spans more than one table.
 */
class NativeProcedureExecutorRelationshipIntegrationTest extends AbstractHibernateIntegrationTest {

    @BeforeAll
    static void createStoredProcedures() {
        hibernateTemplate.execute(session -> {
            session.doWork((java.sql.Connection connection) -> {
                try (Statement statement = connection.createStatement()) {
                    if (isPostgres()) {
                        createPostgresProcedures(statement);
                    } else {
                        createHsqldbProcedures(statement);
                    }
                }
            });
            return null;
        });
    }

    private static void createHsqldbProcedures(Statement statement) throws java.sql.SQLException {
        statement.execute(
            "CREATE PROCEDURE count_tasks_for_project(IN project_name VARCHAR(255), OUT task_count INTEGER) "
                + "READS SQL DATA "
                + "BEGIN ATOMIC "
                + "SET task_count = (SELECT COUNT(*) FROM Task t WHERE t.project_id = "
                + "(SELECT id FROM Project WHERE name = project_name)); "
                + "END");
        statement.execute(
            "CREATE PROCEDURE mark_project_tasks_done(IN project_name VARCHAR(255)) "
                + "MODIFIES SQL DATA "
                + "BEGIN ATOMIC "
                + "UPDATE Task SET done = TRUE WHERE project_id = "
                + "(SELECT id FROM Project WHERE name = project_name); "
                + "END");
    }

    private static void createPostgresProcedures(Statement statement) throws java.sql.SQLException {
        // Both are FUNCTIONs, not PROCEDUREs, for the same reason documented on NativeProcedureExecutorIntegrationTest's
        // increment_value: a true Postgres PROCEDURE rejects the {call ...} JDBC escape NativeProcedureExecutor
        // always uses, regardless of whether it has OUT parameters or not.
        statement.execute(
            "CREATE FUNCTION count_tasks_for_project(IN project_name VARCHAR, OUT task_count INTEGER) "
                + "LANGUAGE plpgsql AS $$ BEGIN "
                + "SELECT COUNT(*) INTO task_count FROM task t JOIN project p ON t.project_id = p.id "
                + "WHERE p.name = project_name; "
                + "END; $$");
        statement.execute(
            "CREATE FUNCTION mark_project_tasks_done(IN project_name VARCHAR) RETURNS VOID "
                + "LANGUAGE plpgsql AS $$ BEGIN "
                + "UPDATE task SET done = TRUE WHERE project_id = (SELECT id FROM project WHERE name = project_name); "
                + "END; $$");
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

    private static ProcedureParameter param(String name, JavaType type, ProcedureParameterType parameterType) {
        ProcedureParameter parameter = new ProcedureParameter();
        parameter.setName(name);
        parameter.setType(type);
        parameter.setParameterType(parameterType);
        parameter.setVariableType(VariableType.PROMPT);
        return parameter;
    }

    @Test
    void anInParameterFeedsAStoredProcedureThatJoinsAcrossARelationshipAndReturnsAnAggregateOutParameter() {
        Project platformMigration = project("Platform Migration");
        Project portalRevamp = project("Customer Portal Revamp");
        hibernateTemplate.persist(platformMigration);
        hibernateTemplate.persist(portalRevamp);
        hibernateTemplate.persist(task(platformMigration, "Migrate schema", false));
        hibernateTemplate.persist(task(platformMigration, "Set up CI", true));
        hibernateTemplate.persist(task(portalRevamp, "Design UI", false));
        hibernateTemplate.flush();

        ProcedureParameter inParam = param("project_name", JavaType.STRING, ProcedureParameterType.IN);
        ProcedureParameter outParam = param("task_count", JavaType.INTEGER, ProcedureParameterType.OUT);
        List<ResolvableParam> params = List.of(
            new RuntimeParameter(inParam, Map.of("project_name", "Platform Migration")),
            new RuntimeParameter(outParam, Map.of()));

        Map result = NativeProcedureExecutor.execute(currentSession(), "{call count_tasks_for_project(?, ?)}", params, Map.class);

        assertEquals(2, result.get("task_count"));
    }

    @Test
    void aProcedureWithNoOutParameterPerformsARelationshipFilteredUpdateAsASideEffect() {
        Project project = project("Platform Migration");
        Project otherProject = project("Customer Portal Revamp");
        hibernateTemplate.persist(project);
        hibernateTemplate.persist(otherProject);
        hibernateTemplate.persist(task(project, "Migrate schema", false));
        hibernateTemplate.persist(task(project, "Set up CI", false));
        hibernateTemplate.persist(task(otherProject, "Design UI", false));
        hibernateTemplate.flush();

        ProcedureParameter inParam = param("project_name", JavaType.STRING, ProcedureParameterType.IN);
        List<ResolvableParam> params = List.of(
            new RuntimeParameter(inParam, Map.of("project_name", "Platform Migration")));

        NativeProcedureExecutor.execute(currentSession(), "{call mark_project_tasks_done(?)}", params, Map.class);

        hibernateTemplate.clear();
        long doneCount = ((Number) hibernateTemplate.execute(session -> session
            .createQuery("select count(*) from Task t where t.done = true").uniqueResult())).longValue();
        long notDoneCount = ((Number) hibernateTemplate.execute(session -> session
            .createQuery("select count(*) from Task t where t.done = false").uniqueResult())).longValue();
        assertEquals(2L, doneCount);
        assertEquals(1L, notDoneCount);
    }
}
