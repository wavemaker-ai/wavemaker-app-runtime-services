/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.exception.EntityNotFoundException;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.TestEmployeeBadgeDao;
import com.wavemaker.runtime.data.support.TestEmployeeDao;
import com.wavemaker.runtime.data.support.TestEmployeeProfileDao;
import com.wavemaker.runtime.data.support.entity.Employee;
import com.wavemaker.runtime.data.support.entity.EmployeeBadge;
import com.wavemaker.runtime.data.support.entity.EmployeeProfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Corner cases for {@code WMGenericDaoImpl} specific to {@link Employee}'s new associations - self-referencing
 * hierarchy ({@code manager}), both one-to-one styles ({@code profile}/{@code badge}), and self-referencing
 * many-to-many ({@code peers}). None of these relationship shapes existed anywhere in the original Employee/
 * Department fixture, so none of this behavior was previously provable through the generic DAO at all.
 */
class WMGenericDaoImplEmployeeRelationshipsIntegrationTest extends AbstractHibernateIntegrationTest {

    private TestEmployeeDao employeeDao;
    private TestEmployeeProfileDao profileDao;
    private TestEmployeeBadgeDao badgeDao;

    @BeforeEach
    void createDaos() {
        employeeDao = new TestEmployeeDao(hibernateTemplate);
        profileDao = new TestEmployeeProfileDao(hibernateTemplate);
        badgeDao = new TestEmployeeBadgeDao(hibernateTemplate);
    }

    private static Employee employee(String name) {
        Employee employee = new Employee();
        employee.setName(name);
        employee.setSalary(BigDecimal.ONE);
        employee.setActive(true);
        return employee;
    }

    @Test
    void createWithASelfReferencingManagerPersistsCorrectlyAndIsNavigableAfterReload() {
        Employee ivan = employeeDao.create(employee("Ivan"));
        Employee alice = employee("Alice");
        alice.setManager(ivan);

        Employee created = employeeDao.create(alice);
        hibernateTemplate.clear();

        Employee reloaded = employeeDao.findById(created.getId());
        assertEquals("Ivan", reloaded.getManager().getName());
    }

    @Test
    void deleteOfAManagerThroughTheGenericDaoThrowsBecauseNothingNullsOutTheReportsForeignKeyFirst() {
        // Corner case, found by actually running this rather than assuming: WMGenericDaoImpl.delete() is
        // {@code getTemplate().delete(entity)} with NO explicit flush (contrast with create()/update(), which
        // both flush). A first version of this test called hibernateTemplate.clear() right after delete() to
        // force a fresh reload - which silently DISCARDED the still-pending, never-flushed delete entirely,
        // making it look like the delete "succeeded" but left the manager reference intact, when really Ivan's
        // row was never deleted from the database at all.
        //
        // Forcing a real flush here instead reveals the actual behavior: Employee.manager carries no
        // {@code cascade} and no Hibernate {@code @OnDelete}, so nothing tells Hibernate to null out Alice's
        // manager_id before deleting Ivan's row. The database's own FK constraint on manager_id then blocks the
        // delete outright. The hand-authored Postgres schema's ON DELETE SET NULL (postgres-init/01-schema.sql)
        // has no equivalent on this Hibernate mapping at all - this is a genuine gap between the two schemas,
        // not something this test can paper over.
        Employee ivan = employeeDao.create(employee("Ivan"));
        Employee alice = employee("Alice");
        alice.setManager(ivan);
        employeeDao.create(alice);

        Employee managedIvan = employeeDao.findById(ivan.getId());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> {
            employeeDao.delete(managedIvan);
            hibernateTemplate.flush();
        });
    }

    @Test
    void deleteOfAnEmployeeCascadesToItsProfileThroughTheGenericDao() {
        // One-to-one, shared-PK style. cascade=ALL/orphanRemoval=true is declared on Employee.profile (the
        // inverse side) - but cascade only acts on what's actually populated in the Java object graph at the
        // moment delete() is called, not on "whatever currently references me in the database". The first
        // version of this test created the profile via profileDao.create(profile) alone, without ever linking
        // bob.setProfile(profile) in memory, then re-fetched "bob" in the SAME session - which handed back the
        // identity-mapped object from creation time, whose profile field was still null, so cascade had
        // nothing to act on and the profile row was silently left behind. Forcing a real flush + clear before
        // the reload (rather than relying on the same-session identity map) is what makes Hibernate's own
        // mappedBy eager-fetch actually populate the association from the database.
        Employee bob = employeeDao.create(employee("Bob"));
        EmployeeProfile profile = new EmployeeProfile();
        profile.setEmployee(bob);
        profile.setBio("test bio");
        profileDao.create(profile);
        hibernateTemplate.flush();
        hibernateTemplate.clear();

        Employee reloadedBob = employeeDao.findById(bob.getId());
        employeeDao.delete(reloadedBob);
        hibernateTemplate.flush();

        assertThrows(EntityNotFoundException.class, () -> profileDao.findById(bob.getId()));
    }

    @Test
    void deleteOfAnEmployeeCascadesToItsBadgeThroughTheGenericDao() {
        // One-to-one, separate-unique-FK style (contrast with profile above) - same fresh-reload requirement
        // and same reasoning as the profile test above applies here too.
        Employee carol = employeeDao.create(employee("Carol"));
        EmployeeBadge badge = new EmployeeBadge();
        badge.setEmployee(carol);
        badge.setBadgeNumber("BDG-2001");
        Long badgeId = badgeDao.create(badge).getId();
        hibernateTemplate.flush();
        hibernateTemplate.clear();

        Employee reloadedCarol = employeeDao.findById(carol.getId());
        employeeDao.delete(reloadedCarol);
        hibernateTemplate.flush();

        assertThrows(EntityNotFoundException.class, () -> badgeDao.findById(badgeId));
    }

    @Test
    void updateAddingAPeerPersistsTheJoinRowThroughTheGenericDao() {
        // Self-referencing many-to-many, exercised through update() rather than a direct session operation -
        // confirms WMGenericDaoImpl.update() correctly flushes a collection-side change, not just scalar fields.
        Employee dave = employeeDao.create(employee("Dave"));
        Employee eve = employeeDao.create(employee("Eve"));

        Employee managedDave = employeeDao.findById(dave.getId());
        managedDave.getPeers().add(eve);
        employeeDao.update(managedDave);
        hibernateTemplate.clear();

        Employee reloadedDave = employeeDao.findById(dave.getId());
        assertEquals(1, reloadedDave.getPeers().size());
        assertEquals("Eve", reloadedDave.getPeers().get(0).getName());
    }

    @Test
    void findByIdForTheSharedPkOneToOneReturnsTheProfileKeyedByTheEmployeesOwnId() {
        Employee frank = employeeDao.create(employee("Frank"));
        EmployeeProfile profile = new EmployeeProfile();
        profile.setEmployee(frank);
        profile.setBio("shared-pk lookup check");
        profileDao.create(profile);

        // The defining trait of this 1:1 style: no separate surrogate id - the employee's own id IS the lookup key.
        EmployeeProfile found = profileDao.findById(frank.getId());
        assertEquals("shared-pk lookup check", found.getBio());
    }

    @Test
    void findByIdForTheSeparateFkOneToOneReturnsTheBadgeByItsOwnSurrogateId() {
        Employee grace = employeeDao.create(employee("Grace"));
        EmployeeBadge badge = new EmployeeBadge();
        badge.setEmployee(grace);
        badge.setBadgeNumber("BDG-3001");

        EmployeeBadge created = badgeDao.create(badge);

        assertNotNull(created.getId());
        assertEquals("BDG-3001", badgeDao.findById(created.getId()).getBadgeNumber());
        assertTrue(!created.getId().equals(grace.getId()),
            "unlike EmployeeProfile, EmployeeBadge has its own id, independent of the employee's id");
    }
}
