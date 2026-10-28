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

import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;

/**
 * The versioned SQL Server dialects are merged into one dialect in Hibernate 7, which picks the features from the version of the
 * database. Kept for the applications which have this class as their dialect.
 */
public class WMSQLServer2008Dialect extends WMSQLServerDialect {

    public WMSQLServer2008Dialect() {
        super();
    }

    public WMSQLServer2008Dialect(DialectResolutionInfo info) {
        super(info);
    }
}
