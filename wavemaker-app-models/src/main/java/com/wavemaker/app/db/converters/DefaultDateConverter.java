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

import java.sql.Date;

import com.wavemaker.commons.json.deserializer.WMDateDeSerializer;

public class DefaultDateConverter implements JavaTypeConverter {

    @Override
    public Object fromString(final String value) {
        final Object numberValue = ConverterUtil.toLongIfPossible(value);
        return numberValue instanceof Long ? new Date((Long) numberValue) : WMDateDeSerializer.getDate(value);
    }

    @Override
    public Object fromDbValue(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return value;
        }
        // java.util.Date (not java.sql.Date) shows up here for hql metadata-derived values - see JavaTypeUtils
        if (value instanceof java.util.Date) {
            return new Date(((java.util.Date) value).getTime());
        }
        return fromString(String.valueOf(value));
    }

    @Override
    public Object toDbValue(final Object value, final Class<?> toType) {
        return value;
    }
}
