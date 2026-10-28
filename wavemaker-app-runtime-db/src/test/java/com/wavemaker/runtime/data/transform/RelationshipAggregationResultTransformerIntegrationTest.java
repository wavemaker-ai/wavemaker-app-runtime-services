/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.transform;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.wavemaker.runtime.data.dao.generators.SimpleEntitiyQueryGenerator;
import com.wavemaker.runtime.data.dao.query.types.wmql.WMQLDefaultTypeHelper;
import com.wavemaker.runtime.data.hql.SelectQueryBuilder;
import com.wavemaker.runtime.data.model.Aggregation;
import com.wavemaker.runtime.data.model.AggregationInfo;
import com.wavemaker.runtime.data.model.AggregationType;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.entity.Department;
import com.wavemaker.runtime.data.support.entity.Employee;
import com.wavemaker.runtime.data.util.HqlQueryHelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Re-covers {@link AliasToEntityLinkedHashMapTransformer} against the real production path that actually feeds it
 * a relationship-path GROUP BY result - every test in {@code AliasToEntityLinkedHashMapTransformerTest} only ever
 * fabricates a flat alias/tuple array directly by hand, never runs a real query through it. {@code
 * SimpleEntitiyQueryGeneratorTest.getAggregatedValuesProjectsGroupByAndAggregationFields} already proved the
 * generated HQL TEXT uses {@code "department.name as department$name"} (a dot can't appear in an HQL select
 * alias) - this class proves that alias convention survives all the way through a real execution and this
 * transformer, which is exactly what {@code WMGenericDaoImpl.getAggregatedValues} relies on in production (it
 * always requests {@code Map.class}, which {@link Transformers#aliasToMappedClassOptional} resolves to this
 * transformer).
 */
class RelationshipAggregationResultTransformerIntegrationTest extends AbstractHibernateIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void aRelationshipPathGroupByAggregateIsKeyedUnderItsDollarSignAliasNotTheDottedPath() {
        Department engineering = new Department();
        engineering.setName("Engineering");
        engineering.setLocation("Building 2");
        hibernateTemplate.persist(engineering);

        Department sales = new Department();
        sales.setName("Sales");
        sales.setLocation("Building 1");
        hibernateTemplate.persist(sales);

        Employee alice = new Employee();
        alice.setName("Alice");
        alice.setSalary(new BigDecimal("50000"));
        alice.setDepartment(engineering);
        hibernateTemplate.persist(alice);

        Employee bob = new Employee();
        bob.setName("Bob");
        bob.setSalary(new BigDecimal("30000"));
        bob.setDepartment(engineering);
        hibernateTemplate.persist(bob);

        Employee carol = new Employee();
        carol.setName("Carol");
        carol.setSalary(new BigDecimal("40000"));
        carol.setDepartment(sales);
        hibernateTemplate.persist(carol);
        hibernateTemplate.flush();

        AggregationInfo aggregationInfo = new AggregationInfo();
        aggregationInfo.setGroupByFields(List.of("department.name"));
        aggregationInfo.setAggregations(List.of(new Aggregation("salary", AggregationType.SUM, "totalSalary")));

        SelectQueryBuilder builder = new SimpleEntitiyQueryGenerator<Employee, Long>(Employee.class)
            .getAggregatedValues(aggregationInfo);

        Page<Map> page = HqlQueryHelper.execute(hibernateTemplate, Map.class, builder, Pageable.unpaged(), new WMQLDefaultTypeHelper());

        List<Map> rows = page.getContent();
        assertEquals(2, rows.size());

        Map<String, Object> engineeringRow = (Map<String, Object>) rows.stream()
            .filter(row -> "Engineering".equals(row.get("department$name")))
            .findFirst().orElseThrow();
        // compareTo, not equals() - the DB's SUM comes back scaled (e.g. "80000.00"), and BigDecimal.equals()
        // treats differing scale as inequality even when the numeric value is the same.
        assertEquals(0, new BigDecimal("80000").compareTo((BigDecimal) engineeringRow.get("totalSalary")));
        // the dotted association path itself is never used as a map key - only the $-escaped form is, confirming
        // the transformer and the generator agree on the same escaping convention end to end.
        assertTrue(rows.stream().noneMatch(row -> row.containsKey("department.name")));
    }
}
