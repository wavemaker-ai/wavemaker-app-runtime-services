/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao.util;

import java.util.List;
import java.util.Map;

import org.hibernate.query.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ParametersConfigurator} needs a real Hibernate {@code Query} (its own {@code getParameterMetadata()}
 * drives which named parameters get bound) to test meaningfully.
 */
class ParametersConfiguratorIntegrationTest extends AbstractHibernateIntegrationTest {

    @BeforeEach
    void seedEmployees() {
        for (String name : List.of("Alice", "Bob", "Carol")) {
            Employee employee = new Employee();
            employee.setName(name);
            hibernateTemplate.save(employee);
        }
        hibernateTemplate.flush();
    }

    @Test
    void configureBindsAScalarNamedParameter() {
        List<Employee> result = hibernateTemplate.execute(session -> {
            Query<Employee> query = session.createQuery("from Employee e where e.name = :name", Employee.class);
            ParametersConfigurator.configure(query, Map.of("name", "Alice"));
            return query.list();
        });

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).getName());
    }

    @Test
    void configureBindsAListValuedNamedParameterViaSetParameterList() {
        List<Employee> result = hibernateTemplate.execute(session -> {
            Query<Employee> query = session.createQuery("from Employee e where e.name in (:names)", Employee.class);
            ParametersConfigurator.configure(query, Map.of("names", List.of("Alice", "Bob")));
            return query.list();
        });

        assertEquals(2, result.size());
    }

    @Test
    void configureReturnsTheSameQueryInstanceItWasGiven() {
        boolean sameInstance = hibernateTemplate.execute(session -> {
            Query<Employee> query = session.createQuery("from Employee e where e.name = :name", Employee.class);
            return ParametersConfigurator.configure(query, Map.of("name", "Alice")) == query;
        });

        assertTrue(sameInstance);
    }
}
