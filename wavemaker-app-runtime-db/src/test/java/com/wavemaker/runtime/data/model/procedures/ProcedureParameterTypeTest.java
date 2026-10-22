/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.model.procedures;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives {@code NativeProcedureExecutor.configureParameters}'s IN/OUT parameter binding (see
 * {@code NativeProcedureExecutorIntegrationTest}) - IN_OUT is both, IN is in-only, OUT is out-only.
 */
class ProcedureParameterTypeTest {

    @Test
    void inIsInParamOnly() {
        assertTrue(ProcedureParameterType.IN.isInParam());
        assertFalse(ProcedureParameterType.IN.isOutParam());
    }

    @Test
    void outIsOutParamOnly() {
        assertFalse(ProcedureParameterType.OUT.isInParam());
        assertTrue(ProcedureParameterType.OUT.isOutParam());
    }

    @Test
    void inOutIsBoth() {
        assertTrue(ProcedureParameterType.IN_OUT.isInParam());
        assertTrue(ProcedureParameterType.IN_OUT.isOutParam());
    }

    @Test
    void fromDbResolvesCaseInsensitivelyByDbKeyword() {
        assertEquals(ProcedureParameterType.IN, ProcedureParameterType.fromDB("in"));
        assertEquals(ProcedureParameterType.IN, ProcedureParameterType.fromDB("IN"));
        assertEquals(ProcedureParameterType.OUT, ProcedureParameterType.fromDB("Out"));
        // Note the DB keyword is "INOUT" (no underscore) even though the enum constant is IN_OUT.
        assertEquals(ProcedureParameterType.IN_OUT, ProcedureParameterType.fromDB("inout"));
    }

    @Test
    void fromDbForAnUnrecognizedKeywordReturnsNull() {
        assertNull(ProcedureParameterType.fromDB("INOUTBUT_NOT_REALLY"));
    }
}
