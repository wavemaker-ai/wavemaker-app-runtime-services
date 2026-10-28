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
package com.wavemaker.runtime.data.dialect;

import java.sql.Types;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.descriptor.jdbc.TimestampJdbcType;

import com.wavemaker.commons.CommonConstants;

/**
 * Created by sunilp on 21/7/15.
 */
public class OracleDialect extends org.hibernate.dialect.OracleDialect {

    public OracleDialect() {
        super();
    }

    public OracleDialect(DialectResolutionInfo info) {
        super(info);
    }

    @Override
    public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        super.contributeTypes(typeContributions, serviceRegistry);
        // Oracle specific timestamp with time zone codes are read as timestamp.
        typeContributions.getTypeConfiguration().getJdbcTypeRegistry()
            .addDescriptor(CommonConstants.TIMESTAMP_WITH_TIMEZONE_SQL_CODE, TimestampJdbcType.INSTANCE);
        typeContributions.getTypeConfiguration().getJdbcTypeRegistry()
            .addDescriptor(CommonConstants.TIMESTAMP_WITH_LOCAL_TIMEZONE_SQL_CODE, TimestampJdbcType.INSTANCE);
    }

    @Override
    public int getPreferredSqlTypeCodeForBoolean() {
        return Types.CHAR;
    }

    @Override
    protected String columnType(int sqlTypeCode) {
        if (sqlTypeCode == CommonConstants.DATE_TIME_WM_TYPE_CODE) {
            return "date";
        }
        if (sqlTypeCode == Types.TIMESTAMP) {
            return "timestamp";
        }
        return super.columnType(sqlTypeCode);
    }
}
