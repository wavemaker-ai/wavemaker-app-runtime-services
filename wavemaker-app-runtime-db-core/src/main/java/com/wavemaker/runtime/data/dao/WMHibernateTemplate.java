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

import java.io.Serializable;

import jakarta.persistence.PersistenceException;

import org.hibernate.FlushMode;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.jpa.hibernate.HibernateExceptionTranslator;

public class WMHibernateTemplate implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(WMHibernateTemplate.class);

    private final HibernateExceptionTranslator exceptionTranslator = new HibernateExceptionTranslator();

    private SessionFactory sessionFactory;

    public WMHibernateTemplate() {
    }

    public WMHibernateTemplate(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public void afterPropertiesSet() {
        if (sessionFactory == null) {
            throw new IllegalArgumentException("Property 'sessionFactory' is required");
        }
    }

    public <T> T execute(WMHibernateCallback<T> action) {
        Session session = null;
        boolean isNew = false;
        try {
            session = sessionFactory.getCurrentSession();
        } catch (HibernateException ex) {
            logger.debug("Could not retrieve pre-bound Hibernate session", ex);
        }
        if (session == null) {
            session = sessionFactory.openSession();
            session.setHibernateFlushMode(FlushMode.MANUAL);
            isNew = true;
        }
        try {
            return action.doInHibernate(session);
        } catch (PersistenceException ex) {
            DataAccessException translated = exceptionTranslator.translateExceptionIfPossible(ex);
            throw translated != null ? translated : ex;
        } finally {
            if (isNew) {
                session.close();
            }
        }
    }

    public <T> T executeWithNativeSession(WMHibernateCallback<T> action) {
        return execute(action);
    }

    public void save(Object entity) {
        execute(session -> session.save(entity));
    }

    public void persist(Object entity) {
        execute(session -> {
            session.persist(entity);
            return null;
        });
    }

    public <T> T get(Class<T> entityClass, Serializable id) {
        return execute(session -> session.get(entityClass, id));
    }

    public boolean contains(Object entity) {
        return execute(session -> session.contains(entity));
    }

    public void update(Object entity) {
        execute(session -> {
            session.update(entity);
            return null;
        });
    }

    public void delete(Object entity) {
        execute(session -> {
            session.delete(entity);
            return null;
        });
    }

    public void flush() {
        execute(session -> {
            session.flush();
            return null;
        });
    }

    public void clear() {
        execute(session -> {
            session.clear();
            return null;
        });
    }

    public int bulkUpdate(String queryString, Object... values) {
        return execute(session -> {
            Query<?> query = session.createQuery(queryString);
            for (int i = 0; i < values.length; i++) {
                query.setParameter(i, values[i]);
            }
            return query.executeUpdate();
        });
    }

    public void refresh(Object entity) {
        execute(session -> {
            session.refresh(entity);
            return null;
        });
    }

    public void evict(Object entity) {
        execute(session -> {
            session.evict(entity);
            return null;
        });
    }
}
