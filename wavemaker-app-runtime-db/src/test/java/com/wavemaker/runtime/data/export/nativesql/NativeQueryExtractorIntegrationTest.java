/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.export.nativesql;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.ScrollableResults;
import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.export.util.DataSourceExporterUtil;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.entity.Employee;
import com.wavemaker.runtime.data.transform.AliasToEntityLinkedHashMapTransformer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code NamedQueryExporterCallback} feeds a {@link NativeQueryExtractor} from a real JDBC {@link ResultSet}
 * pulled out of a Hibernate {@link ScrollableResults} via {@code DataSourceExporterUtil.constructResultSet} - a
 * reflection hack reaching into {@code org.hibernate.internal.AbstractScrollableResults}. Hibernate 6+
 * restructured {@code ScrollableResults} (removing that internal method), so this is exactly the kind of thing
 * expected to break during the Hibernate 7 migration - this test locks down that it still works on 5.6.15.
 */
class NativeQueryExtractorIntegrationTest extends AbstractHibernateIntegrationTest {

    @Test
    void extractsRowsFromARealResultSetBehindAScrollableResults() throws Exception {
        Employee employee = new Employee();
        employee.setName("Alice");
        employee.setSalary(new BigDecimal("50000"));
        hibernateTemplate.save(employee);
        hibernateTemplate.flush();

        Object row = hibernateTemplate.execute(session -> {
            // Both aliases are double-quoted to pin an exact, database-independent result-set label - an
            // unquoted reference gets folded to each database's own default case (HSQLDB upper-cases it,
            // Postgres lower-cases it), which would make the row keys below differ depending on which
            // database the suite happens to be running against.
            ScrollableResults scrollableResults = session
                .createNativeQuery("select name as \"name\", salary as \"salary\" from Employee")
                .scroll();
            ResultSet resultSet = DataSourceExporterUtil.constructResultSet(scrollableResults);

            NativeQueryExtractor extractor =
                new NativeQueryExtractor(resultSet, AliasToEntityLinkedHashMapTransformer.INSTANCE);
            try {
                assertTrue(extractor.next());
                assertTrue(extractor.isFirstRow());
                Object currentRow = extractor.getCurrentRow();
                assertFalse(extractor.next());
                return currentRow;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(row instanceof Map);
        Map<?, ?> rowMap = (Map<?, ?>) row;
        assertEquals("Alice", rowMap.get("name"));
        assertEquals(0, new BigDecimal("50000").compareTo((BigDecimal) rowMap.get("salary")));
    }

    @Test
    void rowKeysCurrentlyComeInHashMapOrderNotSelectOrder() {
        // NativeQueryExtractor collects each row into a HashMap before the transformer sees it, so the
        // LinkedHashMap transformer has no select order left to keep. No visible effect was found: export headers
        // come from the export field list, not from this order.
        Employee employee = new Employee();
        employee.setName("Alice");
        employee.setSalary(BigDecimal.ONE);
        hibernateTemplate.save(employee);
        hibernateTemplate.flush();
        List<String> selectOrder = List.of("z_name", "a_salary", "m_active", "b_id");

        Map<?, ?> row = (Map<?, ?>) hibernateTemplate.execute(session -> {
            ScrollableResults scrollableResults = session.createNativeQuery(
                "select name as \"z_name\", salary as \"a_salary\", active as \"m_active\", id as \"b_id\" from Employee")
                .scroll();
            NativeQueryExtractor extractor = new NativeQueryExtractor(
                DataSourceExporterUtil.constructResultSet(scrollableResults), AliasToEntityLinkedHashMapTransformer.INSTANCE);
            try {
                extractor.next();
                return extractor.getCurrentRow();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals(Set.copyOf(selectOrder), row.keySet());
        assertNotEquals(selectOrder, List.copyOf(row.keySet()));
    }
}
