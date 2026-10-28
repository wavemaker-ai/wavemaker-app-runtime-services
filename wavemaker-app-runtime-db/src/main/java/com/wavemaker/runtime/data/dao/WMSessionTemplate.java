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
package com.wavemaker.runtime.data.dao;

import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;

import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;

/**
 * Runs the work on the Hibernate session of the current transaction of the data service, or on a new session when there
 * is no transaction. The session factory is the {@code <serviceId>SessionFactory} bean, the transaction is the one of the
 * {@code <serviceId>TransactionManager} bean.
 */
public class WMSessionTemplate {

    private static final HibernateJpaDialect JPA_DIALECT = new HibernateJpaDialect();

    private final EntityManagerFactory entityManagerFactory;

    public WMSessionTemplate(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public SessionFactoryImplementor getSessionFactory() {
        // the entity manager factory bean is a proxy, which implements SessionFactory only
        return entityManagerFactory.unwrap(SessionFactoryImplementor.class);
    }

    /**
     * Runs the action. A failure of the persistence is thrown as the Spring {@code DataAccessException} which the
     * HibernateTemplate of Spring 5 threw, like DataIntegrityViolationException or ObjectOptimisticLockingFailureException.
     */
    public <T> T execute(Function<Session, T> action) {
        try {
            EntityManager transactionalEntityManager = EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
            if (transactionalEntityManager != null) {
                return action.apply(transactionalEntityManager.unwrap(Session.class));
            }
            try (Session session = getSessionFactory().openSession()) {
                return action.apply(session);
            }
        } catch (PersistenceException e) {
            DataAccessException translated = JPA_DIALECT.translateExceptionIfPossible(e);
            throw translated == null ? e : translated;
        }
    }

    public void run(Consumer<Session> action) {
        execute(session -> {
            action.accept(session);
            return null;
        });
    }

    public void save(Object entity) {
        run(session -> session.persist(entity));
    }

    /**
     * Updates the row of the entity. An entity which has no row fails with an {@link OptimisticLockException}, as the
     * update of Hibernate 5 did, where merge would insert it.
     */
    public void update(Object entity) {
        run(session -> {
            Object id = session.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
            if (id == null || session.find(Hibernate.getClass(entity), id) == null) {
                throw new OptimisticLockException("The entity " + entity.getClass().getName() + " with id " + id + " has no row to update");
            }
            session.merge(entity);
        });
    }

    public void delete(Object entity) {
        run(session -> session.remove(session.contains(entity) ? entity : session.merge(entity)));
    }

    /**
     * Reloads the entity from the database. An entity which is not in the session, like the one passed to
     * {@link #update(Object)}, which merges a managed copy of it, is not refreshed itself: Hibernate 7 rejects that. The
     * managed copy is refreshed and its state is copied onto the entity, so the entity gets the stored values, as the
     * refresh of an entity attached by the update of Hibernate 5 did.
     */
    public void refresh(Object entity) {
        run(session -> {
            if (session.contains(entity)) {
                session.refresh(entity);
                return;
            }
            Class<?> entityClass = Hibernate.getClass(entity);
            Object id = session.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
            Object managed = id == null ? null : session.find(entityClass, id);
            if (managed == null) {
                throw new EntityNotFoundException("The entity " + entityClass.getName() + " with id " + id + " has no row to refresh");
            }
            session.refresh(managed);
            EntityPersister persister = getSessionFactory().getMappingMetamodel().getEntityDescriptor(entityClass);
            persister.setValues(entity, persister.getValues(managed));
        });
    }

    public void evict(Object entity) {
        run(session -> session.evict(entity));
    }

    public void flush() {
        run(Session::flush);
    }
}
