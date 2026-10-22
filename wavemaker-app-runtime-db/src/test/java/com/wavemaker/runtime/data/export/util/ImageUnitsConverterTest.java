/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.export.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageUnitsConverterTest {

    @Test
    void pixelToWidthUnitsAndBackRoundTripsApproximately() {
        short widthUnits = ImageUnitsConverter.pixel2WidthUnits(100);
        int pixelsBack = ImageUnitsConverter.widthUnits2Pixel(widthUnits);

        // The conversion is lossy (integer division + a lookup-table offset correction), so this is a
        // regression check on the current rounding behavior, not a claim that it's exactly invertible.
        assertEquals(100, pixelsBack);
    }

    @Test
    void millimetresToWidthUnitsAndBackRoundTripsApproximately() {
        int widthUnits = ImageUnitsConverter.millimetres2WidthUnits(50.0);
        double millimetresBack = ImageUnitsConverter.widthUnits2Millimetres((short) widthUnits);

        assertEquals(50.0, millimetresBack, 1.0);
    }

    @Test
    void clientAnchorDetailExposesConstructorArguments() {
        ImageUnitsConverter.ClientAnchorDetail detail = new ImageUnitsConverter.ClientAnchorDetail(1, 2, 3);

        assertEquals(1, detail.getFromIndex());
        assertEquals(2, detail.getToIndex());
        assertEquals(3, detail.getInset());
    }
}
