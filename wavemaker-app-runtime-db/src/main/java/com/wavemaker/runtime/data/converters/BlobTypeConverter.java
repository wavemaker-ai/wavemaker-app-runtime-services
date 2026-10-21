/*******************************************************************************
 * Copyright (C) 2022-2023 WaveMaker, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.wavemaker.runtime.data.converters;

import java.sql.Blob;

import org.hibernate.type.descriptor.java.BlobJavaType; // hibernate7: was org.hibernate.type.BlobType (removed in 6.0)

/**
 * @author <a href="mailto:dilip.gundu@wavemaker.com">Dilip Kumar</a>
 * @since 23/2/17
 */
public class BlobTypeConverter extends HibernateBackedJavaTypeConverter {
    public BlobTypeConverter() {
        super(BlobJavaType.INSTANCE);
    }

    // hibernate7: 5.6 read the string as hex pairs, each shifted by Byte.MIN_VALUE (PrimitiveByteArrayTypeDescriptor);
    // 7.x's BlobJavaType.fromString reads plain hex pairs. The 5.6 bytes are decoded here and handed to it as plain hex.
    @Override
    public Object fromString(final String value) {
        if (value == null) {
            return super.fromString(null);
        }
        if (value.length() % 2 != 0) {
            throw new IllegalArgumentException("The string is not a valid string representation of a binary content.");
        }
        final StringBuilder hex = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i += 2) {
            final int shifted = (byte) (Integer.parseInt(value.substring(i, i + 2), 16) + Byte.MIN_VALUE);
            hex.append(String.format("%02x", shifted & 0xff));
        }
        return super.fromString(hex.toString());
    }

    @Override
    public Object fromDbValue(final Object value) {
        if (value instanceof Blob) {
            return toDbValue(value, byte[].class);
        } else {
            return value;
        }
    }

    @Override
    public Object toDbValue(final Object value, final Class<?> toType) {
        return value instanceof Blob ? super.toDbValue(value, toType) : value;
    }
}
