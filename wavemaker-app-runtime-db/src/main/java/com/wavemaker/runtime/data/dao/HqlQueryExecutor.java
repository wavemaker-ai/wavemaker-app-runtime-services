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

import org.hibernate.Session;
import org.hibernate.query.Query;
import org.springframework.orm.hibernate5.HibernateOperations;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@link DatabaseQueryExecutor} for HQL queries. See {@link DefaultDatabaseQueryExecutorFactory}.
 */
public class HqlQueryExecutor extends AbstractDatabaseQueryExecutor {

    public HqlQueryExecutor(final HibernateOperations hibernateTemplate, final PlatformTransactionManager transactionManager) {
        super(hibernateTemplate, transactionManager);
    }

    @Override
    protected Query<?> createQuery(final Session session, final String query) {
        return session.createQuery(query);
    }
}
