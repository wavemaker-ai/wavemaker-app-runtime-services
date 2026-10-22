/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao.generators;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.annotations.TableTemporal;
import com.wavemaker.runtime.data.filter.WMQueryInfo;
import com.wavemaker.runtime.data.model.Aggregation;
import com.wavemaker.runtime.data.model.AggregationInfo;
import com.wavemaker.runtime.data.model.AggregationType;
import com.wavemaker.runtime.data.support.entity.Employee;

import static com.wavemaker.runtime.data.dao.generators.SimpleEntitiyQueryGeneratorTest.normalize;
import static com.wavemaker.runtime.data.dao.generators.SimpleEntitiyQueryGeneratorTest.values;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TemporalQueryGenerator}, the one {@link QueryGeneratorDecorator}: it adds an "as of now" period clause to
 * {@code searchByQuery} and {@code getAggregatedValues}, between the FROM and the WHERE, and passes {@code findById}
 * and {@code findBy} through to the plain generator unchanged. The spec expects the period as extra AND conditions
 * on the WHERE; it is a separate {@code for ... as of} clause instead.
 *
 * <p>This only checks the query text. The clause is custom Hibernate syntax that the current Hibernate rejects; see
 * {@code WMGenericTemporalDaoImplIntegrationTest}.
 */
class TemporalQueryGeneratorTest {

    private static final String FROM_EMPLOYEE = "from " + Employee.class.getName();

    private static final String BUSINESS_TIME_PARAM = "wm_business_time_as_of_timestamp";

    private final SimpleEntitiyQueryGenerator<Employee, Long> plainGenerator = new SimpleEntitiyQueryGenerator<>(Employee.class);

    private final TemporalQueryGenerator<Employee, Long> generator =
        new TemporalQueryGenerator<>(plainGenerator, TableTemporal.TemporalType.APPLICATION);

    @Test
    void searchByQueryAddsAnAsOfNowClauseAndKeepsTheFilter() {
        long before = System.currentTimeMillis();
        WMQueryInfo queryInfo = generator.searchByQuery("active = true").build();
        long after = System.currentTimeMillis();

        assertEquals(FROM_EMPLOYEE + " for business_time as of :" + BUSINESS_TIME_PARAM
            + " where active = :wm_parsed_param0", normalize(queryInfo.getQuery()));
        assertEquals("true", queryInfo.getParameters().get("wm_parsed_param0").getValue());
        long asOf = ((Timestamp) queryInfo.getParameters().get(BUSINESS_TIME_PARAM).getValue()).getTime();
        assertTrue(before <= asOf && asOf <= after, "the clause is evaluated at the current time");
    }

    @Test
    void aSystemTemporalTypeUsesSystemTime() {
        WMQueryInfo queryInfo = new TemporalQueryGenerator<>(plainGenerator, TableTemporal.TemporalType.SYSTEM)
            .searchByQuery("active = true")
            .build();

        assertEquals(FROM_EMPLOYEE + " for system_time as of :wm_system_time_as_of_timestamp"
            + " where active = :wm_parsed_param0", normalize(queryInfo.getQuery()));
    }

    @Test
    void theCountQueryCarriesTheSameClause() {
        WMQueryInfo count = generator.searchByQuery("active = true").buildCountQuery().orElseThrow();

        assertEquals("select count(*) " + FROM_EMPLOYEE + " for business_time as of :" + BUSINESS_TIME_PARAM
            + " where active = :wm_parsed_param0", normalize(count.getQuery()));
        assertTrue(count.getParameters().containsKey(BUSINESS_TIME_PARAM));
    }

    @Test
    void getAggregatedValuesAddsTheClauseToo() {
        AggregationInfo aggregationInfo = new AggregationInfo();
        aggregationInfo.setGroupByFields(List.of("name"));
        aggregationInfo.setAggregations(List.of(new Aggregation("id", AggregationType.COUNT, "total")));

        WMQueryInfo queryInfo = generator.getAggregatedValues(aggregationInfo).build();

        assertEquals("select name as name,COUNT(id) as total " + FROM_EMPLOYEE + " for business_time as of :"
            + BUSINESS_TIME_PARAM + " group by name", normalize(queryInfo.getQuery()));
    }

    @Test
    void findByIdAndFindByAreExactlyThePlainGeneratorsQueries() {
        assertSameQuery(plainGenerator.findById(42L).build(), generator.findById(42L).build());
        assertSameQuery(plainGenerator.findBy(Map.of("name", "Alice")).build(),
            generator.findBy(Map.of("name", "Alice")).build());
    }

    private static void assertSameQuery(WMQueryInfo expected, WMQueryInfo actual) {
        assertEquals(expected.getQuery(), actual.getQuery());
        assertEquals(values(expected), values(actual));
    }
}
