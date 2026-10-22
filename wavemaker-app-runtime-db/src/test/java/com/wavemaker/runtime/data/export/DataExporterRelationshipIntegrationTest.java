/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.commons.file.model.ExportType;
import com.wavemaker.runtime.data.export.hqlquery.HqlQueryExtractor;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.entity.Department;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Re-covers {@link DataExporter}'s "export-field-selection" and "export-all-fields" scenarios against a
 * relationship property path - every test in {@code DataExporterIntegrationTest} only ever exports flat
 * {@code Employee} fields (name/salary/active). The mechanism exercised here is {@link SimpleFieldValueProvider}'s
 * nested-reflection walk plus {@link ExportOptionsStrategy#includeAllFields}'s one-level-deep auto-discovery -
 * both confirmed by reading their source directly.
 */
class DataExporterRelationshipIntegrationTest extends AbstractHibernateIntegrationTest {

    private HqlQueryExtractor extractorOver(String hql) {
        Query<Employee> query = sessionFactory.getCurrentSession().createQuery(hql, Employee.class);
        return new HqlQueryExtractor(query.scroll());
    }

    private static ExportOptions optionsWithFields(ExportType type, String... fieldNames) {
        ExportOptions options = new ExportOptions(type);
        List<FieldInfo> fields = new java.util.ArrayList<>();
        for (String fieldName : fieldNames) {
            fields.add(new FieldInfo(fieldName));
        }
        options.setFields(fields);
        return options;
    }

    private Department department(String name, String location) {
        Department department = new Department();
        department.setName(name);
        department.setLocation(location);
        sessionFactory.getCurrentSession().persist(department);
        return department;
    }

    @Test
    void exportWithAnExplicitRelationshipFieldResolvesTheNestedPropertyAndCapitalizesEachSegmentOfTheHeader()
        throws IOException {
        Department engineering = department("Engineering", "Building 2");

        Employee employee = new Employee();
        employee.setName("Dave");
        employee.setSalary(new BigDecimal("3000"));
        employee.setDepartment(engineering);
        sessionFactory.getCurrentSession().persist(employee);
        sessionFactory.getCurrentSession().flush();

        ExportOptions options = optionsWithFields(ExportType.CSV, "name", "department.name");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataExporter.export(extractorOver("from Employee e"), options, Employee.class, outputStream);

        String[] lines = outputStream.toString(StandardCharsets.UTF_8).strip().split("\r?\n");
        assertEquals("Name,Department Name", lines[0]);
        assertEquals("Dave,Engineering", lines[1]);
    }

    @Test
    void exportWithNoFieldsExpandsOneLevelOfRelationshipsAndCurrentlyExpandsADateIntoItsInternalFields()
        throws IOException {
        Department engineering = department("Engineering", "Building 2");

        Employee manager = new Employee();
        manager.setName("Eve");
        manager.setSalary(new BigDecimal("9000"));
        manager.setDepartment(engineering);
        sessionFactory.getCurrentSession().persist(manager);

        Employee employee = new Employee();
        employee.setName("Dave");
        employee.setSalary(new BigDecimal("3000"));
        employee.setDepartment(engineering);
        employee.setManager(manager);
        sessionFactory.getCurrentSession().persist(employee);
        sessionFactory.getCurrentSession().flush();

        ExportOptions options = new ExportOptions(ExportType.CSV); // no fields -> includeAllFields(...)
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataExporter.export(extractorOver("from Employee e where e.name = 'Dave'"), options, Employee.class,
            outputStream);

        String header = outputStream.toString(StandardCharsets.UTF_8).split("\r?\n")[0];

        // Employee's own fields, then one level of each non-collection relationship (department, manager, profile,
        // badge). Manager's own relationships and the peers collection are not expanded.
        // Bug: hireDate is a LocalDate, which is not a "known" type, so it is expanded like a relationship into its
        // internal fields (SerialVersionUID, DAYS_PER_CYCLE, ...). A row with a hire date set makes the export
        // crash (see DataExporterIntegrationTest.exportingAllFieldsWithADateValueCurrentlyCrashes).
        assertEquals("Id,Name,Salary,"
            + "HireDate SerialVersionUID,HireDate DAYS_PER_CYCLE,HireDate DAYS_0000_TO_1970,HireDate Year,HireDate Month,HireDate Day,"
            + "Active,Department Id,Department Name,Department Location,Manager Id,Manager Name,Manager Salary,Manager Active,"
            + "Profile EmployeeId,Profile Bio,Profile Address,Badge Id,Badge BadgeNumber", header);
    }

    @Test
    void exportWithAnInvalidNestedRelationshipFieldDegradesToANullColumnRatherThanThrowing() throws IOException {
        // "department" itself resolves fine, but no such property exists on Department -
        // HqlPropertyResolver.findField returns Optional.empty() for the second segment, and
        // ExportOptionsStrategy.generateExportField falls back to an "object -> null" provider rather than
        // throwing - confirmed here against a real relationship path, not just a flat typo.
        Department engineering = department("Engineering", "Building 2");

        Employee employee = new Employee();
        employee.setName("Dave");
        employee.setSalary(new BigDecimal("3000"));
        employee.setDepartment(engineering);
        sessionFactory.getCurrentSession().persist(employee);
        sessionFactory.getCurrentSession().flush();

        ExportOptions options = optionsWithFields(ExportType.CSV, "name", "department.doesNotExist");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataExporter.export(extractorOver("from Employee e"), options, Employee.class, outputStream);

        String[] lines = outputStream.toString(StandardCharsets.UTF_8).strip().split("\r?\n");
        assertEquals("Name,Department DoesNotExist", lines[0]);
        assertEquals("Dave,", lines[1]);
    }
}
