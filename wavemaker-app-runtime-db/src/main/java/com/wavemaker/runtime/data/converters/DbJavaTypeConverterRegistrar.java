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

import org.springframework.beans.factory.InitializingBean;

import com.wavemaker.app.db.constants.JavaType;

/**
 * Only exists when wavemaker-app-runtime-db is on the classpath (see db-java-type-converters.xml).
 * On startup, switches every JavaType constant over to its real, Hibernate-backed converter, so
 * database reads/writes use accurate database conversion instead of the generic fallback.
 */
public class DbJavaTypeConverterRegistrar implements InitializingBean {

    @Override
    public void afterPropertiesSet() {
        JavaType.BYTE.registerDbConverter(new ByteTypeConverter());
        JavaType.SHORT.registerDbConverter(new ShortTypeConverter());
        JavaType.INTEGER.registerDbConverter(new IntegerTypeConverter());
        JavaType.LONG.registerDbConverter(new LongTypeConverter());
        JavaType.BIG_INTEGER.registerDbConverter(new BigIntegerTypeConverter());
        JavaType.FLOAT.registerDbConverter(new FloatTypeConverter());
        JavaType.DOUBLE.registerDbConverter(new DoubleTypeConverter());
        JavaType.BIG_DECIMAL.registerDbConverter(new BigDecimalTypeConverter());
        JavaType.BOOLEAN.registerDbConverter(new BooleanTypeConverter());
        JavaType.YES_OR_NO.registerDbConverter(new YesNoTypeConverter());
        JavaType.TRUE_OR_FALSE.registerDbConverter(new TrueFalseTypeConverter());
        JavaType.CHARACTER.registerDbConverter(new CharacterTypeConverter());
        JavaType.STRING.registerDbConverter(new StringTypeConverter());
        JavaType.TEXT.registerDbConverter(new StringTypeConverter());
        JavaType.CLOB.registerDbConverter(new StringTypeConverter());
        JavaType.BLOB.registerDbConverter(new BlobTypeConverter());
        JavaType.DATE.registerDbConverter(new DateTypeConverter());
        JavaType.TIME.registerDbConverter(new TimeTypeConverter());
        JavaType.TIMESTAMP.registerDbConverter(new TimestampTypeConverter());
    }
}
