/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.service;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.model.ReferenceType;
import com.wavemaker.runtime.data.model.returns.ReturnProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code executeInTransaction} needs a live Spring {@code WMAppContext} (see {@code RuntimeParameterTest} for
 * the same constraint elsewhere) - only {@code extractMetaFromResults} is testable in this harness, but it's
 * also the one part with real (recursive) logic: inferring a design-time result's shape from its first row.
 */
class AbstractDesignServiceTest {

    private final AbstractDesignService service = new AbstractDesignService() {
    };

    @Test
    void emptyResultsProduceNoProperties() {
        assertTrue(service.extractMetaFromResults(List.of()).isEmpty());
    }

    @Test
    void inspectsOnlyTheFirstRowToInferPropertyTypes() {
        List<Object> results = List.of(
            Map.of("name", "Alice", "salary", 50000),
            Map.of("name", "Bob")); // a second row with fewer keys - must not affect the inferred shape

        List<ReturnProperty> properties = service.extractMetaFromResults(results);

        assertEquals(2, properties.size());
        assertTrue(properties.stream().allMatch(p -> p.getFieldType().getType() == ReferenceType.PRIMITIVE));
    }

    @Test
    void nullValueInTheFirstRowIsTypedAsPlainObject() {
        List<Object> results = List.of(new java.util.HashMap<String, Object>() {
            {
                put("middleName", null);
            }
        });

        List<ReturnProperty> properties = service.extractMetaFromResults(results);

        assertEquals(1, properties.size());
        assertEquals(Object.class.getName(), properties.get(0).getFieldType().getTypeRef());
    }

    @Test
    void collectionValuedFieldsAreTypedAsCustomListsWithNestedProperties() {
        List<Object> results = List.of(Map.of("awards",
            List.of(Map.of("year", 2020, "name", "Employee of the Year"))));

        List<ReturnProperty> properties = service.extractMetaFromResults(results);

        assertEquals(1, properties.size());
        assertEquals("awards", properties.get(0).getName());
        assertTrue(properties.get(0).getFieldType().isList());
        assertEquals(ReferenceType.CUSTOM, properties.get(0).getFieldType().getType());
        assertEquals(2, properties.get(0).getFieldType().getProperties().size());
    }

    @Test
    void nonMapFirstRowProducesNoProperties() {
        // extractMetaFromResults only recognizes Map-shaped rows ("always returns map", per its own comment) -
        // a scalar/entity-shaped first row is silently treated as having no inferable properties at all.
        assertTrue(service.extractMetaFromResults(List.of("just a string")).isEmpty());
    }
}
