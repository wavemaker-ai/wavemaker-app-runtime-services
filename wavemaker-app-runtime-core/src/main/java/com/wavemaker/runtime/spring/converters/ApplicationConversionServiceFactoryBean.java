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
package com.wavemaker.runtime.spring.converters;

import java.sql.Time;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Set;

import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.support.FormattingConversionServiceFactoryBean;

import com.wavemaker.commons.WMRuntimeException;
import com.wavemaker.commons.json.deserializer.WMDateDeSerializer;
import com.wavemaker.commons.json.deserializer.WMLocalDateTimeDeSerializer;
import com.wavemaker.commons.json.deserializer.WMSqlDateDeSerializer;
import com.wavemaker.commons.util.StringUtils;
import com.wavemaker.runtime.commons.file.model.ExportType;

/**
 *
 */
public class ApplicationConversionServiceFactoryBean extends FormattingConversionServiceFactoryBean {

    @Override
    public void setConverters(Set converters) {
        super.setConverters(converters);
    }

    public static class WMStringToDateConverter implements Converter<String, Date> {

        @Override
        public Date convert(String source) {
            if (NumberUtils.isCreatable(source)) {
                return new java.sql.Date(Long.parseLong(source));
            }
            return WMDateDeSerializer.getDate(source);
        }
    }

    public static class WMStringToTimeConverter implements Converter<String, Time> {

        @Override
        public Time convert(String source) {
            if (NumberUtils.isCreatable(source)) {
                return new Time(Long.parseLong(source));
            }
            return (Time) WMDateDeSerializer.getDate(source);
        }
    }

    public static class WMStringToSqlDateConverter implements Converter<String, java.sql.Date> {

        @Override
        public java.sql.Date convert(String source) {
            if (StringUtils.isNumber(source)) {
                return new java.sql.Date(Long.parseLong(source));
            } else {
                return WMSqlDateDeSerializer.getDate(source);
            }
        }
    }

    public static class WMStringToLocalDateTimeConverter implements Converter<String, LocalDateTime> {

        @Override
        public LocalDateTime convert(String source) {
            return WMLocalDateTimeDeSerializer.getLocalDateTime(source);
        }
    }

    public static class WMStringToTimestampConverter implements Converter<String, Timestamp> {

        private static final String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

        @Override
        public Timestamp convert(final String source) {
            if (NumberUtils.isCreatable(source)) {
                return new Timestamp(Long.parseLong(source));
            }
            try {
                return new Timestamp(new SimpleDateFormat(TIMESTAMP_FORMAT).parse(source).getTime());
            } catch (ParseException e) {
                throw new WMRuntimeException(e);
            }
        }
    }

    public static class WMStringToExportTypeConverter implements Converter<String, ExportType> {

        @Override
        public ExportType convert(final String source) {
            return ExportType.valueOf(org.apache.commons.lang3.StringUtils.upperCase(source));
        }
    }

}