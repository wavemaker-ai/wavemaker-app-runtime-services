/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.util;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.expression.AttributeType;
import com.wavemaker.runtime.data.expression.QueryFilter;
import com.wavemaker.runtime.data.expression.Type;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code WMGenericDaoImpl.search} runs every {@link QueryFilter} through this before building {@code Criterion}s
 * (see {@code WMGenericDaoImplIntegrationTest}'s search tests) - converting each filter's string/raw attribute
 * value to its declared {@link AttributeType} in place.
 */
class DaoUtilsValidateQueryFiltersTest {

    @Test
    void nullOrEmptyArrayIsANoOp() {
        DaoUtils.validateQueryFilters(null);
        DaoUtils.validateQueryFilters(new QueryFilter[0]);
    }

    @Test
    void scalarValueIsConvertedToItsAttributeType() {
        QueryFilter filter = new QueryFilter("age", "42", Type.EQUALS, AttributeType.INTEGER);

        DaoUtils.validateQueryFilters(new QueryFilter[]{filter});

        assertEquals(42, filter.getAttributeValue());
    }

    @Test
    void collectionValueHasEachElementConvertedAndStaysAList() {
        QueryFilter filter = new QueryFilter("age", List.of("1", "2"), Type.IN, AttributeType.INTEGER);

        DaoUtils.validateQueryFilters(new QueryFilter[]{filter});

        assertEquals(List.of(1, 2), filter.getAttributeValue());
    }

    @Test
    void arrayValueHasEachElementConvertedAndStaysAnArray() {
        QueryFilter filter = new QueryFilter("age", new Object[]{"1", "2"}, Type.BETWEEN, AttributeType.INTEGER);

        DaoUtils.validateQueryFilters(new QueryFilter[]{filter});

        assertArrayEquals(new Object[]{1, 2}, (Object[]) filter.getAttributeValue());
    }

    @Test
    void nullAttributeValueIsSkippedRatherThanNullPointerException() {
        QueryFilter filter = new QueryFilter("age", null, Type.EQUALS, AttributeType.INTEGER);

        DaoUtils.validateQueryFilters(new QueryFilter[]{filter});

        assertNull(filter.getAttributeValue());
    }

    @Test
    void typeNullConditionSkipsConversionEvenWithANonNullValue() {
        // Documents the "|| filterCondition == Type.NULL" branch specifically - a NULL-condition filter's value
        // is never actually used by Type.NULL.criterion(), but validateQueryFilters still special-cases it here
        // rather than trying (and potentially failing) to convert it.
        QueryFilter filter = new QueryFilter("age", "unused", Type.NULL, AttributeType.INTEGER);

        DaoUtils.validateQueryFilters(new QueryFilter[]{filter});

        assertEquals("unused", filter.getAttributeValue());
    }
}
