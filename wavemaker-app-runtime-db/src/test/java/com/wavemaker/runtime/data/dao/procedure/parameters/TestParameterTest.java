/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao.procedure.parameters;

import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.model.JavaType;
import com.wavemaker.runtime.data.model.procedures.ProcedureParameter;
import com.wavemaker.runtime.data.model.procedures.ProcedureParameterType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link TestParameter} is Studio's design-time "test run" parameter source - unlike {@link RuntimeParameter}, it
 * never touches the request-time params map, only the parameter definition's stored {@code testValue}.
 */
class TestParameterTest {

    @Test
    void getValueReturnsTheParametersTestValueVerbatim() {
        ProcedureParameter parameter = new ProcedureParameter();
        parameter.setName("x");
        parameter.setType(JavaType.INTEGER);
        parameter.setParameterType(ProcedureParameterType.IN);
        parameter.setTestValue(21);

        TestParameter testParameter = new TestParameter(parameter);

        assertEquals(21, testParameter.getValue());
        assertSame(parameter, testParameter.getParameter());
    }
}
