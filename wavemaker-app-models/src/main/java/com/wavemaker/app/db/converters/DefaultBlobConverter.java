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
package com.wavemaker.app.db.converters;

import java.sql.Blob;
import java.sql.SQLException;

import com.wavemaker.commons.WMRuntimeException;

public class DefaultBlobConverter implements JavaTypeConverter {

    @Override
    public Object fromString(final String value) {
        return value;
    }

    @Override
    public Object fromDbValue(final Object value) {
        return value instanceof Blob ? toBytes((Blob) value) : value;
    }

    @Override
    public Object toDbValue(final Object value, final Class<?> toType) {
        return value instanceof Blob ? toBytes((Blob) value) : value;
    }

    private byte[] toBytes(final Blob blob) {
        try {
            return blob.getBytes(1, (int) blob.length());
        } catch (SQLException e) {
            throw new WMRuntimeException(e);
        }
    }
}
