/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao.callbacks;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.hibernate.Session;
import org.hibernate.type.BasicType;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import com.wavemaker.runtime.commons.file.model.ExportType;
import com.wavemaker.runtime.data.annotations.ColumnAlias;
import com.wavemaker.runtime.data.dao.query.providers.ParametersProvider;
import com.wavemaker.runtime.data.dao.query.providers.SessionBackedQueryProvider;
import com.wavemaker.runtime.data.export.ExportOptions;
import com.wavemaker.runtime.data.export.FieldInfo;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link NamedQueryExporterCallback} picks its extraction strategy (HQL vs native) purely from whether the
 * resolved {@code Query} instance happens to be a {@code NativeQuery} at runtime - both branches need their own
 * test since nothing else about the calling code differs.
 *
 * <p>The native-path test exports into {@link EmployeeNameRow} (a flat, {@code @ColumnAlias}-annotated DTO,
 * the {@code @ColumnAlias} mapping a native column to a differently named field), not {@code Employee} itself - a first attempt
 * using the full entity as the response type produced a garbled CSV walking into {@code LocalDate}'s internal
 * reflection fields instead of real columns, because a native query hydrating a full {@code @Entity} type
 * collides with the export path's own result transformer. See {@code Employee.native.byActive}'s javadoc for
 * the same finding from the production side.
 */
class NamedQueryExporterCallbackTest extends AbstractHibernateIntegrationTest {

    private static final ParametersProvider ACTIVE_TRUE_PROVIDER = new ParametersProvider() {
        @Override
        public Object getValue(final Session session, final String name) {
            return "active".equals(name) ? Boolean.TRUE : null;
        }

        @Override
        public Optional<BasicType<?>> getType(final Session session, final String name) {
            return Optional.empty();
        }
    };

    private void seedEmployees() {
        hibernateTemplate.persist(employee("ActiveOne", true));
        hibernateTemplate.persist(employee("ActiveTwo", true));
        hibernateTemplate.persist(employee("InactiveOne", false));
        hibernateTemplate.flush();
    }

    private static Employee employee(String name, boolean active) {
        Employee employee = new Employee();
        employee.setName(name);
        employee.setSalary(BigDecimal.ONE);
        employee.setActive(active);
        return employee;
    }

    @Test
    void exportsViaTheHqlExtractionPathWhenTheResolvedQueryIsPlainHql() throws Exception {
        seedEmployees();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        NamedQueryExporterCallback<Employee> callback = new NamedQueryExporterCallback<>(
            new SessionBackedQueryProvider<>("Employee.byActive", Employee.class),
            ACTIVE_TRUE_PROVIDER, PageRequest.of(0, 10),
            nameOnly(), outputStream, Employee.class);

        hibernateTemplate.execute(callback::apply);

        List<String> lines = outputStream.toString(StandardCharsets.UTF_8).lines().toList();
        assertEquals("Name", lines.get(0));
        assertEquals(List.of("ActiveOne", "ActiveTwo"), lines.subList(1, lines.size()).stream().sorted().toList());
    }

    @Test
    void exportsViaTheNativeExtractionPathWhenTheResolvedQueryIsANamedNativeQuery() throws Exception {
        seedEmployees();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        NamedQueryExporterCallback<EmployeeNameRow> callback = new NamedQueryExporterCallback<>(
            new SessionBackedQueryProvider<>("Employee.native.byActive", EmployeeNameRow.class),
            ACTIVE_TRUE_PROVIDER, PageRequest.of(0, 10),
            new ExportOptions(ExportType.CSV), outputStream, EmployeeNameRow.class);

        hibernateTemplate.execute(callback::apply);

        // A header line, then one "name,salary" line per active employee. Values are unquoted and the salary
        // (1.00 in the column) is written without trailing zeros.
        List<String> lines = outputStream.toString(StandardCharsets.UTF_8).lines()
            .filter(line -> !line.isBlank()).toList();
        assertEquals(3, lines.size(), String.join("\n", lines));
        assertEquals(List.of("ActiveOne,1", "ActiveTwo,1"), lines.subList(1, lines.size()).stream().sorted().toList());
    }

    public static class EmployeeNameRow {
        // Matches Employee.native.byActive's double-quoted, lower-case aliases exactly - @ColumnAlias's lookup
        // is case-sensitive against the result-set label, so the aliases are quoted to keep their case on every database.
        @ColumnAlias("emp_name")
        private String name;
        @ColumnAlias("salary")
        private BigDecimal salary;

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public BigDecimal getSalary() {
            return salary;
        }

        public void setSalary(final BigDecimal salary) {
            this.salary = salary;
        }
    }

    /**
     * Exports only the name. With no field selection every Employee field is exported, which expands hireDate
     * into its internal fields (see DataExporterRelationshipIntegrationTest).
     */
    private static ExportOptions nameOnly() {
        ExportOptions options = new ExportOptions(ExportType.CSV);
        options.setFields(List.of(new FieldInfo("name")));
        return options;
    }
}
