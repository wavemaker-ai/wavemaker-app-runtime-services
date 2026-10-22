/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.export.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code ExportBuilder}/{@code DataExporter} build an Excel workbook in memory regardless of the requested
 * export type, then convert it to CSV via this class when {@code ExportType.CSV} was asked for (see
 * {@code WMGenericDaoImplIntegrationTest.exportProducesNonEmptyCsvOutput}).
 */
class CSVConverterUtilTest {

    private static String convert(XSSFWorkbook workbook) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        new CSVConverterUtil(workbook).convert(outputStream);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    @Test
    void plainCellsAreJoinedWithCommasAndNoQuoting() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet();
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Name");
        header.createCell(1).setCellValue("Department");
        Row data = sheet.createRow(1);
        data.createCell(0).setCellValue("Alice");
        data.createCell(1).setCellValue("Engineering");

        String csv = convert(workbook);

        assertEquals("Name,Department\nAlice,Engineering", csv);
    }

    @Test
    void aFieldContainingACommaIsWrappedInQuotes() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet();
        Row row = sheet.createRow(0);
        row.createCell(0).setCellValue("Smith, Jr.");
        row.createCell(1).setCellValue("Engineering");

        String csv = convert(workbook);

        assertEquals("\"Smith, Jr.\",Engineering", csv);
    }

    @Test
    void aFieldContainingAQuoteHasItsQuotesDoubledAndIsWrapped() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet();
        Row row = sheet.createRow(0);
        row.createCell(0).setCellValue("Bob \"The Builder\"");

        String csv = convert(workbook);

        assertEquals("\"Bob \"\"The Builder\"\"\"", csv);
    }
}
