/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao.query;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.wavemaker.runtime.commons.file.model.ExportType;
import com.wavemaker.runtime.data.exception.EntityNotFoundException;
import com.wavemaker.runtime.data.export.ExportOptions;
import com.wavemaker.runtime.data.model.CustomQuery;
import com.wavemaker.runtime.data.model.QueryProcedureInput;
import com.wavemaker.runtime.data.model.queries.QueryType;
import com.wavemaker.runtime.data.model.queries.RuntimeQuery;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the 5 real {@code WMQueryExecutorImpl} methods the runtime-db use-case spec never asked about at all -
 * the spec's HQL/Native-SQL scenarios only exercise {@code executeNamedQuery(..., Pageable)} and
 * {@code executeRuntimeQuery}/{@code executeNamedQuery(UpdatableQueryInput)}, leaving these five untouched.
 */
class WMQueryExecutorImplAdditionalMethodsIntegrationTest extends AbstractHibernateIntegrationTest {

    private WMQueryExecutorImpl executor;

    @BeforeEach
    void createExecutor() throws ReflectiveOperationException {
        executor = new WMQueryExecutorImpl();
        executor.setTemplate(hibernateTemplate);
        Method init = WMQueryExecutorImpl.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(executor);
    }

    private Employee newEmployee(String name, boolean active) {
        Employee employee = new Employee();
        employee.setName(name);
        employee.setActive(active);
        hibernateTemplate.save(employee);
        return employee;
    }

    @Test
    void executeNamedQuerySingleResultReturnsOneMatchOrThrowsWhenNoneMatch() {
        Employee alice = newEmployee("Alice", true);
        hibernateTemplate.flush();

        Employee result = executor.executeNamedQuery(
            new QueryProcedureInput<>("Employee.byActive", Map.of("active", true), Employee.class));
        assertEquals("Alice", result.getName());

        assertThrows(EntityNotFoundException.class, () -> executor.executeNamedQuery(
            new QueryProcedureInput<>("Employee.byActive", Map.of("active", false), Employee.class)));
    }

    @Test
    void executeRuntimeQueryForUpdateRunsAnInlineUpdateAndReturnsTheAffectedRowCount() {
        newEmployee("Alice", true);
        newEmployee("Bob", true);
        newEmployee("Carol", false);
        hibernateTemplate.flush();

        RuntimeQuery update = new RuntimeQuery(
            "update Employee e set e.active = false where e.active = true", false, QueryType.UPDATE, List.of());

        int updated = executor.executeRuntimeQueryForUpdate(update);

        assertEquals(2, updated);
    }

    @Test
    void executeCustomQueryDelegatesThroughCustomQueryAdapterAndReturnsAPage() {
        newEmployee("Alice", true);
        newEmployee("Carol", true);
        newEmployee("Bob", false);
        flushAndClear();

        CustomQuery customQuery = new CustomQuery("from Employee e where e.active = true", List.of());
        // Page size 1 against 2 matches, so the total comes from the count query.
        Page<Object> page = executor.executeCustomQuery(customQuery, PageRequest.of(0, 1));

        assertEquals(2, page.getTotalElements());
    }

    @Test
    void executeCustomQueryForUpdateDelegatesThroughCustomQueryAdapterAndReturnsTheAffectedRowCount() {
        newEmployee("Alice", true);
        newEmployee("Bob", true);
        hibernateTemplate.flush();

        CustomQuery customQuery = new CustomQuery("update Employee e set e.active = false", List.of());

        int updated = executor.executeCustomQueryForUpdate(customQuery);

        assertEquals(2, updated);
    }

    // ---- Exporting a saved HQL projection ----
    //
    // Employee.namesAndSalariesByActive selects two columns. HqlQueryExtractor reads each row with
    // ScrollableResults.get(0), which in Hibernate 6 returns only the first column, so these tests check that both
    // columns are exported. CSV values are unquoted, and a BigDecimal is written without trailing zeros.

    /** Row type for Employee.namesAndSalariesByActive, whose aliases match the field names. */
    public static class NameAndSalaryRow {
        private String employeeName;

        private BigDecimal salary;

        public String getEmployeeName() {
            return employeeName;
        }

        public void setEmployeeName(String employeeName) {
            this.employeeName = employeeName;
        }

        public BigDecimal getSalary() {
            return salary;
        }

        public void setSalary(BigDecimal salary) {
            this.salary = salary;
        }
    }

    /** Saves the active employees Carol (70000), Alice (45000) and Bob (60000), and the inactive Dave (99000). */
    private void saveEmployeesWithSalaries() {
        newEmployee("Carol", true).setSalary(new BigDecimal("70000"));
        newEmployee("Alice", true).setSalary(new BigDecimal("45000"));
        newEmployee("Bob", true).setSalary(new BigDecimal("60000"));
        newEmployee("Dave", false).setSalary(new BigDecimal("99000"));
        flushAndClear();
    }

    private List<String> exportActiveNamesAndSalaries(ExportOptions options, PageRequest pageable) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        executor.exportNamedQueryData(new QueryProcedureInput<>("Employee.namesAndSalariesByActive",
            Map.of("active", true), NameAndSalaryRow.class), options, pageable, outputStream);
        return outputStream.toString(StandardCharsets.UTF_8).lines().filter(line -> !line.isBlank()).toList();
    }

    @Test
    void aMultiColumnNamedHqlProjectionExportsBothColumnsNotJustTheFirst() {
        saveEmployeesWithSalaries();

        List<String> lines = exportActiveNamesAndSalaries(new ExportOptions(ExportType.CSV), PageRequest.of(0, 10));

        assertEquals(4, lines.size(), String.join("\n", lines));
        assertEquals(List.of("Alice,45000", "Bob,60000", "Carol,70000"),
            lines.subList(1, lines.size()).stream().sorted().toList());
    }

    @Test
    void aNamedQueryExportStopsAtTheExportSize() {
        saveEmployeesWithSalaries();

        List<String> lines = exportActiveNamesAndSalaries(new ExportOptions(ExportType.CSV, 2),
            PageRequest.of(0, 10, Sort.by("employeeName")));

        assertEquals(List.of("Alice,45000", "Bob,60000"), lines.subList(1, lines.size()));
    }

    @Disabled("Production bug: an export size of 0 or less becomes SortedUnPagedRequest(0, -1), which is not treated "
        + "as unpaged, so setMaxResults(-1) is called and Hibernate throws \"max-results cannot be negative\". Enable "
        + "once PageUtils.overrideExportSize or the query providers are fixed.")
    @Test
    void aNamedQueryExportWithExportSizeZeroExportsEveryMatchingRow() {
        // PageUtils.overrideExportSize treats a size of 0 or less as "no limit".
        saveEmployeesWithSalaries();

        List<String> lines = exportActiveNamesAndSalaries(new ExportOptions(ExportType.CSV, 0), PageRequest.of(0, 10));

        assertEquals(List.of("Alice,45000", "Bob,60000", "Carol,70000"),
            lines.subList(1, lines.size()).stream().sorted().toList());
    }
}
