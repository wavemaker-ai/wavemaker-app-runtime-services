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

import org.hibernate.dialect.SQLServerDialect;

import com.wavemaker.commons.CommonConstants;

/**
 * Created by sunilp on 4/2/15.
 */
// hibernate7: SQLServer2012Dialect was deprecated in 6 and removed in 7; SQLServerDialect is version-aware
public class WMSQLServerDialect extends SQLServerDialect {

    public WMSQLServerDialect() {
        super();
    }

    // hibernate7: registerColumnType(..) was removed in 6.0; DDL type names come from columnType(int).
    // 5.6 also called registerHibernateType(NCHAR / NVARCHAR -> string, LONGNVARCHAR -> text) for native-query result
    // typing; 6/7 read these JDBC types as String (or Character for length 1) themselves, and the query test-run converts
    // every value to the column's Java type, giving the same output for both. Not run against SQL Server. DDL type
    // names only matter to Hibernate's own schema export, which Studio does not use.
    @Override
    protected String columnType(int sqlTypeCode) {
        if (sqlTypeCode == Types.CHAR) {
            return "nchar(1)";
        }
        if (sqlTypeCode == CommonConstants.DATE_TIME_WM_TYPE_CODE) {
            return "datetime";
        }
        return super.columnType(sqlTypeCode);
    }
}
