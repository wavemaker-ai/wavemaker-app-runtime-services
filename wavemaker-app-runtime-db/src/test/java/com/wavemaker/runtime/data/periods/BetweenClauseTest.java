/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.periods;

import java.util.Date;

import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.annotations.TableTemporal;
import com.wavemaker.runtime.data.filter.WMQueryInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BetweenClauseTest {

    @Test
    void generatesABetweenClauseWithDistinctFromAndToParamNames() {
        Date from = new Date(1_600_000_000_000L);
        Date to = new Date(1_700_000_000_000L);

        WMQueryInfo queryInfo = new BetweenClause(TableTemporal.TemporalType.SYSTEM, from, to).asWMQueryClause();

        assertEquals("system_time between :wm_system_time_from_timestamp and :wm_system_time_and_timestamp",
            queryInfo.getQuery());
        assertEquals(from, queryInfo.getParameters().get("wm_system_time_from_timestamp").getValue());
        assertEquals(to, queryInfo.getParameters().get("wm_system_time_and_timestamp").getValue());
    }

    @Test
    void applicationTimeUsesTheBusinessTimeHqlKeyword() {
        Date from = new Date(1_600_000_000_000L);
        Date to = new Date(1_700_000_000_000L);

        WMQueryInfo queryInfo = new BetweenClause(TableTemporal.TemporalType.APPLICATION, from, to).asWMQueryClause();

        assertEquals("business_time between :wm_business_time_from_timestamp and :wm_business_time_and_timestamp",
            queryInfo.getQuery());
    }
}
