/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.util;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.entity.Department;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code DaoUtils.findAllChildren}/{@code findAllRemainingChildren} build and run a JPA {@code CriteriaQuery}
 * directly (not HQL) - needs a real session with a mapped association to test meaningfully.
 */
class DaoUtilsFindAllChildrenIntegrationTest extends AbstractHibernateIntegrationTest {

    @Test
    void findAllChildrenReturnsAllChildrenOfTheGivenParent() {
        List<Employee> result = hibernateTemplate.execute(session -> {
            Department engineering = new Department();
            engineering.setName("Engineering");
            session.save(engineering);

            Employee alice = new Employee();
            alice.setName("Alice");
            alice.setDepartment(engineering);
            session.save(alice);

            Employee bob = new Employee();
            bob.setName("Bob");
            bob.setDepartment(engineering);
            session.save(bob);

            session.flush();

            return DaoUtils.findAllChildren(session, Employee.class,
                new DaoUtils.ChildrenFilter<>("department", engineering));
        });

        assertEquals(2, result.size());
    }

    @Test
    void findAllRemainingChildrenExcludesAlreadyKnownChildren() {
        List<Employee> remaining = hibernateTemplate.execute(session -> {
            Department engineering = new Department();
            engineering.setName("Engineering");
            session.save(engineering);

            Employee alice = new Employee();
            alice.setName("Alice");
            alice.setDepartment(engineering);
            session.save(alice);

            Employee bob = new Employee();
            bob.setName("Bob");
            bob.setDepartment(engineering);
            session.save(bob);

            session.flush();

            return DaoUtils.findAllRemainingChildren(session, Employee.class,
                new DaoUtils.ChildrenFilter<>("department", engineering, List.of(alice)));
        });

        assertEquals(1, remaining.size());
        assertTrue(remaining.stream().allMatch(e -> "Bob".equals(e.getName())));
    }
}
