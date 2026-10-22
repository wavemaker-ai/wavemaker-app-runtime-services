/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.transform;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers use-case-spec scenario "transform-void-for-dml". A DML (UPDATE/DELETE) query has no result-set columns
 * to transform, so both hooks just return {@code null} outright rather than attempting to read a tuple/map that
 * was never produced - no {@code ClassCastException} or result-set iteration is possible here.
 */
class VoidTransformerTest {

    private final VoidTransformer transformer = new VoidTransformer();

    @Test
    void transformTupleReturnsNullRegardlessOfInput() {
        assertNull(transformer.transformTuple(new Object[] {1L, "ignored"}, new String[] {"id", "name"}));
    }

    @Test
    void transformFromMapReturnsNullRegardlessOfInput() {
        assertNull(transformer.transformFromMap(Map.of("id", 1L)));
    }
}
