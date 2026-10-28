/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.export;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import com.wavemaker.commons.WMRuntimeException;
import com.wavemaker.runtime.commons.file.model.ExportType;
import com.wavemaker.runtime.data.export.hqlquery.HqlQueryExtractor;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.entity.Employee;
import com.wavemaker.runtime.data.support.entity.TypedRecord;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers use-case-spec scenarios "export-hql-to-csv", "export-hql-to-excel", "export-field-selection",
 * "export-large-result-streaming", "export-expression-field" and "export-empty-result" for {@link DataExporter}.
 *
 * <p>One spec scenario is deliberately NOT here:
 * <ul>
 *   <li>"export-native-column-alias-headers" - the spec assumes {@code @ColumnAlias} drives *export column
 *   headers* via an {@code ExportField.getHeaderName()} method. Neither exists: {@code ExportField} only has
 *   {@code getDisplayName()}, and export headers actually come from {@code FieldInfo.getHeader()} (see
 *   "export-field-selection" below) - a completely separate mechanism from export. {@code @ColumnAlias}'s real
 *   job is mapping a native-query result column to a Java field name during row transformation, which is
 *   verified against a real Hibernate session in {@code dao/callbacks/NamedQueryExporterCallbackTest}.</li>
 * </ul>
 */
class DataExporterIntegrationTest extends AbstractHibernateIntegrationTest {

    private HqlQueryExtractor extractorOver(String hql) {
        Query<Employee> query = currentSession().createQuery(hql, Employee.class);
        return new HqlQueryExtractor(query.scroll());
    }

    private void persistEmployees(int count) {
        for (int i = 0; i < count; i++) {
            Employee employee = new Employee();
            employee.setName("Employee" + i);
            employee.setSalary(new BigDecimal("1000"));
            employee.setActive(true);
            currentSession().persist(employee);
        }
        currentSession().flush();
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

    @Test
    void exportsHqlResultsToCsvWithAHeaderRowAndOneDataRowPerResult() throws IOException {
        persistEmployees(2);
        ExportOptions options = optionsWithFields(ExportType.CSV, "name", "salary");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataExporter.export(extractorOver("from Employee e order by e.id"), options, Employee.class, outputStream);

        assertEquals(List.of("Name,Salary", "Employee0,1000", "Employee1,1000"), csvLines(outputStream));
    }

    @Test
    void exportsHqlResultsToExcelWithCorrectlyTypedCells() throws Exception {
        Employee employee = new Employee();
        employee.setName("Alice");
        employee.setSalary(new BigDecimal("50000"));
        employee.setHireDate(LocalDate.of(2024, 3, 15));
        currentSession().persist(employee);
        currentSession().flush();

        ExportOptions options = optionsWithFields(ExportType.EXCEL, "name", "salary", "hireDate");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataExporter.export(extractorOver("from Employee e"), options, Employee.class, outputStream);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(outputStream.toByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            assertEquals("Name", headerRow.getCell(0).getStringCellValue());
            assertEquals("Salary", headerRow.getCell(1).getStringCellValue());

            Row dataRow = sheet.getRow(1);
            Cell nameCell = dataRow.getCell(0);
            Cell salaryCell = dataRow.getCell(1);
            assertEquals(CellType.STRING, nameCell.getCellType());
            assertEquals("Alice", nameCell.getStringCellValue());
            assertEquals(CellType.NUMERIC, salaryCell.getCellType());
            assertEquals(50000d, salaryCell.getNumericCellValue());
            // The spec expects a date-formatted cell. Only java.util.Date values get one; a LocalDate (like
            // LocalDateTime and Timestamp) is written as its text.
            Cell hireDateCell = dataRow.getCell(2);
            assertEquals(CellType.STRING, hireDateCell.getCellType());
            assertEquals("2024-03-15", hireDateCell.getStringCellValue());
        }
    }

    @Test
    void exportWithASelectedFieldSubsetOnlyIncludesThoseFieldsInTheSpecifiedOrder() throws IOException {
        Employee employee = new Employee();
        employee.setName("Bob");
        employee.setSalary(new BigDecimal("1000"));
        employee.setActive(true);
        currentSession().persist(employee);
        currentSession().flush();

        // "All" fields would be name/salary/active - the selection below only asks for one of them.
        ExportOptions options = optionsWithFields(ExportType.CSV, "name");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataExporter.export(extractorOver("from Employee e"), options, Employee.class, outputStream);

        String[] lines = outputStream.toString(StandardCharsets.UTF_8).strip().split("\r?\n");
        assertEquals("Name", lines[0]);
        assertEquals("Bob", lines[1]);
    }

    @Test
    void exportOfManyRowsWritesEveryRow() throws IOException {
        // Rows are read one at a time from a ScrollableResults cursor and written to a streaming (SXSSF) workbook,
        // which keeps only a window of rows in memory, so an Excel export streams. A CSV export does not: the
        // finished workbook is written to a temp file and loaded back fully to convert it. This only checks that
        // 500 rows all arrive; it does not measure memory.
        persistEmployees(500);

        ExportOptions options = optionsWithFields(ExportType.CSV, "name");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataExporter.export(extractorOver("from Employee e order by e.id"), options, Employee.class, outputStream);

        assertEquals(501, csvLines(outputStream).size()); // header + 500 data rows
    }

    @Test
    void exportWithAnExpressionFieldEvaluatesAComputedColumnPerRowAfterTheBaseColumns() throws IOException {
        Employee employee = new Employee();
        employee.setName("Carol");
        employee.setSalary(new BigDecimal("2000"));
        currentSession().persist(employee);
        currentSession().flush();

        ExportOptions options = new ExportOptions(ExportType.CSV);
        FieldInfo nameField = new FieldInfo("name");
        FieldInfo computedField = new FieldInfo();
        computedField.setExpression("${name} earns ${salary}");
        computedField.setHeader("Summary");
        options.setFields(List.of(nameField, computedField));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataExporter.export(extractorOver("from Employee e"), options, Employee.class, outputStream);

        String[] lines = outputStream.toString(StandardCharsets.UTF_8).strip().split("\r?\n");
        assertEquals("Name,Summary", lines[0]);
        // FreeMarker's ${salary} renders a BigDecimal with locale-based thousands grouping ("2,000"), not a
        // plain "2000" - and since that comma collides with the CSV delimiter, the CSV writer correctly quotes
        // the whole field. Both behaviors are real and verified, not assumptions.
        assertEquals("Carol,\"Carol earns 2,000\"", lines[1]);
    }

    @Test
    void exportOfAnEmptyResultProducesAHeaderOnlyFileWithNoExceptionAndNonZeroSize() throws IOException {
        ExportOptions options = optionsWithFields(ExportType.CSV, "name");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        DataExporter.export(extractorOver("from Employee e"), options, Employee.class, outputStream);

        assertTrue(outputStream.size() > 0);
        String[] lines = outputStream.toString(StandardCharsets.UTF_8).strip().split("\r?\n");
        assertEquals(1, lines.length);
        assertEquals("Name", lines[0]);
    }

    @Test
    void aBlobIsEmbeddedAsAPictureInExcelAndLeftEmptyInCsv() throws Exception {
        TypedRecord record = new TypedRecord();
        record.setText("x");
        record.setPayload(new byte[]{1, 2, 3});
        currentSession().persist(record);
        currentSession().flush();

        ByteArrayOutputStream excel = new ByteArrayOutputStream();
        DataExporter.export(extractorOver("from TypedRecord", TypedRecord.class),
            optionsWithFields(ExportType.EXCEL, "text", "payload"), TypedRecord.class, excel);
        ByteArrayOutputStream csv = new ByteArrayOutputStream();
        DataExporter.export(extractorOver("from TypedRecord", TypedRecord.class),
            optionsWithFields(ExportType.CSV, "text", "payload"), TypedRecord.class, csv);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(excel.toByteArray()))) {
            assertEquals(1, workbook.getAllPictures().size());
            assertEquals(CellType.BLANK, workbook.getSheetAt(0).getRow(1).getCell(1).getCellType());
        }
        assertEquals(List.of("Text,Payload", "x,"), csvLines(csv));
    }

    @Test
    void exportingAllFieldsWithADateValueCurrentlyCrashes() {
        // Bug: with no field selection every field is exported, and a LocalDate is expanded like a relationship
        // into its internal fields (see DataExporterRelationshipIntegrationTest). Reading the first of those,
        // serialVersionUID, has no getter, so a row whose date is set fails. Enum fields hit the same problem.
        Employee employee = new Employee();
        employee.setName("Alice");
        employee.setHireDate(LocalDate.of(2024, 3, 15));
        currentSession().persist(employee);
        currentSession().flush();

        WMRuntimeException failure = assertThrows(WMRuntimeException.class, () -> DataExporter.export(
            extractorOver("from Employee e"), new ExportOptions(ExportType.CSV), Employee.class,
            new ByteArrayOutputStream()));
        assertInstanceOf(NullPointerException.class, ExceptionUtils.getRootCause(failure));
    }

    @Test
    void aLargeNumberIsCurrentlyWrittenInScientificNotationInCsv() throws IOException {
        // Bug: numbers are written to the workbook as a double, and the CSV conversion formats them with Excel's
        // "General" format, which switches to scientific notation past 11 characters, losing the digits.
        Employee employee = new Employee();
        employee.setName("Alice");
        employee.setSalary(new BigDecimal("123456789012.34"));
        currentSession().persist(employee);
        currentSession().flush();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataExporter.export(extractorOver("from Employee e"), optionsWithFields(ExportType.CSV, "salary"),
            Employee.class, outputStream);

        assertEquals(List.of("Salary", "1.23457E+11"), csvLines(outputStream));
    }

    @Test
    void csvTrimsLeadingAndTrailingSpacesFromValues() throws IOException {
        // Excel keeps the spaces; the CSV conversion trims every value.
        Employee employee = new Employee();
        employee.setName("  Alice  ");
        currentSession().persist(employee);
        currentSession().flush();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataExporter.export(extractorOver("from Employee e"), optionsWithFields(ExportType.CSV, "name"),
            Employee.class, outputStream);

        assertEquals(List.of("Name", "Alice"), csvLines(outputStream));
    }

    private HqlQueryExtractor extractorOver(String hql, Class<?> entityClass) {
        return new HqlQueryExtractor(currentSession().createQuery(hql, entityClass).scroll());
    }

    private static List<String> csvLines(ByteArrayOutputStream outputStream) {
        return List.of(outputStream.toString(StandardCharsets.UTF_8).split("\r?\n"));
    }
}
