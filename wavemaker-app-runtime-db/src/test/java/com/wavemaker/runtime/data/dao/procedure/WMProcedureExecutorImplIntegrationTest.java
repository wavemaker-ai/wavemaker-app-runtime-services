/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao.procedure;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wavemaker.commons.WMRuntimeException;
import com.wavemaker.runtime.commons.variable.VariableType;
import com.wavemaker.runtime.data.model.JavaType;
import com.wavemaker.runtime.data.model.procedures.ProcedureParameter;
import com.wavemaker.runtime.data.model.procedures.ProcedureParameterType;
import com.wavemaker.runtime.data.model.procedures.RuntimeProcedure;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.TestReflectionUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link WMProcedureExecutorImpl} adds one thing on top of {@link com.wavemaker.runtime.data.dao.callbacks.
 * NativeProcedureExecutor} (covered directly in {@code NativeProcedureExecutorIntegrationTest}): looking up a
 * named procedure's metadata and assembling the {@code RuntimeParameter} list from the request params map. Its
 * {@code init()} loads that metadata from a classpath JSON resource ({@code <serviceId>-procedures.mappings.json}),
 * tested on its own below; the execution tests inject the procedure map directly.
 *
 * <p><b>Spec scenario "proc-design-time-test" (executing a procedure with {@code TestParameter} instances for
 * a design-time preview, returning a {@code DesignServiceResponse}) is not testable at all, anywhere.</b>
 * {@code WMProcedureExecutorImpl.executeNamedProcedure} (above) always wraps parameters as
 * {@link com.wavemaker.runtime.data.dao.procedure.parameters.RuntimeParameter}, never
 * {@link com.wavemaker.runtime.data.dao.procedure.parameters.TestParameter} - that wiring, and the
 * {@code DesignServiceResponse} construction, would live in {@code ProcedureDesignService}/
 * {@code ProcedureDesignServiceImpl}, which are literal 0-byte empty files in this codebase (confirmed via
 * {@code wc -l}) - the same dead-file finding already documented for spec scenario
 * "design-proc-test-out-params" in the Design-time Services test coverage. There is no code path anywhere to
 * test.
 */
class WMProcedureExecutorImplIntegrationTest extends AbstractHibernateIntegrationTest {

    private static final String PROCEDURE_NAME = "doubleValue";

    private WMProcedureExecutorImpl executor;

    @BeforeAll
    static void createStoredProcedure() {
        hibernateTemplate.execute(session -> {
            session.doWork((Connection connection) -> {
                try (Statement statement = connection.createStatement()) {
                    if (isPostgres()) {
                        // Postgres PROCEDUREs cannot declare a plain OUT parameter at all (only IN/INOUT/
                        // VARIADIC) - a FUNCTION is the real Postgres equivalent here, same translation as
                        // postgres-init/03-procedures.sql's double_value.
                        statement.execute(
                            "CREATE FUNCTION double_value(IN x INTEGER, OUT y INTEGER) "
                                + "LANGUAGE plpgsql AS $$ BEGIN y := x * 2; END; $$");
                    } else {
                        statement.execute(
                            "CREATE PROCEDURE double_value(IN x INTEGER, OUT y INTEGER) "
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

    @BeforeEach
    void createExecutor() {
        executor = new WMProcedureExecutorImpl();
        executor.setTemplate(hibernateTemplate);
        executor.setServiceId("test");

        RuntimeProcedure procedure = new RuntimeProcedure();
        procedure.setProcedureString("{call double_value(?, ?)}");
        procedure.setParameters(List.of(param("x", ProcedureParameterType.IN), param("y", ProcedureParameterType.OUT)));

        TestReflectionUtils.setField(executor, "procedureMap", Map.of(PROCEDURE_NAME, procedure));
    }

    private static ProcedureParameter param(String name, ProcedureParameterType parameterType) {
        ProcedureParameter parameter = new ProcedureParameter();
        parameter.setName(name);
        parameter.setType(JavaType.INTEGER);
        parameter.setParameterType(parameterType);
        parameter.setVariableType(VariableType.PROMPT);
        return parameter;
    }

    @Test
    void executeNamedProcedureReturnsResultMappedToRequestedType() {
        Map result = executor.executeNamedProcedure(PROCEDURE_NAME, Map.of("x", 21), Map.class);

        assertEquals(42, result.get("y"));
    }

    @Test
    void deprecatedListOverloadWrapsTheResultMapAsASingletonList() {
        List<Object> result = executor.executeNamedProcedure(PROCEDURE_NAME, Map.of("x", 10));

        assertEquals(List.of(Map.of("y", 20)), result);
    }

    @Test
    void anUnknownProcedureNameFailsWithAWaveMakerException() {
        assertThrows(WMRuntimeException.class,
            () -> executor.executeNamedProcedure("noSuchProcedure", Map.of(), Map.class));
    }

    @Test
    void initLoadsTheServicesProcedureMappingsFile() {
        WMProcedureExecutorImpl svc1Executor = new WMProcedureExecutorImpl();
        svc1Executor.setTemplate(hibernateTemplate);
        svc1Executor.setServiceId("svc1");

        // svc1-procedures.mappings.json in the test resources declares no procedures.
        svc1Executor.init();

        assertThrows(WMRuntimeException.class,
            () -> svc1Executor.executeNamedProcedure(PROCEDURE_NAME, Map.of("x", 1), Map.class));
    }

    @Test
    void initThrowsWhenTheServiceHasNoProcedureMappingsFile() {
        WMProcedureExecutorImpl executorWithoutMappings = new WMProcedureExecutorImpl();
        executorWithoutMappings.setServiceId("noSuchService");

        assertThrows(WMRuntimeException.class, executorWithoutMappings::init);
    }
}
