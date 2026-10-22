/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao.callbacks;

import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

import com.wavemaker.runtime.commons.variable.VariableType;
import com.wavemaker.runtime.data.dao.procedure.parameters.ResolvableParam;
import com.wavemaker.runtime.data.dao.procedure.parameters.RuntimeParameter;
import com.wavemaker.runtime.data.model.JavaType;
import com.wavemaker.runtime.data.model.procedures.ProcedureParameter;
import com.wavemaker.runtime.data.model.procedures.ProcedureParameterType;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Covers the "WMProcedureExecutor" scenarios that live at the JDBC {@code CallableStatement} level, through
 * {@link NativeProcedureExecutor} against real stored procedures created in {@code @BeforeAll}: HSQLDB procedures by
 * default, or the equivalent Postgres functions with {@code -Dwm.test.db=postgres} (see {@link #createPostgresProcedures}).
 *
 * <p>{@code NativeProcedureExecutor} runs through {@code doReturningWork}, which does not auto-flush, so tests that
 * read table data flush first. Result sets (spec "proc-single-result-set" / "proc-multiple-result-sets"):
 * <ul>
 *   <li>HSQLDB: for a {@code DYNAMIC RESULT SETS} procedure {@code execute()} returns false, and the result set is only
 *   reachable through {@code getMoreResults()}, which {@code getResultMap} never calls, so nothing is returned.</li>
 *   <li>Postgres: a {@code RETURNS SETOF} function's rows come back under {@code "content"}; two {@code REFCURSOR} OUT
 *   parameters not declared as such come back as raw, unread {@link java.sql.ResultSet} handles.</li>
 * </ul>
 */
class NativeProcedureExecutorIntegrationTest extends AbstractHibernateIntegrationTest {

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

    // HSQLDB and Postgres use genuinely different DDL syntax for stored procedures - these are deliberate
    // translations, not a copy-paste. See postgres-init/03-procedures.sql, which documents the same 4 procedures
    // (and the reasoning behind each translation) for the Testcontainers Postgres instance.
    private static void createHsqldbProcedures(Statement statement) throws java.sql.SQLException {
        statement.execute(
            "CREATE PROCEDURE double_value(IN x INTEGER, OUT y INTEGER) "
                + "MODIFIES SQL DATA "
                + "BEGIN ATOMIC "
                + "SET y = x * 2; "
                + "END");
        statement.execute(
            "CREATE PROCEDURE increment_value(INOUT x INTEGER) "
                + "MODIFIES SQL DATA "
                + "BEGIN ATOMIC "
                + "SET x = x + 1; "
                + "END");
        statement.execute(
            "CREATE PROCEDURE list_active_employee_names() "
                + "READS SQL DATA DYNAMIC RESULT SETS 1 "
                + "BEGIN ATOMIC "
                + "DECLARE activeNames CURSOR WITH RETURN FOR "
                + "SELECT name FROM Employee WHERE active = TRUE; "
                + "OPEN activeNames; "
                + "END");
        statement.execute(
            "CREATE PROCEDURE list_active_and_inactive_employee_names() "
                + "READS SQL DATA DYNAMIC RESULT SETS 2 "
                + "BEGIN ATOMIC "
                + "DECLARE activeNames CURSOR WITH RETURN FOR "
                + "SELECT name FROM Employee WHERE active = TRUE; "
                + "DECLARE inactiveNames CURSOR WITH RETURN FOR "
                + "SELECT name FROM Employee WHERE active = FALSE; "
                + "OPEN activeNames; "
                + "OPEN inactiveNames; "
                + "END");
    }

    private static void createPostgresProcedures(Statement statement) throws java.sql.SQLException {
        // Postgres PROCEDUREs cannot declare a plain OUT parameter at all (only IN/INOUT/VARIADIC) - a
        // FUNCTION is the real Postgres equivalent for returning a value by name.
        statement.execute(
            "CREATE FUNCTION double_value(IN x INTEGER, OUT y INTEGER) "
                + "LANGUAGE plpgsql AS $$ BEGIN y := x * 2; END; $$");
        // A true Postgres PROCEDURE also supports INOUT (native since PG11) - but it can only be invoked with
        // CALL, not the {call ...} JDBC escape NativeProcedureExecutor always uses (confirmed directly: Postgres
        // itself rejects it - "increment_value(integer) is a procedure. Hint: To call a procedure, use CALL.").
        // A FUNCTION with an INOUT parameter behaves identically for this scenario and IS invokable through
        // {call ...}, same as double_value above.
        statement.execute(
            "CREATE FUNCTION increment_value(INOUT x INTEGER) "
                + "LANGUAGE plpgsql AS $$ BEGIN x := x + 1; END; $$");
        // No DYNAMIC RESULT SETS equivalent exists in Postgres - RETURNS SETOF is the idiomatic shape for "a
        // procedure that hands back rows".
        statement.execute(
            "CREATE FUNCTION list_active_employee_names() RETURNS SETOF VARCHAR "
                + "LANGUAGE plpgsql AS $$ BEGIN RETURN QUERY SELECT name FROM employee WHERE active = TRUE; END; $$");
        // Two independent result sets need two REFCURSOR OUT params, each opened for its own query - a single
        // RETURNS SETOF can only ever produce one result set.
        statement.execute(
            "CREATE FUNCTION list_active_and_inactive_employee_names("
                + "OUT active_names REFCURSOR, OUT inactive_names REFCURSOR) "
                + "LANGUAGE plpgsql AS $$ BEGIN "
                + "OPEN active_names FOR SELECT name FROM employee WHERE active = TRUE; "
                + "OPEN inactive_names FOR SELECT name FROM employee WHERE active = FALSE; "
                + "END; $$");
    }

    private static ProcedureParameter param(String name, ProcedureParameterType parameterType) {
        ProcedureParameter parameter = new ProcedureParameter();
        parameter.setName(name);
        parameter.setType(JavaType.INTEGER);
        parameter.setParameterType(parameterType);
        parameter.setVariableType(VariableType.PROMPT);
        return parameter;
    }

    private Employee newEmployee(String name, boolean active) {
        Employee employee = new Employee();
        employee.setName(name);
        employee.setActive(active);
        hibernateTemplate.save(employee);
        return employee;
    }

    @Test
    void anInParameterIsPassedAndAnOutParameterIsReadBack() {
        ProcedureParameter inParam = param("x", ProcedureParameterType.IN);
        ProcedureParameter outParam = param("y", ProcedureParameterType.OUT);

        List<ResolvableParam> params = List.of(
            new RuntimeParameter(inParam, Map.of("x", 21)),
            new RuntimeParameter(outParam, Map.of()));

        Map result = NativeProcedureExecutor.execute(hibernateTemplate, "{call double_value(?, ?)}", params, Map.class);

        assertEquals(42, result.get("y"));
    }

    @Test
    void anInOutParameterIsRegisteredForBothInputAndOutputAndTheOutputValueDiffersFromTheInput() {
        ProcedureParameter inOutParam = param("x", ProcedureParameterType.IN_OUT);

        List<ResolvableParam> params = List.of(new RuntimeParameter(inOutParam, Map.of("x", 5)));

        Map result = NativeProcedureExecutor.execute(hibernateTemplate, "{call increment_value(?)}", params, Map.class);

        assertEquals(6, result.get("x"));
    }

    @Test
    void aCursorProcedureCalledAfterAFlushReturnsTheMatchingRowsOnPostgres() {
        // Postgres returns the active employee's row under \"content\"; HSQLDB returns nothing (see the class
        // description).
        newEmployee("Alice", true);
        newEmployee("Bob", false);
        flushAndClear();

        Map result = NativeProcedureExecutor.execute(
            hibernateTemplate, "{call list_active_employee_names()}", List.of(), Map.class);

        if (isPostgres()) {
            List<?> content = (List<?>) result.get(NativeProcedureExecutor.CONTENT_FIELD);
            assertEquals(1, content.size(), String.valueOf(content));
            assertEquals(List.of("Alice"), List.copyOf(((Map<?, ?>) content.get(0)).values()));
        } else {
            assertTrue(result.isEmpty());
        }
    }

    @Test
    void aProcedureReturningMultipleResultSetsDoesNotReadThem() {
        // HSQLDB returns nothing (see the class description); Postgres returns the two REFCURSOR OUT values as raw,
        // unread ResultSet handles, because they are not declared as CURSOR parameters.
        newEmployee("Alice", true);
        newEmployee("Bob", false);
        flushAndClear();

        Map result = NativeProcedureExecutor.execute(
            hibernateTemplate, "{call list_active_and_inactive_employee_names()}", List.of(), Map.class);

        if (isPostgres()) {
            List<?> content = (List<?>) result.get(NativeProcedureExecutor.CONTENT_FIELD);
            assertEquals(1, content.size());
            Map<?, ?> row = (Map<?, ?>) content.get(0);
            assertTrue(row.get("active_names") instanceof java.sql.ResultSet);
            assertTrue(row.get("inactive_names") instanceof java.sql.ResultSet);
        } else {
            assertTrue(result.isEmpty());
        }
    }

    @Test
    void convertToOldResponseWrapsTheResultMapWhenItIsNotASingleCursorColumn() {
        // A single scalar OUT param produces a map with exactly one key, but its value is not itself a List
        // (that only happens for a single CURSOR-typed OUT param) - so the legacy response is [wholeMap], not
        // the value unwrapped. Easy to get backwards, which is why it's covered explicitly.
        Map result = Map.of("y", 42);

        List<Object> oldResponse = NativeProcedureExecutor.convertToOldResponse(result);

        assertEquals(List.of(Map.of("y", 42)), oldResponse);
    }

    @Test
    void aNullInValueIsBoundAsSqlNull() {
        ProcedureParameter inParam = param("x", ProcedureParameterType.IN);
        ProcedureParameter outParam = param("y", ProcedureParameterType.OUT);
        List<ResolvableParam> params = List.of(
            new RuntimeParameter(inParam, Map.of()),
            new RuntimeParameter(outParam, Map.of()));

        Map result = NativeProcedureExecutor.execute(hibernateTemplate, "{call double_value(?, ?)}", params, Map.class);

        assertTrue(result.containsKey("y"));
        assertNull(result.get("y"));
    }

    @Test
    void cursorOutParametersCurrentlyFailOnPostgresBecauseTheOracleCursorTypeIsRegistered() {
        assumeTrue(isPostgres(), "REFCURSOR OUT parameters are a Postgres function");
        newEmployee("Alice", true);
        newEmployee("Bob", false);
        flushAndClear();
        List<ResolvableParam> params = List.of(
            new RuntimeParameter(cursorOutParam("active_names"), Map.of()),
            new RuntimeParameter(cursorOutParam("inactive_names"), Map.of()));

        // Bug: JDBCUtils.getSqlTypeCode(CURSOR) always registers the Oracle cursor type code, which the Postgres
        // driver rejects (it expects Types.REF_CURSOR). Once fixed, each OUT value should be read into a list of rows:
        // active_names = [{name=Alice}], inactive_names = [{name=Bob}].
        InvalidDataAccessResourceUsageException failure = assertThrows(InvalidDataAccessResourceUsageException.class,
            () -> NativeProcedureExecutor.execute(
                hibernateTemplate, "{call list_active_and_inactive_employee_names(?, ?)}", params, Map.class));
        assertTrue(failure.getMostSpecificCause().getMessage().contains("java.sql.Types=" + Types.REF_CURSOR));
    }

    @Test
    void convertToOldResponseUnwrapsASingleListValue() {
        List<Map<String, Object>> rows = List.of(Map.of("name", "Alice"), Map.of("name", "Bob"));

        assertEquals(List.copyOf(rows), NativeProcedureExecutor.convertToOldResponse(Map.of("content", rows)));
    }

    private static ProcedureParameter cursorOutParam(String name) {
        ProcedureParameter parameter = param(name, ProcedureParameterType.OUT);
        parameter.setType(JavaType.CURSOR);
        return parameter;
    }
}
