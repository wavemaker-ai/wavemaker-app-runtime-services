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

class FromToClauseTest {

    @Test
    void generatesAFromToClauseWithDistinctFromAndToParamNames() {
        Date from = new Date(1_600_000_000_000L);
        Date to = new Date(1_700_000_000_000L);

        WMQueryInfo queryInfo = new FromToClause(TableTemporal.TemporalType.SYSTEM, from, to).asWMQueryClause();

        assertEquals("system_time from :wm_system_time_from_timestamp to :wm_system_time_to_timestamp",
            queryInfo.getQuery());
        assertEquals(from, queryInfo.getParameters().get("wm_system_time_from_timestamp").getValue());
        assertEquals(to, queryInfo.getParameters().get("wm_system_time_to_timestamp").getValue());
    }

    @Test
    void fromToAndBetweenUseDifferentParamNamesForTheUpperBound() {
        // FromToClause names its second param "..._to_timestamp"; BetweenClause (see BetweenClauseTest) names
        // its "..._and_timestamp" - different enough that mixing them up would silently produce an
        // unresolvable HQL named parameter rather than a compile error.
        Date to = new Date(1_700_000_000_000L);

        WMQueryInfo queryInfo = new FromToClause(TableTemporal.TemporalType.SYSTEM, new Date(0), to).asWMQueryClause();

        assertEquals(to, queryInfo.getParameters().get("wm_system_time_to_timestamp").getValue());
    }
}
