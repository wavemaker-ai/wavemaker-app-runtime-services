/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao.callbacks;

import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.hibernate.Session;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.commons.variable.VariableType;
import com.wavemaker.runtime.data.dao.procedure.parameters.ResolvableParam;
import com.wavemaker.runtime.data.dao.procedure.parameters.RuntimeParameter;
import com.wavemaker.runtime.data.model.CustomProcedure;
import com.wavemaker.runtime.data.model.CustomProcedureParam;
import com.wavemaker.runtime.data.model.JavaType;
import com.wavemaker.runtime.data.model.procedures.ProcedureParameter;
import com.wavemaker.runtime.data.model.procedures.ProcedureParameterType;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers scenario "proc-legacy-executor" from the runtime-db use-case specification:
 * {@link LegacyNativeProcedureExecutor} is the older JDBC-only procedure execution path (predates
 * {@link NativeProcedureExecutor}, which is covered directly in {@code NativeProcedureExecutorIntegrationTest}).
 * Its OUT-parameter result shape is structurally equivalent - a singleton list wrapping a
 * {@code Map<paramName, value>} - for the non-cursor, non-result-set case.
 *
 * <p><b>A real bug found and worked around below:</b> {@code executeNativeJDBCCall} does
 * {@code try (Connection conn = ((SessionImpl) session).connection())}, which explicitly closes the raw JDBC
 * connection when the call returns. That is only safe if the executor owns that connection exclusively. Called
 * the normal way - through {@code hibernateTemplate}, sharing the connection this test's own ambient
 * {@code AbstractHibernateIntegrationTest} transaction is bound to - it closes the connection out from under
 * that transaction, and the following {@code @AfterEach} rollback then fails with
 * {@code SQLNonTransientConnectionException: connection does not exist}. Confirmed directly: the test method's
 * own assertions pass every time, but the harness's teardown then breaks, failing the test regardless. Worked
 * around by giving this one test a completely independent {@link Session} (not sharing the ambient transaction's
 * connection), which is exactly what a real caller would need to do too, in practice, to use this legacy path
 * safely from within any wider Spring-managed transaction.
 */
class LegacyNativeProcedureExecutorIntegrationTest extends AbstractHibernateIntegrationTest {

    @BeforeAll
    static void createStoredProcedure() {
        hibernateTemplate.execute(session -> {
            session.doWork((java.sql.Connection connection) -> {
                try (Statement statement = connection.createStatement()) {
                    if (isPostgres()) {
                        // Postgres PROCEDUREs cannot declare a plain OUT parameter at all - a FUNCTION is the
                        // real Postgres equivalent here (see WMProcedureExecutorImplIntegrationTest).
                        statement.execute(
                            "CREATE FUNCTION double_value_legacy(IN x INTEGER, OUT y INTEGER) "
                                + "LANGUAGE plpgsql AS $$ BEGIN y := x * 2; END; $$");
                    } else {
                        statement.execute(
                            "CREATE PROCEDURE double_value_legacy(IN x INTEGER, OUT y INTEGER) "
                                + "MODIFIES SQL DATA "
                                + "BEGIN ATOMIC "
                                + "SET y = x * 2; "
                                + "END");
                    }
                }
            });
            return null;
        });
    }

    @Test
    void executesAProcedureViaTheLegacyJdbcPathAndReturnsAStructurallyEquivalentResult() {
        CustomProcedureParam inParam = new CustomProcedureParam(
            "x", 21, ProcedureParameterType.IN, Integer.class.getName());
        CustomProcedureParam outParam = new CustomProcedureParam(
            "y", null, ProcedureParameterType.OUT, Integer.class.getName());

        CustomProcedure procedure = new CustomProcedure("{call double_value_legacy(?, ?)}",
            List.of(inParam, outParam));

        // A completely independent session/connection, deliberately NOT the one bound to this test's ambient
        // transaction (see class javadoc) - executeProcedure closes the raw JDBC connection it borrows, which
        // would otherwise take down the harness's own per-test transaction along with it.
        List<Object> result;
        try (Session independentSession = sessionFactory.openSession()) {
            result = LegacyNativeProcedureExecutor.executeProcedure(independentSession, procedure);
        }

        assertEquals(List.of(Map.of("y", 42)), result);
        // The spec: the legacy result is structurally identical to the standard path's for the same call.
        assertEquals(nativeResultOfTheSameCall(), result);
    }

    private static List<Object> nativeResultOfTheSameCall() {
        List<ResolvableParam> params = List.of(
            new RuntimeParameter(integerParam("x", ProcedureParameterType.IN), Map.of("x", 21)),
            new RuntimeParameter(integerParam("y", ProcedureParameterType.OUT), Map.of()));
        return NativeProcedureExecutor.convertToOldResponse(
            NativeProcedureExecutor.execute(currentSession(), "{call double_value_legacy(?, ?)}", params, Map.class));
    }

    private static ProcedureParameter integerParam(String name, ProcedureParameterType parameterType) {
        ProcedureParameter parameter = new ProcedureParameter();
        parameter.setName(name);
        parameter.setType(JavaType.INTEGER);
        parameter.setParameterType(parameterType);
        parameter.setVariableType(VariableType.PROMPT);
        return parameter;
    }
}
