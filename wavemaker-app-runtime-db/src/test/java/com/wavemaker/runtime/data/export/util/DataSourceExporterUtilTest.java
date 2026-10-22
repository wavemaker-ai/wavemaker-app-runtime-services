/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.export.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.export.ExportCellStyles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@code setCellValue} is what every exported cell (see {@code ExportBuilder.fillData}) actually goes through -
 * it dispatches on the value's {@code JavaType} (numeric types get {@code CellType.NUMERIC} via a double
 * conversion, dates get a date-formatted style, everything else falls back to a plain string).
 */
class DataSourceExporterUtilTest {

    private final XSSFWorkbook workbook = new XSSFWorkbook();
    private final ExportCellStyles cellStyles = new ExportCellStyles(workbook);

    private Cell newCell() {
        Sheet sheet = workbook.createSheet();
        Row row = sheet.createRow(0);
        return row.createCell(0);
    }

    @Test
    void stringDataFallsBackToPlainStringCellValue() {
        Cell cell = newCell();

        DataSourceExporterUtil.setCellValue("Alice", cell, cellStyles);

        assertEquals("Alice", cell.getStringCellValue());
    }

    @Test
    void integerDataBecomesANumericCellViaDoubleConversion() {
        Cell cell = newCell();

        DataSourceExporterUtil.setCellValue(42, cell, cellStyles);

        assertEquals(CellType.NUMERIC, cell.getCellType());
        assertEquals(42.0, cell.getNumericCellValue());
    }

    @Test
    void sqlDateGetsTheDateCellStyleApplied() {
        Cell cell = newCell();

        DataSourceExporterUtil.setCellValue(new java.sql.Date(0), cell, cellStyles);

        assertEquals(cellStyles.getDateCellStyle(), cell.getCellStyle());
    }

    @Test
    void nullDataLeavesTheCellBlankWithoutThrowing() {
        Cell cell = newCell();

        DataSourceExporterUtil.setCellValue(null, cell, cellStyles);

        assertNotNull(cell.getCellStyle());
        assertEquals(CellType.BLANK, cell.getCellType());
    }
}
