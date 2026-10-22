/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.wavemaker.runtime.data.annotations.ColumnAlias;
import com.wavemaker.runtime.data.dialect.WMHSQLDialect;
import com.wavemaker.runtime.data.transform.AliasToEntityLinkedHashMapTransformer;
import com.wavemaker.runtime.data.transform.Transformers;
import com.wavemaker.runtime.data.transform.WMResultTransformer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QueryHelperTest {

    // ---- getCountQuery ----

    @Test
    void getCountQueryForHqlWithoutGroupByOrOrderBy() {
        assertEquals("select count(*) from Employee e where e.active = true",
            QueryHelper.getCountQuery("from Employee e where e.active = true", false));
    }

    @Test
    void getCountQueryForHqlWithSelectClauseBeforeFrom() {
        assertEquals("select count(*)  from Employee e",
            QueryHelper.getCountQuery("select e from Employee e", false));
    }

    @Test
    void getCountQueryForHqlStripsTrailingOrderBy() {
        assertEquals("select count(*) from Employee e",
            QueryHelper.getCountQuery("from Employee e order by e.name", false));
    }

    @Test
    void getCountQueryReturnsNullWhenGroupByPresent() {
        assertNull(QueryHelper.getCountQuery("select e.department, count(*) from Employee e group by e.department", false));
    }

    @Test
    void getCountQueryForNativeSqlWrapsInASubquery() {
        assertEquals("select count(*) from (select * from employee_table) wmTempTable",
            QueryHelper.getCountQuery("select * from employee_table", true));
    }

    // ---- applySortingForHqlQuery (plain property names, no alias mapping) ----

    @Test
    void applySortingForHqlQueryAppendsOrderByForEachSortField() {
        String result = QueryHelper.applySortingForHqlQuery("from Employee e",
            Sort.by(Sort.Order.asc("name"), Sort.Order.desc("salary")));

        assertEquals("from Employee e order by name ASC,salary DESC", result);
    }

    @Test
    void applySortingForHqlQueryWithUnsortedSortIsUnchanged() {
        assertEquals("from Employee e", QueryHelper.applySortingForHqlQuery("from Employee e", Sort.unsorted()));
    }

    @Test
    void applySortingForHqlQueryWithNullSortIsUnchanged() {
        assertEquals("from Employee e", QueryHelper.applySortingForHqlQuery("from Employee e", null));
    }

    // ---- applySortingForHqlQuery (with a WMResultTransformer's field->alias mapping) ----

    @Test
    void applySortingForHqlQueryWithTransformerMapsFieldNameToItsColumnAlias() {
        WMResultTransformer transformer = Transformers.aliasToMappedClass(DeptSummary.class);

        // Sorting is expressed against the DTO's field name ("headcount"), but the generated clause must use
        // the @ColumnAlias-declared alias ("headcount_alias") that the query actually selects under.
        String result = QueryHelper.applySortingForHqlQuery("select dept as deptName, count(*) as headcount_alias from Employee",
            Sort.by("headcount"), transformer);

        assertEquals("select dept as deptName, count(*) as headcount_alias from Employee order by headcount_alias ASC", result);
    }

    /** Public no-arg constructor + JavaBean getters/setters required for reflective population by the transformer. */
    public static class DeptSummary {
        private String deptName;

        @ColumnAlias("headcount_alias")
        private Integer headcount;

        public String getDeptName() {
            return deptName;
        }

        public void setDeptName(String deptName) {
            this.deptName = deptName;
        }

        public Integer getHeadcount() {
            return headcount;
        }

        public void setHeadcount(Integer headcount) {
            this.headcount = headcount;
        }
    }

    @Test
    void applySortingForHqlQueryWithTransformerAndNoFieldAliasMappingPassesFieldNameThrough() {
        String result = QueryHelper.applySortingForHqlQuery("from Employee e", Sort.by("name"),
            AliasToEntityLinkedHashMapTransformer.INSTANCE);

        assertEquals("from Employee e order by name ASC", result);
    }

    // ---- applySortingForNativeQuery ----

    @Test
    void applySortingForNativeQueryWrapsInSubqueryAndDialectQuotesTheSortColumn() {
        // WMHSQLDialect's quote characters are a single space (see WMHSQLDialectTest), so a backtick-quoted
        // identifier round-trips through Dialect.quote() as " name " rather than a real quoting character.
        String result = QueryHelper.applySortingForNativeQuery("select * from employee_table",
            Sort.by("name"), AliasToEntityLinkedHashMapTransformer.INSTANCE, new WMHSQLDialect());

        assertEquals("select * from (select * from employee_table) wmTempTable order by  name  ASC", result);
    }

    @Test
    void applySortingForNativeQueryWithUnsortedSortIsUnchanged() {
        String result = QueryHelper.applySortingForNativeQuery("select * from employee_table",
            Sort.unsorted(), AliasToEntityLinkedHashMapTransformer.INSTANCE, new WMHSQLDialect());

        assertEquals("select * from employee_table", result);
    }
}
