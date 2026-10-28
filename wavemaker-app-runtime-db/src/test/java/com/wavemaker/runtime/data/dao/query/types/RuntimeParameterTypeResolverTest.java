/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao.query.types;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hibernate.type.BasicType;
import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.dao.query.types.wmql.WMQLDefaultTypeHelper;
import com.wavemaker.runtime.data.dao.query.types.wmql.WMQLSqlServerTypeHelper;
import com.wavemaker.runtime.data.filter.WMQueryParamInfo;
import com.wavemaker.runtime.data.model.JavaType;
import com.wavemaker.runtime.data.model.queries.QueryParameter;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Needs a real Hibernate {@code TypeHelper} (from a live {@code SessionFactory}) to resolve a Java class name to
 * a Hibernate {@code Type} - hence an integration test rather than a plain unit test.
 */
class RuntimeParameterTypeResolverTest extends AbstractHibernateIntegrationTest {

    @Test
    void resolvesTypesFromRuntimeQueryParametersByName() {
        List<QueryParameter> parameters = List.of(
            new QueryParameter("name", JavaType.STRING, false, null),
            new QueryParameter("age", JavaType.INTEGER, false, null));

        RuntimeParameterTypeResolver resolver =
            new RuntimeParameterTypeResolver(parameters, sessionFactory.getTypeConfiguration());

        assertEquals(String.class, resolver.resolveType("name").map(BasicType::getJavaType).orElseThrow());
        assertEquals(Integer.class, resolver.resolveType("age").map(BasicType::getJavaType).orElseThrow());
        assertTrue(resolver.resolveType("missing").isEmpty());
    }

    @Test
    void resolvesTypesFromWMQueryParamInfoMap() {
        Map<String, WMQueryParamInfo> parameters = Map.of(
            "name", new WMQueryParamInfo("Alice", JavaType.STRING),
            "untyped", new WMQueryParamInfo("no-type-here"));

        RuntimeParameterTypeResolver resolver =
            new RuntimeParameterTypeResolver(parameters, sessionFactory.getTypeConfiguration(), new WMQLDefaultTypeHelper());

        assertEquals(String.class, resolver.resolveType("name").map(BasicType::getJavaType).orElseThrow());
        // A parameter with no declared JavaType is skipped entirely, not defaulted to something.
        assertTrue(resolver.resolveType("untyped").isEmpty());
    }

    @Test
    void wmqlTypeHelperAliasIsAppliedBeforeResolvingType() {
        Map<String, WMQueryParamInfo> parameters = Map.of("t", new WMQueryParamInfo("10:15:30", JavaType.TIME));

        RuntimeParameterTypeResolver resolver = new RuntimeParameterTypeResolver(
            parameters, sessionFactory.getTypeConfiguration(), new WMQLSqlServerTypeHelper());

        // WMQLSqlServerTypeHelper aliases TIME to STRING - the resolved Hibernate Type should reflect that,
        // not a TIME type.
        Optional<BasicType<?>> resolved = resolver.resolveType("t");
        assertEquals(String.class, resolved.map(BasicType::getJavaType).orElseThrow());
    }
}
