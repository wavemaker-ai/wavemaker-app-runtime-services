/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.service;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.commons.variable.VariableType;
import com.wavemaker.runtime.data.dao.procedure.parameters.ResolvableParam;
import com.wavemaker.runtime.data.dao.procedure.parameters.TestParameter;
import com.wavemaker.runtime.data.model.JavaType;
import com.wavemaker.runtime.data.model.ReferenceType;
import com.wavemaker.runtime.data.model.procedures.ProcedureParameter;
import com.wavemaker.runtime.data.model.procedures.ProcedureParameterType;
import com.wavemaker.runtime.data.model.procedures.RuntimeProcedure;
import com.wavemaker.runtime.data.model.queries.QueryType;
import com.wavemaker.runtime.data.model.queries.RuntimeQuery;
import com.wavemaker.runtime.data.model.returns.ReturnProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignTimeServiceUtilsTest {

    @Test
    void selectQueryIsNeverDmlRegardlessOfQueryText() {
        RuntimeQuery query = new RuntimeQuery("delete from Employee", false, QueryType.SELECT, List.of());

        assertFalse(DesignTimeServiceUtils.isDMLOrUpdateQuery(query));
    }

    @Test
    void nonSelectTypeWithADmlStatementIsDml() {
        RuntimeQuery query = new RuntimeQuery("update Employee set active = false", false, QueryType.UPDATE, List.of());

        assertTrue(DesignTimeServiceUtils.isDMLOrUpdateQuery(query));
    }

    @Test
    void getMetaForDmlIsASingleUnnamedIntegerProperty() {
        List<ReturnProperty> meta = DesignTimeServiceUtils.getMetaForDML();

        assertEquals(1, meta.size());
        assertEquals(ReferenceType.PRIMITIVE, meta.get(0).getFieldType().getType());
        assertEquals(Integer.class.getName(), meta.get(0).getFieldType().getTypeRef());
    }

    @Test
    void prepareParametersBuildsOneTestParameterPerProcedureParameterInOrder() {
        ProcedureParameter in = new ProcedureParameter();
        in.setName("x");
        in.setType(JavaType.INTEGER);
        in.setParameterType(ProcedureParameterType.IN);
        in.setVariableType(VariableType.PROMPT);
        in.setTestValue(21);

        RuntimeProcedure procedure = new RuntimeProcedure();
        procedure.setProcedureString("{call double_value(?, ?)}");
        procedure.setParameters(List.of(in));

        List<ResolvableParam> params = DesignTimeServiceUtils.prepareParameters(procedure);

        assertEquals(1, params.size());
        assertInstanceOf(TestParameter.class, params.get(0));
        assertEquals(21, params.get(0).getValue());
    }
}
