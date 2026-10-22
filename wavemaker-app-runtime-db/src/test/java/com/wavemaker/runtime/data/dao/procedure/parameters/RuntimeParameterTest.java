/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao.procedure.parameters;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.commons.variable.VariableType;
import com.wavemaker.runtime.data.model.JavaType;
import com.wavemaker.runtime.data.model.procedures.ProcedureParameter;
import com.wavemaker.runtime.data.model.procedures.ProcedureParameterType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link RuntimeParameter} resolves its value from the request-time params map for a plain ({@link
 * VariableType#PROMPT}) parameter, or from a {@code VariableValueProvider} (SERVER/APP_ENVIRONMENT variables) for
 * a variable-backed one. The variable-backed path needs a live Spring {@code WMAppContext} (looks up the
 * provider bean via {@code WMAppContext.getInstance()}), which nothing in this harness sets up, so it is out of
 * scope for this pass - only the {@code PROMPT} path is covered.
 */
class RuntimeParameterTest {

    @Test
    void getValueResolvesFromParamsMapForAPromptParameter() {
        ProcedureParameter parameter = new ProcedureParameter();
        parameter.setName("x");
        parameter.setType(JavaType.INTEGER);
        parameter.setParameterType(ProcedureParameterType.IN);
        parameter.setVariableType(VariableType.PROMPT);

        RuntimeParameter runtimeParameter = new RuntimeParameter(parameter, Map.of("x", 21));

        assertEquals(21, runtimeParameter.getValue());
        assertSame(parameter, runtimeParameter.getParameter());
    }

    @Test
    void getValueIsNullWhenParamsMapHasNoEntryForTheParameter() {
        ProcedureParameter parameter = new ProcedureParameter();
        parameter.setName("x");
        parameter.setType(JavaType.INTEGER);
        parameter.setParameterType(ProcedureParameterType.IN);
        parameter.setVariableType(VariableType.PROMPT);

        RuntimeParameter runtimeParameter = new RuntimeParameter(parameter, Map.of());

        assertNull(runtimeParameter.getValue());
    }
}
