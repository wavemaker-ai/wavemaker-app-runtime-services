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

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.dialect.InnoDBStorageEngine;
import org.hibernate.dialect.MySQLStorageEngine;
import org.hibernate.type.StandardBasicTypes;

import com.wavemaker.commons.CommonConstants;

/**
 * hibernate7: copied from wavemaker-app-runtime-db and ported (the original builds on Hibernate 5 APIs removed in 6.0):
 * <ul>
 *   <li>{@code MySQL5Dialect} was removed in 6.0; {@code org.hibernate.dialect.MySQLDialect} is the version-aware one;</li>
 *   <li>{@code registerFunction(.., new NoArgSQLFunction(..))} -&gt; {@link #initializeFunctionRegistry};</li>
 *   <li>{@code registerColumnType(..)} -&gt; {@link #columnType(int)}.</li>
 * </ul>
 * DDL type names ({@link #columnType(int)}) only matter to Hibernate's own schema export, which Studio does not use
 * (Studio's DDL comes from its SchemaUpdater).
 */
public class MySQLDialect extends org.hibernate.dialect.MySQLDialect {

    public MySQLDialect() {
        super();
    }

    @Override
    public void initializeFunctionRegistry(FunctionContributions functionContributions) {
        super.initializeFunctionRegistry(functionContributions);
        functionContributions.getFunctionRegistry().noArgsBuilder("uuid")
            .setInvariantType(functionContributions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.STRING))
            .register();
    }

    @Override
    protected String columnType(int sqlTypeCode) {
        // as hibernate timestamp is mapping to sql datetime in mysql, so forcing hibernate timestamp to map sql timestamp.
        if (sqlTypeCode == Types.TIMESTAMP) {
            return "timestamp";
        }
        if (sqlTypeCode == CommonConstants.DATE_TIME_WM_TYPE_CODE) {
            return "datetime";
        }
        return super.columnType(sqlTypeCode);
    }

    @Override
    protected MySQLStorageEngine getDefaultMySQLStorageEngine() {
        return InnoDBStorageEngine.INSTANCE;
    }
}
