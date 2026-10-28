/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.converters;

import java.sql.Blob;

import org.hibernate.engine.jdbc.proxy.BlobProxy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Covers use-case-spec scenario "conv-blob-to-bytes". A real {@code java.sql.Blob} can be built in-memory via
 * {@code org.hibernate.engine.jdbc.proxy.BlobProxy.generateProxy(byte[])} - no live JDBC connection/session needed to
 * exercise the unwrap-to-byte[] path, since {@code BlobJavaTypeDescriptor.unwrap} reads the blob's stream
 * directly rather than depending on {@code WrapperOptions}.
 */
class BlobTypeConverterTest {

    private final BlobTypeConverter converter = new BlobTypeConverter();

    @Test
    void toDbValueConvertsARealBlobToItsFullByteArrayContentAndDoesNotReturnTheBlobItself() {
        byte[] original = {1, 2, 3, 4, 5};
        Blob blob = BlobProxy.generateProxy(original);

        Object result = converter.toDbValue(blob, byte[].class);

        assertFalse(result instanceof Blob);
        assertArrayEquals(original, (byte[]) result);
    }

    @Test
    void fromDbValueWithNonBlobValueIsUnchanged() {
        byte[] bytes = {1, 2, 3};

        assertSame(bytes, converter.fromDbValue(bytes));
    }

    @Test
    void toDbValueWithNonBlobValueIsUnchanged() {
        byte[] bytes = {1, 2, 3};

        assertSame(bytes, converter.toDbValue(bytes, byte[].class));
    }

    @Test
    void fromDbValueReadsABlobIntoItsFullByteArray() {
        // The spec's "conv-blob-to-bytes": a Blob read from the database is returned as its content, not the Blob.
        byte[] original = {1, 2, 3, 4, 5};

        assertArrayEquals(original, (byte[]) converter.fromDbValue(BlobProxy.generateProxy(original)));
    }
}
