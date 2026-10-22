/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.hql;

import java.util.Date;

import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.annotations.TableTemporal;
import com.wavemaker.runtime.data.filter.WMQueryInfo;
import com.wavemaker.runtime.data.periods.AsOfClause;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateQueryBuilderTest {

    private static final String EMPLOYEE = Employee.class.getName();

    private static String normalize(String hql) {
        return hql.trim().replaceAll("\\s+", " ");
    }

    @Test
    void buildProducesAnUpdateWithNoFromKeyword() {
        WMQueryInfo queryInfo = new UpdateQueryBuilder(Employee.class)
            .withSetter("active", false)
            .build();

        assertEquals("update " + EMPLOYEE + " set active = :wm_setter_active",
            normalize(queryInfo.getQuery()));
        assertEquals(false, queryInfo.getParameters().get("wm_setter_active").getValue());
    }

    @Test
    void multipleSettersAreJoinedWithCommas() {
        WMQueryInfo queryInfo = new UpdateQueryBuilder(Employee.class)
            .withSetter("active", false)
            .withSetter("name", "Deactivated")
            .build();

        // The setters come from a HashMap, so either order is valid.
        QueryBuilderTest.assertOneOf(normalize(queryInfo.getQuery()),
            "update " + EMPLOYEE + " set active = :wm_setter_active, name = :wm_setter_name",
            "update " + EMPLOYEE + " set name = :wm_setter_name, active = :wm_setter_active");
        assertEquals("Deactivated", queryInfo.getParameters().get("wm_setter_name").getValue());
        assertEquals(2, queryInfo.getParameters().size());
    }

    @Test
    void setClauseIsCombinedWithAWhereClauseFromFilterConditions() {
        WMQueryInfo queryInfo = new UpdateQueryBuilder(Employee.class)
            .withSetter("active", false)
            .withFilterCondition("id", 5L)
            .build();

        assertEquals("update " + EMPLOYEE + " set active = :wm_setter_active "
                + "where id = :wm_filter_id",
            normalize(queryInfo.getQuery()));
    }

    @Test
    void periodClauseUsesThePortionOfWordingUnlikeSelect() {
        // Unlike SelectQueryBuilder (see QueryBuilderTest), UPDATE/DELETE use SQL:2011's
        // "FOR PORTION OF <period> ..." syntax rather than plain "FOR <period> ...".
        Date asOf = new Date(1_700_000_000_000L);
        WMQueryInfo queryInfo = new UpdateQueryBuilder(Employee.class)
            .withSetter("active", false)
            .withPeriodClause(new AsOfClause(TableTemporal.TemporalType.SYSTEM, asOf))
            .build();

        assertEquals("update " + EMPLOYEE + " for portion of system_time as of :wm_system_time_as_of_timestamp"
            + " set active = :wm_setter_active", normalize(queryInfo.getQuery()));
    }
}
