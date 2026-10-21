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

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.hibernate.HibernateException;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * @author <a href="mailto:dilip.gundu@wavemaker.com">Dilip Kumar</a>
 * @since 12/1/17
 */
// hibernate7: extends JdbcDateTimeTypeConverter (5.6's TimestampType descriptor conversions) instead of wrapping
// JdbcTimestampJavaType
public class TimestampTypeConverter extends JdbcDateTimeTypeConverter {

    public static final String ORACLE_SQL_TIMESTAMP = "oracle.sql.TIMESTAMP";

    // hibernate7: the format 5.6's JdbcTimestampTypeDescriptor.fromString parsed; 7.x's parses ISO-8601 instead
    private static final String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

    public TimestampTypeConverter() {
        super(Timestamp.class);
    }

    @Override
    protected java.util.Date create(final long millis) {
        return new Timestamp(millis);
    }

    @Override
    public Object fromString(final String value) {
        final Object numberValue = ConverterUtil.toLongIfPossible(value);

        if (numberValue instanceof Long) {
            return new Timestamp((Long) numberValue);
        } else {
            try {
                return new Timestamp(new SimpleDateFormat(TIMESTAMP_FORMAT).parse(value).getTime());
            } catch (ParseException e) {
                throw new HibernateException("could not parse timestamp string" + value, e);
            }
        }
    }

    @Override
    public Object fromDbValue(final Object value) {
        Object convertedValue = value;
        if (value != null && ORACLE_SQL_TIMESTAMP.equals(value.getClass().getCanonicalName())) {
            try {
                Class<?> oracleTimestamp = ClassUtils.forName(ORACLE_SQL_TIMESTAMP, Thread.currentThread()
                    .getContextClassLoader());
                convertedValue = ReflectionUtils.invokeMethod(ClassUtils.getMethod(oracleTimestamp, "timestampValue"),
                    value);
            } catch (ClassNotFoundException e) {
                // ignore this exception
            }
        } else {
            convertedValue = super.fromDbValue(value);
        }
        return convertedValue;
    }
}
