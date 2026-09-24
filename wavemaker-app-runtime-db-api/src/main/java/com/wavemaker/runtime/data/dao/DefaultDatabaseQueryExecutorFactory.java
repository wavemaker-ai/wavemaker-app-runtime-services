/*******************************************************************************
 * Copyright (C) 2022-2026 WaveMaker, Inc.
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
package com.wavemaker.runtime.data.dao;

import com.wavemaker.runtime.commons.WMAppContext;

/**
 * Looks up the {@code <serviceName>Native}/{@code <serviceName>Hql} {@code QueryExecutor} bean generated
 * for a database service (see {@code spring_xml.ftl}), already wired with that service's
 * {@code <serviceName>Template}/{@code <serviceName>TransactionManager} beans - the same beans callers used
 * to look up and cast by hand before this factory existed.
 */
public class DefaultDatabaseQueryExecutorFactory implements DatabaseQueryExecutorFactory {

    @Override
    public DatabaseQueryExecutor getExecutor(final String serviceName, final QueryType queryType) {
        String queryExecutorBeanSuffix = queryType == QueryType.NATIVE_SQL ? "Native" : "Hql";
        return WMAppContext.getInstance().getSpringBean(serviceName + queryExecutorBeanSuffix + "QueryExecutor");
    }

    @Override
    public DatabaseQueryExecutor getExecutor(final String serviceName, final boolean isNative) {
        return getExecutor(serviceName, isNative ? QueryType.NATIVE_SQL : QueryType.HQL);
    }
}
