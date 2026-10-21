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

import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.dialect.identity.IdentityColumnSupport;

import com.wavemaker.commons.CommonConstants;

/**
 * hibernate7: copied from wavemaker-app-runtime-db (projects store this class name as their dialect) and ported:
 * PostgreSQL82Dialect moved to hibernate-community-dialects in 6.0, the core, version-aware PostgreSQLDialect is used
 * (as for WMRedshiftSQLDialect); {@code registerColumnType(..)} -&gt; {@link #columnType(int)}.
 */
public class WMPostgresSQLDialect extends PostgreSQLDialect {

    public WMPostgresSQLDialect() {
        super();
    }

    @Override
    protected String columnType(int sqlTypeCode) {
        if (sqlTypeCode == CommonConstants.DATE_TIME_WM_TYPE_CODE) {
            return "timestamp without time zone";
        }
        return super.columnType(sqlTypeCode);
    }

    @Override
    public IdentityColumnSupport getIdentityColumnSupport() {
        return new WMPostgresIdentityColumnSupport();
    }
}
