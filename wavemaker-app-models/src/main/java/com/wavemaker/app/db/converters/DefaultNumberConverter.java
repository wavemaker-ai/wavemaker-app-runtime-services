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

import java.util.function.Function;

public class DefaultNumberConverter implements JavaTypeConverter {

    private final Class<? extends Number> targetType;
    private final Function<String, Object> parser;
    private final Function<Number, Object> fromNumber;

    public DefaultNumberConverter(final Class<? extends Number> targetType, final Function<String, Object> parser,
                                  final Function<Number, Object> fromNumber) {
        this.targetType = targetType;
        this.parser = parser;
        this.fromNumber = fromNumber;
    }

    @Override
    public Object fromString(final String value) {
        return parser.apply(value);
    }

    @Override
    public Object fromDbValue(final Object value) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        return value instanceof Number ? fromNumber.apply((Number) value) : parser.apply(String.valueOf(value));
    }

    @Override
    public Object toDbValue(final Object value, final Class<?> toType) {
        return value;
    }
}
