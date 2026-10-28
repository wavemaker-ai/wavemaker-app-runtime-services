package com.wavemaker.runtime.data.export.hqlquery;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.hibernate.ScrollableResults;
import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.entity.Employee;
import com.wavemaker.runtime.data.transform.AliasToEntityLinkedHashMapTransformer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The export of a native query scrolls it with the tuple transformer of the query and reads the transformed rows, as
 * {@code NamedQueryExporterCallback} does. Hibernate 7 has no {@code ScrollableResults} access to the JDBC result set,
 * which the old native extractor read through reflection.
 */
class NativeQueryScrollExportIntegrationTest extends AbstractHibernateIntegrationTest {

    @Test
    void extractsTransformedRowsFromAScrolledNativeQuery() throws Exception {
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
            ScrollableResults<?> scrollableResults = session
                .createNativeQuery("select name as \"name\", salary as \"salary\" from Employee")
                .setTupleTransformer(AliasToEntityLinkedHashMapTransformer.INSTANCE)
                .scroll();

            HqlQueryExtractor extractor = new HqlQueryExtractor(scrollableResults);
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
    void rowKeysComeInSelectOrder() {
        // The old native extractor collected each row into a HashMap before the transformer saw it, so the select
        // order was lost. The rows now reach the LinkedHashMap transformer directly and keep it.
        Employee employee = new Employee();
        employee.setName("Alice");
        employee.setSalary(BigDecimal.ONE);
        hibernateTemplate.save(employee);
        hibernateTemplate.flush();
        List<String> selectOrder = List.of("z_name", "a_salary", "m_active", "b_id");

        Map<?, ?> row = (Map<?, ?>) hibernateTemplate.execute(session -> {
            ScrollableResults<?> scrollableResults = session.createNativeQuery(
                "select name as \"z_name\", salary as \"a_salary\", active as \"m_active\", id as \"b_id\" from Employee")
                .setTupleTransformer(AliasToEntityLinkedHashMapTransformer.INSTANCE)
                .scroll();
            HqlQueryExtractor extractor = new HqlQueryExtractor(scrollableResults);
            try {
                extractor.next();
                return extractor.getCurrentRow();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals(selectOrder, List.copyOf(row.keySet()));
    }
}
