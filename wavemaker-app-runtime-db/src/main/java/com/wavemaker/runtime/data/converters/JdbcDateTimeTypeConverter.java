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

import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.hibernate.HibernateException;

/**
 * hibernate7: the conversions 5.6's JdbcDate / JdbcTime / JdbcTimestampTypeDescriptor did. Their 7.x replacements
 * (Jdbc*JavaType) no longer keep the milliseconds of a wrapped java.util.Date (dates are truncated to midnight, times to
 * 1970-01-01) and only unwrap their own class (any other java.util.Date fails with a ClassCastException), so these
 * converters do it themselves.
 */
public abstract class JdbcDateTimeTypeConverter implements JavaTypeConverter {

    private final Class<? extends Date> jdbcType;

    protected JdbcDateTimeTypeConverter(Class<? extends Date> jdbcType) {
        this.jdbcType = jdbcType;
    }

    protected abstract Date create(long millis);

    @Override
    public Object fromDbValue(final Object value) {
        if (value == null) {
            return null;
        }
        if (jdbcType.isInstance(value)) {
            return value;
        }
        if (value instanceof Long millis) {
            return create(millis);
        }
        if (value instanceof Calendar calendar) {
            return create(calendar.getTimeInMillis());
        }
        if (value instanceof Date date) {
            return create(date.getTime());
        }
        throw new HibernateException("Unknown wrap conversion requested: " + value.getClass().getName() + " to " + Date.class.getName());
    }

    @Override
    public Object toDbValue(final Object value, final Class<?> toType) {
        if (value == null) {
            return null;
        }
        final Date date = (Date) value;
        if (java.sql.Date.class.isAssignableFrom(toType)) {
            return date instanceof java.sql.Date ? date : new java.sql.Date(date.getTime());
        }
        if (Time.class.isAssignableFrom(toType)) {
            return date instanceof Time ? date : new Time(date.getTime());
        }
        if (Timestamp.class.isAssignableFrom(toType)) {
            return date instanceof Timestamp ? date : new Timestamp(date.getTime());
        }
        if (Date.class.isAssignableFrom(toType)) {
            return date;
        }
        if (Calendar.class.isAssignableFrom(toType)) {
            final GregorianCalendar calendar = new GregorianCalendar();
            calendar.setTimeInMillis(date.getTime());
            return calendar;
        }
        if (Long.class.isAssignableFrom(toType)) {
            return date.getTime();
        }
        throw new HibernateException("Unknown unwrap conversion requested: " + Date.class.getName() + " to " + toType.getName());
    }
}
