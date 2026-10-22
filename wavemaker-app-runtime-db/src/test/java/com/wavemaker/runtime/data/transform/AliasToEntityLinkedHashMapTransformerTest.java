/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.transform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers use-case-spec scenarios "transform-linkedhashmap-key-order" and "transform-null-column-present".
 */
class AliasToEntityLinkedHashMapTransformerTest {

    private final AliasToEntityLinkedHashMapTransformer transformer = AliasToEntityLinkedHashMapTransformer.INSTANCE;

    @Test
    void transformTupleProducesALinkedHashMapWithKeysInTheSameOrderAsTheSelectClause() {
        String[] aliases = {"id", "name", "salary"};
        Object[] tuple = {1L, "Alice", java.math.BigDecimal.TEN};

        Object result = transformer.transformTuple(tuple, aliases);

        assertInstanceOf(LinkedHashMap.class, result);
        List<String> keysInOrder = new ArrayList<>(((Map<String, Object>) result).keySet());
        assertEquals(List.of("id", "name", "salary"), keysInOrder);
    }

    @Test
    void transformTupleKeepsTheColumnKeyPresentEvenWhenItsValueIsNull() {
        String[] aliases = {"id", "name"};
        Object[] tuple = {1L, null};

        Map<String, Object> result = (Map<String, Object>) transformer.transformTuple(tuple, aliases);

        assertTrue(result.containsKey("name"));
        assertNull(result.get("name"));
    }

    @Test
    void columnsWithoutAnAliasAreKeyedByTheirPosition() {
        // An HQL select without "as" aliases gives Hibernate 5 no alias names at all.
        Object result = transformer.transformTuple(new Object[]{1L, "Alice"}, null);

        assertEquals(Map.of("0", 1L, "1", "Alice"), result);
    }
}
