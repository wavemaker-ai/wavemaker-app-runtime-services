/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.periods;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;

import com.wavemaker.commons.InvalidInputException;
import com.wavemaker.runtime.data.annotations.TableTemporal;
import com.wavemaker.runtime.data.filter.WMQueryInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Parses the small "as_of &lt;ts&gt; / from_to &lt;ts1&gt; &lt;ts2&gt; / between &lt;ts1&gt; &lt;ts2&gt;" mini
 * grammar (e.g. from a REST query param) into the matching {@link PeriodClause} implementation.
 */
class PeriodClauseWrapperTest {

    @Test
    void asOfDelegatesToAsOfClause() {
        WMQueryInfo queryInfo = new PeriodClauseWrapper(TableTemporal.TemporalType.SYSTEM, "as_of 1700000000000")
            .asWMQueryClause();

        assertEquals("system_time as of :wm_system_time_as_of_timestamp", queryInfo.getQuery());
        assertEquals(new Timestamp(1_700_000_000_000L), queryInfo.getParameters().get("wm_system_time_as_of_timestamp").getValue());
    }

    @Test
    void fromToDelegatesToFromToClause() {
        WMQueryInfo queryInfo = new PeriodClauseWrapper(TableTemporal.TemporalType.SYSTEM, "from_to 1600000000000 1700000000000")
            .asWMQueryClause();

        assertEquals("system_time from :wm_system_time_from_timestamp to :wm_system_time_to_timestamp", queryInfo.getQuery());
    }

    @Test
    void betweenDelegatesToBetweenClause() {
        WMQueryInfo queryInfo = new PeriodClauseWrapper(TableTemporal.TemporalType.SYSTEM, "between 1600000000000 1700000000000")
            .asWMQueryClause();

        assertEquals("system_time between :wm_system_time_from_timestamp and :wm_system_time_and_timestamp", queryInfo.getQuery());
    }

    @Test
    void periodTypeKeywordIsCaseInsensitive() {
        WMQueryInfo queryInfo = new PeriodClauseWrapper(TableTemporal.TemporalType.SYSTEM, "AS_OF 1700000000000")
            .asWMQueryClause();

        assertEquals("system_time as of :wm_system_time_as_of_timestamp", queryInfo.getQuery());
    }

    @Test
    void unknownPeriodTypeThrowsInvalidInputException() {
        assertThrows(InvalidInputException.class,
            () -> new PeriodClauseWrapper(TableTemporal.TemporalType.SYSTEM, "since 1700000000000"));
    }

    @Test
    void asOfWithWrongArgumentCountThrowsInvalidInputException() {
        assertThrows(InvalidInputException.class,
            () -> new PeriodClauseWrapper(TableTemporal.TemporalType.SYSTEM, "as_of 1700000000000 1800000000000"));
    }

    @Test
    void betweenWithWrongArgumentCountThrowsInvalidInputException() {
        assertThrows(InvalidInputException.class,
            () -> new PeriodClauseWrapper(TableTemporal.TemporalType.SYSTEM, "between 1700000000000"));
    }
}
