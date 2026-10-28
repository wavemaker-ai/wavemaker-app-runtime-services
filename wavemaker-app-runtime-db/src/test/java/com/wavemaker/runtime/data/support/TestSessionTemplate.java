package com.wavemaker.runtime.data.support;

import jakarta.persistence.EntityManagerFactory;

import com.wavemaker.runtime.data.dao.WMSessionTemplate;

/**
 * The session template with the few operations of the Hibernate 5 template that the tests use directly.
 */
public class TestSessionTemplate extends WMSessionTemplate {

    public TestSessionTemplate(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    public void persist(Object entity) {
        save(entity);
    }

    public void clear() {
        run(org.hibernate.Session::clear);
    }

    public boolean contains(Object entity) {
        return execute(session -> session.contains(entity));
    }

    public <T> T get(Class<T> entityClass, Object id) {
        return execute(session -> session.find(entityClass, id));
    }

    public int bulkUpdate(String hql) {
        return execute(session -> session.createMutationQuery(hql).executeUpdate());
    }
}
