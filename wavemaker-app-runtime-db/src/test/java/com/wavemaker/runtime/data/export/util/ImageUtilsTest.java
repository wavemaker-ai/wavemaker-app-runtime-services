/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.export.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real Apache POI objects throughout (an in-memory {@link XSSFWorkbook}, no mocks needed) - this is adapted
 * straight from {@code org.apache.poi.ss.examples.AddDimensionedImage}, so the meaningful thing to verify is
 * that a picture actually gets attached to the sheet, and that a too-narrow column/row gets resized to fit it.
 */
class ImageUtilsTest {

    private static final byte[] FAKE_IMAGE_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 1, 2, 3};

    @Test
    void addImageToSheetAttachesOnePictureToTheWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);

            ImageUtils.addImageToSheet(FAKE_IMAGE_BYTES, cell);

            assertEquals(1, workbook.getAllPictures().size());
            assertEquals(1, countShapes(sheet.createDrawingPatriarch()));
        }
    }

    @Test
    void addImageToSheetWidensATooNarrowColumnToFitTheRequiredImageWidth() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            sheet.setColumnWidth(0, 100); // deliberately much narrower than the 20mm the image needs
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            int originalWidth = sheet.getColumnWidth(0);

            ImageUtils.addImageToSheet(FAKE_IMAGE_BYTES, cell);

            assertTrue(sheet.getColumnWidth(0) > originalWidth,
                "column should have been widened to fit the image, was " + originalWidth + " now " + sheet.getColumnWidth(0));
        }
    }

    @Test
    void addImageToSheetIncreasesATooShortRowsHeightToFitTheRequiredImageHeight() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            Row row = sheet.createRow(0);
            row.setHeightInPoints(5f); // deliberately much shorter than the 30mm the image needs
            Cell cell = row.createCell(0);
            float originalHeight = row.getHeightInPoints();

            ImageUtils.addImageToSheet(FAKE_IMAGE_BYTES, cell);

            assertTrue(row.getHeightInPoints() > originalHeight,
                "row should have been made taller to fit the image, was " + originalHeight + " now " + row.getHeightInPoints());
        }
    }

    private static int countShapes(Drawing<?> drawing) {
        int count = 0;
        for (Object ignored : drawing) {
            count++;
        }
        return count;
    }
}
