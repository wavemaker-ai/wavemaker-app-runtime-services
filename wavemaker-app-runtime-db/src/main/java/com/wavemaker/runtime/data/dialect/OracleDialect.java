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
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.descriptor.jdbc.CharJdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;

import com.wavemaker.commons.CommonConstants;

/**
 * hibernate7: copied from wavemaker-app-runtime-db (projects store this class name as their dialect) and ported; the
 * original builds on Hibernate 5 APIs removed in 6.0:
 * <ul>
 *   <li>{@code registerColumnType(..)} -&gt; {@link #columnType(int)};</li>
 *   <li>{@code registerHibernateType(TIMESTAMP WITH (LOCAL) TIME ZONE, timestamp)} -&gt; {@link #resolveSqlTypeDescriptor};</li>
 *   <li>{@code getSqlTypeDescriptorOverride(BOOLEAN) = CharTypeDescriptor} -&gt; {@link #contributeTypes}.</li>
 * </ul>
 * Not run against Oracle here (no Oracle database in the test setup).
 */
public class OracleDialect extends org.hibernate.dialect.OracleDialect {

    public OracleDialect() {
        super();
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

    @Override
    public JdbcType resolveSqlTypeDescriptor(String columnTypeName, int jdbcTypeCode, int precision, int scale,
                                             JdbcTypeRegistry jdbcTypeRegistry) {
        if (jdbcTypeCode == CommonConstants.TIMESTAMP_WITH_TIMEZONE_SQL_CODE
            || jdbcTypeCode == CommonConstants.TIMESTAMP_WITH_LOCAL_TIMEZONE_SQL_CODE) {
            return jdbcTypeRegistry.getDescriptor(Types.TIMESTAMP);
        }
        return super.resolveSqlTypeDescriptor(columnTypeName, jdbcTypeCode, precision, scale, jdbcTypeRegistry);
    }

    @Override
    public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        super.contributeTypes(typeContributions, serviceRegistry);
        typeContributions.getTypeConfiguration().getJdbcTypeRegistry().addDescriptor(Types.BOOLEAN, CharJdbcType.INSTANCE);
    }
}
