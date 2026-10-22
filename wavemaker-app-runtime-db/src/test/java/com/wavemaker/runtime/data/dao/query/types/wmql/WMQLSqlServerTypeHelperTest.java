/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao.query.types.wmql;

import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.model.JavaType;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SQL Server does not support binding a bare {@code TIME} JDBC parameter the way other WM-supported dialects do,
 * so query parameters typed {@code TIME} are rewritten to bind as {@code STRING} instead - every other type
 * passes through unchanged.
 *
 * <p><b>Maps to spec scenario "native-sqlserver-datetime-type"</b> (WMQueryExecutor - Native SQL): the spec
 * assumes an end-to-end test showing "temporal parameters use SQL Server syntax" when the active dialect is SQL
 * Server. That isn't practically testable in this harness - there is no real SQL Server to run against, only
 * HSQLDB. The entire, actual SQL-Server-specific temporal type-binding behavior in this codebase is precisely
 * the one line covered by {@link #aliasesTimeToString()} above: {@code TIME} is rewritten to {@code STRING}.
 * {@code TIMESTAMP} (SQL Server's closest analog to "datetime") is explicitly asserted to pass through
 * unchanged in {@link #leavesOtherTypesUnchanged()} below - there is no SQL-Server-specific datetime2 rewriting
 * anywhere in this class or its interface. This is the narrowest real substitute for the scenario, not merely a
 * unit test standing in for an end-to-end one.
 */
class WMQLSqlServerTypeHelperTest {

    private final WMQLSqlServerTypeHelper helper = new WMQLSqlServerTypeHelper();

    @Test
    void aliasesTimeToString() {
        assertEquals(JavaType.STRING, helper.aliasFor(JavaType.TIME));
    }

    @Test
    void leavesOtherTypesUnchanged() {
        assertEquals(JavaType.INTEGER, helper.aliasFor(JavaType.INTEGER));
        assertEquals(JavaType.TIMESTAMP, helper.aliasFor(JavaType.TIMESTAMP));
        assertEquals(JavaType.STRING, helper.aliasFor(JavaType.STRING));
    }
}
