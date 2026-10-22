/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.hql;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.annotations.TableTemporal;
import com.wavemaker.runtime.data.filter.WMQueryInfo;
import com.wavemaker.runtime.data.periods.AsOfClause;
import com.wavemaker.runtime.data.periods.BetweenClause;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link QueryBuilder} is the abstract base shared by {@link SelectQueryBuilder}/{@link UpdateQueryBuilder}/
 * {@link DeleteQueryBuilder} - where-clause and period-clause (temporal "for ... as of/between/from-to")
 * assembly. Exercised through {@link SelectQueryBuilder} since the base class itself can't be instantiated;
 * subclass-specific behavior (the "from"/"portion of" wording difference) is covered in each subclass's own
 * test class.
 * <p>
 * Whitespace in the generated HQL is normalized before comparison - real callers execute this HQL through
 * Hibernate, which does not care about incidental extra spaces, so pinning the exact byte-for-byte spacing down
 * would just make these tests brittle without protecting anything meaningful.
 *
 * <p><b>Spec scenario "builder-join-clause" (a QueryBuilder subclass adding an explicit JOIN) is not
 * testable, because no such feature exists anywhere in this class hierarchy.</b> {@code generateFromClause}
 * (in {@link QueryBuilder}) only ever emits {@code from <EntityClass> } plus an optional period clause - there
 * is no join-building method, no join type, and no join-alias concept anywhere in {@code QueryBuilder}/
 * {@link SelectQueryBuilder}/{@link UpdateQueryBuilder}/{@link DeleteQueryBuilder}. (Nested-property filtering,
 * e.g. {@code department.name}, is a genuinely different mechanism - an implicit-join dot-path handled entirely
 * by Hibernate's own HQL parser, not by anything this builder hierarchy constructs - and is covered separately
 * under the WMQL Filter Parser scenarios, not here.) Verified directly against the production source, not
 * assumed.
 */
class QueryBuilderTest {

    private static final String EMPLOYEE = Employee.class.getName();

    private static String normalize(String hql) {
        return hql.trim().replaceAll("\\s+", " ");
    }

    @Test
    void noFilterOrPeriodClauseProducesJustTheFromClause() {
        WMQueryInfo queryInfo = SelectQueryBuilder.newBuilder(Employee.class).build();

        assertEquals("from " + EMPLOYEE, normalize(queryInfo.getQuery()));
        assertTrue(queryInfo.getParameters().isEmpty());
    }

    @Test
    void singleFilterConditionProducesAWhereClauseWithAWmFilterPrefixedParam() {
        WMQueryInfo queryInfo = SelectQueryBuilder.newBuilder(Employee.class)
            .withFilterCondition("name", "Alice")
            .build();

        assertEquals("from " + EMPLOYEE + " where name = :wm_filter_name",
            normalize(queryInfo.getQuery()));
        assertEquals("Alice", queryInfo.getParameters().get("wm_filter_name").getValue());
    }

    @Test
    void multipleFilterConditionsAreJoinedWithAnd() {
        WMQueryInfo queryInfo = SelectQueryBuilder.newBuilder(Employee.class)
            .withFilterConditions(Map.of("name", "Alice", "active", true))
            .build();

        // The conditions come from a HashMap, so either order is valid.
        assertOneOf(normalize(queryInfo.getQuery()),
            "from " + EMPLOYEE + " where name = :wm_filter_name and active = :wm_filter_active",
            "from " + EMPLOYEE + " where active = :wm_filter_active and name = :wm_filter_name");
        assertEquals("Alice", queryInfo.getParameters().get("wm_filter_name").getValue());
        assertEquals(true, queryInfo.getParameters().get("wm_filter_active").getValue());
    }

    @Test
    void rawFilterStringIsParsedAndAppendedToTheWhereClause() {
        WMQueryInfo queryInfo = SelectQueryBuilder.newBuilder(Employee.class)
            .withFilter("active = true")
            .build();

        assertEquals("from " + EMPLOYEE + " where active = :wm_parsed_param0", normalize(queryInfo.getQuery()));
        assertEquals("true", queryInfo.getParameters().get("wm_parsed_param0").getValue());
    }

    @Test
    void filterConditionsAndRawFilterStringAreCombinedWithAnd() {
        WMQueryInfo queryInfo = SelectQueryBuilder.newBuilder(Employee.class)
            .withFilterCondition("name", "Alice")
            .withFilter("active = true")
            .build();

        assertEquals("from " + EMPLOYEE + " where name = :wm_filter_name and active = :wm_parsed_param0",
            normalize(queryInfo.getQuery()));
        assertEquals(2, queryInfo.getParameters().size());
    }

    @Test
    void aFilterWithOrIsCurrentlyNotBracketedSoItEscapesTheFilterConditions() {
        // Bug: the filter is appended without brackets, so HQL reads this as (name and active) or salary, and a
        // row with any name matches when its salary is over 5. No production caller combines filter conditions
        // with a filter today (the temporal DAO uses one or the other), so this is a trap for future callers.
        WMQueryInfo queryInfo = SelectQueryBuilder.newBuilder(Employee.class)
            .withFilterCondition("name", "Alice")
            .withFilter("active = true or salary > 5")
            .build();

        assertEquals("from " + EMPLOYEE + " where name = :wm_filter_name and active = :wm_parsed_param0"
            + " or salary > :wm_parsed_param1", normalize(queryInfo.getQuery()));
    }

    @Test
    void singlePeriodClauseAppendsAForAsOfSegmentAndMergesItsParameter() {
        Date asOf = new Date(1_700_000_000_000L);
        WMQueryInfo queryInfo = SelectQueryBuilder.newBuilder(Employee.class)
            .withPeriodClause(new AsOfClause(TableTemporal.TemporalType.SYSTEM, asOf))
            .build();

        assertEquals("from " + EMPLOYEE + " for system_time as of :wm_system_time_as_of_timestamp",
            normalize(queryInfo.getQuery()));
        assertEquals(asOf, queryInfo.getParameters().get("wm_system_time_as_of_timestamp").getValue());
    }

    @Test
    void multiplePeriodClausesEachAppendTheirOwnForSegmentInOrder() {
        Date from = new Date(1_600_000_000_000L);
        Date to = new Date(1_700_000_000_000L);
        WMQueryInfo queryInfo = SelectQueryBuilder.newBuilder(Employee.class)
            .withPeriodClauses(List.of(
                new AsOfClause(TableTemporal.TemporalType.SYSTEM, to),
                new BetweenClause(TableTemporal.TemporalType.APPLICATION, from, to)))
            .build();

        // SELECT never uses "portion of"; that wording is UPDATE/DELETE-only (see those test classes).
        assertEquals("from " + EMPLOYEE + " for system_time as of :wm_system_time_as_of_timestamp"
                + " for business_time between :wm_business_time_from_timestamp and :wm_business_time_and_timestamp",
            normalize(queryInfo.getQuery()));
        assertEquals(to, queryInfo.getParameters().get("wm_system_time_as_of_timestamp").getValue());
        assertEquals(from, queryInfo.getParameters().get("wm_business_time_from_timestamp").getValue());
        assertEquals(to, queryInfo.getParameters().get("wm_business_time_and_timestamp").getValue());
    }

    static void assertOneOf(String actual, String... expected) {
        assertTrue(List.of(expected).contains(actual), () -> "got: " + actual);
    }
}
