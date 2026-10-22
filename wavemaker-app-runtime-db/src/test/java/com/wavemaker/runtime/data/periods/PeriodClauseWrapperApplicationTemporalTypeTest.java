/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.periods;

import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.annotations.TableTemporal;
import com.wavemaker.runtime.data.filter.WMQueryInfo;
import com.wavemaker.runtime.data.support.entity.TemporalEmployee;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Re-covers {@code PeriodClauseWrapperTest} against {@link TableTemporal.TemporalType#APPLICATION} - every test
 * there uses {@code TemporalType.SYSTEM} exclusively, but {@link TemporalEmployee} (this session's own
 * relationship-rich temporal fixture, carrying a one-to-many to {@code TemporalEmployeeAward}) is declared
 * {@code @TableTemporal(value = TemporalType.APPLICATION, ...)} - the one concrete temporal entity in this test
 * suite actually uses the branch nothing here had ever exercised. {@code TemporalType.APPLICATION} overrides
 * {@code asHqlKeyword()} to return {@code "business_time"} instead of the {@code SYSTEM} default's
 * {@code name().toLowerCase() + "_time"} - a real, distinct code path, not just a different string constant.
 */
class PeriodClauseWrapperApplicationTemporalTypeTest {

    @Test
    void asOfUsesTheBusinessTimeKeywordForTheApplicationTemporalType() {
        WMQueryInfo queryInfo = new PeriodClauseWrapper(TableTemporal.TemporalType.APPLICATION, "as_of 1700000000000")
            .asWMQueryClause();

        assertEquals("business_time as of :wm_business_time_as_of_timestamp", queryInfo.getQuery());
        assertEquals(new java.sql.Timestamp(1_700_000_000_000L),
            queryInfo.getParameters().get("wm_business_time_as_of_timestamp").getValue());
    }

    @Test
    void fromToUsesTheBusinessTimeKeywordForTheApplicationTemporalType() {
        WMQueryInfo queryInfo = new PeriodClauseWrapper(
            TableTemporal.TemporalType.APPLICATION, "from_to 1600000000000 1700000000000")
            .asWMQueryClause();

        assertEquals("business_time from :wm_business_time_from_timestamp to :wm_business_time_to_timestamp",
            queryInfo.getQuery());
    }

    @Test
    void betweenUsesTheBusinessTimeKeywordForTheApplicationTemporalType() {
        WMQueryInfo queryInfo = new PeriodClauseWrapper(
            TableTemporal.TemporalType.APPLICATION, "between 1600000000000 1700000000000")
            .asWMQueryClause();

        assertEquals("business_time between :wm_business_time_from_timestamp and :wm_business_time_and_timestamp",
            queryInfo.getQuery());
    }
}
