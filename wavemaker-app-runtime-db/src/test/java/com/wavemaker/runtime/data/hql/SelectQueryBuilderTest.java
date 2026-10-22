/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.hql;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.filter.WMQueryInfo;
import com.wavemaker.runtime.data.model.Aggregation;
import com.wavemaker.runtime.data.model.AggregationInfo;
import com.wavemaker.runtime.data.model.AggregationType;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectQueryBuilderTest {

    private static String normalize(String hql) {
        return hql.trim().replaceAll("\\s+", " ");
    }

    @Test
    void withFieldsProjectsExactlyTheGivenFields() {
        WMQueryInfo queryInfo = SelectQueryBuilder.newBuilder(Employee.class)
            .withFields(List.of("name", "salary"))
            .build();

        assertEquals("select name,salary from com.wavemaker.runtime.data.support.entity.Employee",
            normalize(queryInfo.getQuery()));
    }

    @Test
    void withDistinctFieldsProjectsADistinctExpressionWithDotsCleanedFromTheAlias() {
        WMQueryInfo queryInfo = SelectQueryBuilder.newBuilder(Employee.class)
            .withDistinctFields("department.name")
            .build();

        assertEquals("select distinct(department.name) as department$name from com.wavemaker.runtime.data.support.entity.Employee",
            normalize(queryInfo.getQuery()));
    }

    @Test
    void withGroupByFieldsProjectsAndGroupsByTheSameFields() {
        WMQueryInfo queryInfo = SelectQueryBuilder.newBuilder(Employee.class)
            .withGroupByFields(List.of("department.name"))
            .build();

        String query = normalize(queryInfo.getQuery());
        assertEquals("select department.name as department$name from com.wavemaker.runtime.data.support.entity.Employee "
            + "group by department.name", query);
    }

    @Test
    void withAggregationsProjectsTheAggregateSelection() {
        WMQueryInfo queryInfo = SelectQueryBuilder.newBuilder(Employee.class)
            .withAggregations(List.of(new Aggregation("salary", AggregationType.SUM, "totalSalary")))
            .build();

        assertEquals("select SUM(salary) as totalSalary from com.wavemaker.runtime.data.support.entity.Employee",
            normalize(queryInfo.getQuery()));
    }

    @Test
    void withAggregationInfoCombinesGroupByAggregationsAndFilter() {
        AggregationInfo aggregationInfo = new AggregationInfo();
        aggregationInfo.setGroupByFields(List.of("department.name"));
        aggregationInfo.setAggregations(List.of(new Aggregation("salary", AggregationType.SUM, "totalSalary")));

        WMQueryInfo queryInfo = SelectQueryBuilder.newBuilder(Employee.class)
            .withAggregationInfo(aggregationInfo)
            .build();

        String query = normalize(queryInfo.getQuery());
        assertTrue(query.startsWith("select department.name as department$name,SUM(salary) as totalSalary from"));
        assertTrue(query.endsWith("group by department.name"));
    }

    @Test
    void buildCountQueryReturnsACountVariantWhenThereIsNoGroupBy() {
        Optional<WMQueryInfo> countQuery = SelectQueryBuilder.newBuilder(Employee.class)
            .withFilterCondition("active", true)
            .buildCountQuery();

        assertTrue(countQuery.isPresent());
        assertEquals("select count(*) from com.wavemaker.runtime.data.support.entity.Employee where active = :wm_filter_active",
            normalize(countQuery.get().getQuery()));
    }

    @Test
    void buildCountQueryIsAbsentWhenGroupByIsPresent() {
        // HQL does not support "select count(*) ... group by ..." as a meaningful count query, so this is
        // deliberately not generated (WMGenericDaoImpl.getAggregatedValues never asks for a count query either).
        Optional<WMQueryInfo> countQuery = SelectQueryBuilder.newBuilder(Employee.class)
            .withGroupByFields(List.of("department.name"))
            .buildCountQuery();

        assertTrue(countQuery.isEmpty());
    }
}
