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

class DeleteQueryBuilderTest {

    private static final String EMPLOYEE = Employee.class.getName();

    private static String normalize(String hql) {
        return hql.trim().replaceAll("\\s+", " ");
    }

    @Test
    void buildProducesADeleteWithNoFromKeyword() {
        WMQueryInfo queryInfo = new DeleteQueryBuilder(Employee.class)
            .withFilterCondition("id", 5L)
            .build();

        assertEquals("delete " + EMPLOYEE + " where id = :wm_filter_id",
            normalize(queryInfo.getQuery()));
    }

    @Test
    void withNoFilterConditionsProducesAnUnconditionalDelete() {
        // The spec expects a delete without a where to be rejected; the builder builds it and it deletes every
        // row. WMGenericTemporalDaoImpl.delete(periodClause, filter) with a blank filter reaches this, deleting every
        // row in the period.
        WMQueryInfo queryInfo = new DeleteQueryBuilder(Employee.class).build();

        assertEquals("delete " + EMPLOYEE, normalize(queryInfo.getQuery()));
    }

    @Test
    void periodClauseUsesThePortionOfWordingLikeUpdate() {
        Date asOf = new Date(1_700_000_000_000L);
        WMQueryInfo queryInfo = new DeleteQueryBuilder(Employee.class)
            .withPeriodClause(new AsOfClause(TableTemporal.TemporalType.SYSTEM, asOf))
            .build();

        assertEquals("delete " + EMPLOYEE + " for portion of system_time as of :wm_system_time_as_of_timestamp",
            normalize(queryInfo.getQuery()));
    }
}
