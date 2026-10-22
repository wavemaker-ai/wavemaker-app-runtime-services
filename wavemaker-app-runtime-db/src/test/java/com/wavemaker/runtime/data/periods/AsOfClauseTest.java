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

/**
 * Covers "period-as-of-predicate" from the runtime-db use-case spec - and the other three {@code periods/}
 * classes ({@link BetweenClauseTest}/{@link FromToClauseTest}/{@link PeriodClauseWrapperTest}) cover the
 * remaining three period-clause scenarios.
 *
 * <p><b>The spec's assumed predicate shape is wrong for every class in this package.</b> It assumes something
 * like {@code e.validFrom <= :asOf AND (e.validTo IS NULL OR e.validTo > :asOf)} - a hand-rolled boolean
 * predicate over application-defined column names. The real classes generate none of that: they emit the
 * SQL:2011 system-versioned-table clause syntax verbatim ({@code "<period> as of :param"},
 * {@code "<period> between :p1 and :p2"}, {@code "<period> from :p1 to :p2"}), where {@code <period>} comes from
 * {@code TableTemporal.TemporalType.asHqlKeyword()} (e.g. {@code system_time}, or {@code business_time} for
 * {@code APPLICATION} - not the more obvious {@code application_time}). This is also the exact HQL fragment
 * confirmed elsewhere in this suite (see {@code dao/WMGenericTemporalDaoImplIntegrationTest}) to not be valid
 * Hibernate 5 HQL grammar at all - a genuine pre-existing bug, independent of this spec mismatch.
 */
class AsOfClauseTest {

    @Test
    void systemTimeUsesTheSystemTimeHqlKeyword() {
        Date timestamp = new Date(1_700_000_000_000L);

        WMQueryInfo queryInfo = new AsOfClause(TableTemporal.TemporalType.SYSTEM, timestamp).asWMQueryClause();

        assertEquals("system_time as of :wm_system_time_as_of_timestamp", queryInfo.getQuery());
        assertEquals(timestamp, queryInfo.getParameters().get("wm_system_time_as_of_timestamp").getValue());
    }

    @Test
    void applicationTimeUsesTheBusinessTimeHqlKeyword() {
        // TemporalType.APPLICATION.asHqlKeyword() overrides the default "<name>_time" pattern to "business_time"
        // rather than "application_time" - easy to get wrong since it doesn't follow the enum's own naming.
        Date timestamp = new Date(1_700_000_000_000L);

        WMQueryInfo queryInfo = new AsOfClause(TableTemporal.TemporalType.APPLICATION, timestamp).asWMQueryClause();

        assertEquals("business_time as of :wm_business_time_as_of_timestamp", queryInfo.getQuery());
        assertEquals(timestamp, queryInfo.getParameters().get("wm_business_time_as_of_timestamp").getValue());
    }
}
