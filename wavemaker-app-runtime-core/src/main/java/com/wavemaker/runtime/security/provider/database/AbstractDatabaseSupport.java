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
package com.wavemaker.runtime.security.provider.database;

import java.util.List;
import java.util.Map;

import com.wavemaker.runtime.data.dao.DatabaseQueryExecutor;
import com.wavemaker.runtime.data.dao.DatabaseQueryExecutorFactory;

/**
 * Created by ArjunSahasranam on 11/3/16.
 */
public abstract class AbstractDatabaseSupport {

    protected boolean hql;
    private String serviceName;
    private DatabaseQueryExecutorFactory executorFactory;

    public boolean isHql() {
        return hql;
    }

    public void setHql(final boolean hql) {
        this.hql = hql;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(final String serviceName) {
        this.serviceName = serviceName;
    }

    public void setExecutorFactory(final DatabaseQueryExecutorFactory executorFactory) {
        this.executorFactory = executorFactory;
    }

    protected List<Map<String, Object>> executeQuery(final String query, final Map<String, Object> params) {
        DatabaseQueryExecutor executor = executorFactory.getExecutor(serviceName, !hql);
        return executor.execute(query, params);
    }
}
