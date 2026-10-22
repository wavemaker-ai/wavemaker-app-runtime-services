/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao.query.providers;

import java.util.List;
import java.util.Optional;

import org.hibernate.Session;
import org.hibernate.type.Type;
import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.model.JavaType;
import com.wavemaker.runtime.data.model.queries.QueryParameter;
import com.wavemaker.runtime.data.model.queries.QueryType;
import com.wavemaker.runtime.data.model.queries.RuntimeQuery;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RuntimeParametersProvider} resolves parameter values from the {@code RuntimeQuery}'s own declared
 * {@code testValue} on each {@link QueryParameter} - not from any externally-supplied value map. That makes it
 * a design-time/"test run" parameter source (Studio's query test-run feature), distinct from
 * {@code AppRuntimeParameterProvider} which resolves against request-time parameter values - worth pinning down
 * since the name alone doesn't make that obvious.
 */
class RuntimeParametersProviderTest extends AbstractHibernateIntegrationTest {

    @Test
    void getValueResolvesFromTheParametersOwnTestValueNotAnExternalMap() {
        QueryParameter nameParam = new QueryParameter("name", JavaType.STRING, false, "Alice");
        RuntimeQuery query = new RuntimeQuery("from Employee where name = :name", false, QueryType.SELECT, List.of(nameParam));
        RuntimeParametersProvider provider = new RuntimeParametersProvider(query);

        Object value = hibernateTemplate.execute((Session session) -> provider.getValue(session, "name"));

        assertEquals("Alice", value);
    }

    @Test
    void getValueForUnknownParameterNameReturnsNull() {
        RuntimeQuery query = new RuntimeQuery("from Employee", false, QueryType.SELECT, List.of());
        RuntimeParametersProvider provider = new RuntimeParametersProvider(query);

        Object value = hibernateTemplate.execute((Session session) -> provider.getValue(session, "missing"));

        assertNull(value);
    }

    @Test
    void getTypeResolvesHibernateTypeForKnownParameter() {
        QueryParameter ageParam = new QueryParameter("age", JavaType.INTEGER, false, 30);
        RuntimeQuery query = new RuntimeQuery("from Employee where age = :age", false, QueryType.SELECT, List.of(ageParam));
        RuntimeParametersProvider provider = new RuntimeParametersProvider(query);

        Optional<Type> type = hibernateTemplate.execute((Session session) -> provider.getType(session, "age"));

        assertEquals(Integer.class, type.map(Type::getReturnedClass).orElseThrow());
    }

    @Test
    void getTypeForUnknownParameterIsEmpty() {
        RuntimeQuery query = new RuntimeQuery("from Employee", false, QueryType.SELECT, List.of());
        RuntimeParametersProvider provider = new RuntimeParametersProvider(query);

        Optional<Type> type = hibernateTemplate.execute((Session session) -> provider.getType(session, "missing"));

        assertTrue(type.isEmpty());
    }
}
