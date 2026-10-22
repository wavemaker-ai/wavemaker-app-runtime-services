/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao.generators;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jakarta.persistence.IdClass;

import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.filter.WMQueryInfo;
import com.wavemaker.runtime.data.hql.SelectQueryBuilder;
import com.wavemaker.runtime.data.model.Aggregation;
import com.wavemaker.runtime.data.model.AggregationInfo;
import com.wavemaker.runtime.data.model.AggregationType;
import com.wavemaker.runtime.data.support.entity.Employee;
import com.wavemaker.runtime.data.support.entity.EmployeeAward;
import com.wavemaker.runtime.data.support.entity.EmployeeAwardId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "Query Generators" scenarios for {@link SimpleEntitiyQueryGenerator}, checking the exact query and parameters
 * it builds. {@code findById} goes through {@link SingleIdentifierStrategy} for a plain {@code @Id} entity and
 * {@link CompositeIdentifierStrategy} for an {@code @IdClass} entity, so its parameters show what each strategy
 * extracted.
 *
 * <p>Unlike the spec's {@code FROM EntityClass e WHERE ...}, the query uses the entity's fully qualified class name
 * and no alias, and the generator returns a {@link SelectQueryBuilder} rather than a string.
 */
class SimpleEntitiyQueryGeneratorTest {

    private static final String FROM_EMPLOYEE = "from " + Employee.class.getName();

    private final SimpleEntitiyQueryGenerator<Employee, Long> generator = new SimpleEntitiyQueryGenerator<>(Employee.class);

    @Test
    void findByIdOnASingleKeyEntityFiltersByTheIdField() {
        WMQueryInfo queryInfo = generator.findById(42L).build();

        assertEquals(FROM_EMPLOYEE + " where id = :wm_filter_id", normalize(queryInfo.getQuery()));
        assertEquals(Map.of("wm_filter_id", 42L), values(queryInfo));
    }

    @Test
    void findByIdOnACompositeKeyEntityFiltersByEveryKeyField() {
        WMQueryInfo queryInfo = new SimpleEntitiyQueryGenerator<EmployeeAward, EmployeeAwardId>(EmployeeAward.class)
            .findById(new EmployeeAwardId(7L, "MVP"))
            .build();

        String query = normalize(queryInfo.getQuery());
        assertTrue(query.equals("from " + EmployeeAward.class.getName()
                + " where employeeId = :wm_filter_employeeId and awardCode = :wm_filter_awardCode")
                || query.equals("from " + EmployeeAward.class.getName()
                + " where awardCode = :wm_filter_awardCode and employeeId = :wm_filter_employeeId"),
            () -> "the key fields come from a HashMap, so either order is valid; got: " + query);
        assertEquals(Map.of("wm_filter_employeeId", 7L, "wm_filter_awardCode", "MVP"), values(queryInfo));
    }

    @Test
    void aCompositeKeyClassWithSerialVersionUidCurrentlyFailsFindById() {
        // Bug: CompositeIdentifierStrategy reads every declared field of the key class, static ones included, and
        // serialVersionUID has no getter, so it fails with a NullPointerException. WaveMaker-generated key classes
        // have no serialVersionUID, so only hand-written ones are affected.
        SimpleEntitiyQueryGenerator<EntityWithSerialVersionUidKey, KeyWithSerialVersionUid> keyGenerator =
            new SimpleEntitiyQueryGenerator<>(EntityWithSerialVersionUidKey.class);

        assertThrows(NullPointerException.class, () -> keyGenerator.findById(new KeyWithSerialVersionUid()));
    }

    @Test
    void findByFiltersByEachGivenField() {
        WMQueryInfo queryInfo = generator.findBy(Map.of("name", "Alice")).build();

        assertEquals(FROM_EMPLOYEE + " where name = :wm_filter_name", normalize(queryInfo.getQuery()));
        assertEquals(Map.of("wm_filter_name", "Alice"), values(queryInfo));
    }

    @Test
    void searchByQueryParsesTheWmqlFilterIntoTheWhereClause() {
        WMQueryInfo queryInfo = generator.searchByQuery("active = true").build();

        assertEquals(FROM_EMPLOYEE + " where active = :wm_parsed_param0", normalize(queryInfo.getQuery()));
        assertEquals(Map.of("wm_parsed_param0", "true"), values(queryInfo));
    }

    @Test
    void searchByQueryWithAnEmptyFilterHasNoWhereClause() {
        WMQueryInfo queryInfo = generator.searchByQuery("").build();

        assertEquals(FROM_EMPLOYEE, normalize(queryInfo.getQuery()));
        assertTrue(queryInfo.getParameters().isEmpty());
    }

    @Test
    void theCountQueryHasTheSameFromAndWhereAsTheSearchQuery() {
        // The generator has no count method of its own; the SelectQueryBuilder it returns builds both queries.
        SelectQueryBuilder builder = generator.searchByQuery("active = true");

        WMQueryInfo search = builder.build();
        WMQueryInfo count = builder.buildCountQuery().orElseThrow();

        assertEquals("select count(*) " + normalize(search.getQuery()), normalize(count.getQuery()));
        assertEquals(values(search), values(count));
    }

    @Test
    void getAggregatedValuesProjectsTheGroupByAndAggregationFieldsAndHasNoCountQuery() {
        AggregationInfo aggregationInfo = new AggregationInfo();
        aggregationInfo.setGroupByFields(List.of("department.name"));
        aggregationInfo.setAggregations(List.of(new Aggregation("salary", AggregationType.SUM, "totalSalary")));

        SelectQueryBuilder builder = generator.getAggregatedValues(aggregationInfo);

        assertEquals("select department.name as department$name,SUM(salary) as totalSalary " + FROM_EMPLOYEE
            + " group by department.name", normalize(builder.build().getQuery()));
        // A grouped query has one row per group, so a single count query is not built for it.
        assertTrue(builder.buildCountQuery().isEmpty());
    }

    @Test
    void aSortIsAppliedToTheGeneratedQueryAsAnOrderByClauseUsingBarePropertyNamesNotAnEntityAlias() {
        // "gen-order-by-clause": neither SimpleEntitiyQueryGenerator nor SelectQueryBuilder has any Sort-aware
        // method at all (verified: no reference to org.springframework.data.domain.Sort anywhere in
        // dao/generators/** or hql/**). The real Sort -> "ORDER BY" translation happens one layer up, in
        // com.wavemaker.runtime.data.dao.util.QueryHelper.applySortingForHqlQuery(String, Sort) - which is
        // exactly how WMGenericDaoImpl.searchByQuery(...) combines a generator's output with a caller's sort in
        // production. And per its actual implementation, the property name is used bare (fieldMapper =
        // `property -> property`) - there is no entity-alias prefixing at all, contradicting the spec's
        // assumption of `ORDER BY e.field DESC`. (A 3-arg overload exists that maps through a
        // WMResultTransformer's column alias for projected/aggregated queries - that is a *result-column* alias,
        // not a table alias either.)
        WMQueryInfo queryInfo = new SimpleEntitiyQueryGenerator<Employee, Long>(Employee.class)
            .searchByQuery("active = true")
            .build();
        String sortedQuery = com.wavemaker.runtime.data.dao.util.QueryHelper.applySortingForHqlQuery(
            queryInfo.getQuery(), org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("hireDate"),
                org.springframework.data.domain.Sort.Order.asc("name")));
        String normalized = normalize(sortedQuery);
        assertTrue(normalized.endsWith("order by hireDate DESC,name ASC"), () -> "got: " + normalized);
    }

    static String normalize(String hql) {
        return hql.trim().replaceAll("\\s+", " ");
    }

    /**
     * The parameters as {@code name -> value}, sorted by name.
     */
    static Map<String, Object> values(WMQueryInfo queryInfo) {
        Map<String, Object> values = new TreeMap<>();
        queryInfo.getParameters().forEach((name, param) -> values.put(name, param.getValue()));
        return values;
    }

    @IdClass(KeyWithSerialVersionUid.class)
    public static class EntityWithSerialVersionUidKey {
    }

    public static class KeyWithSerialVersionUid implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long code;

        public Long getCode() {
            return code;
        }

        public void setCode(Long code) {
            this.code = code;
        }
    }
}
