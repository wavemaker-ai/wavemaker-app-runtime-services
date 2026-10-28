/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc.
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.Session;
import org.hibernate.query.Query;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Shared {@link DatabaseQueryExecutor} logic: wrapping execution in a transaction and converting Hibernate's
 * positional row shape into a {@code Map<String, Object>} keyed by column position ("0", "1", ...). The
 * {@code <serviceName>Template}/{@code <serviceName>TransactionManager} beans are resolved once by
 * {@link DatabaseQueryExecutorFactory} and handed in here as plain constructor configuration - this class does
 * no bean lookup of its own. Subclasses only decide how to build the {@link Query} itself (HQL vs native SQL).
 */
public abstract class AbstractDatabaseQueryExecutor implements DatabaseQueryExecutor {

    private final WMSessionTemplate hibernateTemplate;
    private final PlatformTransactionManager transactionManager;

    protected AbstractDatabaseQueryExecutor(final WMSessionTemplate hibernateTemplate, final PlatformTransactionManager transactionManager) {
        this.hibernateTemplate = hibernateTemplate;
        this.transactionManager = transactionManager;
    }

    protected abstract Query<?> createQuery(Session session, String query);

    @Override
    public List<Map<String, Object>> execute(final String query, final Map<String, Object> params) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> hibernateTemplate.execute(session -> executeQuery(session, query, params)));
    }

    private List<Map<String, Object>> executeQuery(final Session session, final String query, final Map<String, Object> params) {
        Query<?> hibernateQuery = createQuery(session, query);
        if (params != null) {
            params.forEach(hibernateQuery::setParameter);
        }
        return toRowMaps(hibernateQuery.list());
    }

    private List<Map<String, Object>> toRowMaps(final List<?> resultList) {
        List<Map<String, Object>> rows = new ArrayList<>(resultList.size());
        for (Object row : resultList) {
            Map<String, Object> rowMap = new LinkedHashMap<>();
            if (row instanceof Object[] columns) {
                for (int i = 0; i < columns.length; i++) {
                    rowMap.put(String.valueOf(i), columns[i]);
                }
            } else {
                rowMap.put("0", row);
            }
            rows.add(rowMap);
        }
        return rows;
    }
}
