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
import com.wavemaker.runtime.data.support.TestEmployeeDao;
import com.wavemaker.runtime.data.support.TestEmployeeProjectDao;
import com.wavemaker.runtime.data.support.TestProjectDao;
import com.wavemaker.runtime.data.support.entity.Employee;
import com.wavemaker.runtime.data.support.entity.EmployeeProject;
import com.wavemaker.runtime.data.support.entity.EmployeeProjectId;
import com.wavemaker.runtime.data.support.entity.Project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers "dao-create-composite-pk" again, this time against {@link EmployeeProject} - a composite-key
 * ({@code @IdClass}) entity that ALSO carries real {@code @ManyToOne} associations to both halves of its key
 * (see that class's own javadoc for the mapping pitfalls this surfaced). Contrast with
 * {@link WMGenericDaoImplCompositeKeyIntegrationTest}'s {@code EmployeeAward}, whose composite key is two plain
 * scalar fields with no object navigation at all - this is the "association class" variant of a composite key.
 */
class WMGenericDaoImplEmployeeProjectIntegrationTest extends AbstractHibernateIntegrationTest {

    private TestEmployeeDao employeeDao;
    private TestProjectDao projectDao;
    private TestEmployeeProjectDao employeeProjectDao;

    @BeforeEach
    void createDaos() {
        employeeDao = new TestEmployeeDao(hibernateTemplate);
        projectDao = new TestProjectDao(hibernateTemplate);
        employeeProjectDao = new TestEmployeeProjectDao(hibernateTemplate);
    }

    private static Employee employee(String name) {
        Employee employee = new Employee();
        employee.setName(name);
        employee.setSalary(BigDecimal.ONE);
        employee.setActive(true);
        return employee;
    }

    private static Project project(String name) {
        Project project = new Project();
        project.setName(name);
        return project;
    }

    @Test
    void createWithACompositePrimaryKeyPopulatesEveryKeyFieldAndFindByIdLocatesItByTheCompositeKeyAndNavigatesBothAssociations() {
        Employee carol = employeeDao.create(employee("Carol"));
        Project project = projectDao.create(project("Second Project"));

        EmployeeProject employeeProject = new EmployeeProject();
        employeeProject.setEmployeeId(carol.getId());
        employeeProject.setProjectId(project.getId());
        employeeProject.setEmployee(carol);
        employeeProject.setProject(project);
        employeeProject.setRole("Lead");

        EmployeeProject created = employeeProjectDao.create(employeeProject);

        assertNotNull(created.getEmployeeId());
        assertNotNull(created.getProjectId());
        //why clear()
        hibernateTemplate.clear();
        EmployeeProject found = employeeProjectDao.findById(new EmployeeProjectId(carol.getId(), project.getId()));
        assertEquals("Lead", found.getRole());
        assertEquals("Carol", found.getEmployee().getName());
        assertEquals("Second Project", found.getProject().getName());
    }

    @Test
    void findByIdWithAnUnmatchedCompositeKeyThrows() {
        assertThrows(EntityNotFoundException.class, () -> employeeProjectDao.findById(new EmployeeProjectId(999999L, 999999L)));
    }

    @Test
    void updateChangesOnlyTheRoleFieldAndALaterReloadReflectsIt() {
        Employee dave = employeeDao.create(employee("Dave"));
        Project project = projectDao.create(project("Platform Migration"));
        EmployeeProject employeeProject = new EmployeeProject();
        employeeProject.setEmployeeId(dave.getId());
        employeeProject.setProjectId(project.getId());
        employeeProject.setEmployee(dave);
        employeeProject.setProject(project);
        employeeProject.setRole("Contributor");
        employeeProjectDao.create(employeeProject);
        hibernateTemplate.clear();

        EmployeeProjectId id = new EmployeeProjectId(dave.getId(), project.getId());
        EmployeeProject toUpdate = employeeProjectDao.findById(id);
        toUpdate.setRole("Lead");
        employeeProjectDao.update(toUpdate);
        hibernateTemplate.clear();

        assertEquals("Lead", employeeProjectDao.findById(id).getRole());
    }

    @Test
    void deleteByCompositeKeyRemovesOnlyTheJoinRowLeavingBothTheEmployeeAndProjectIntact() {
        // Corner case directly proving the many-to-many "join row only" cascade semantics already verified at
        // the raw Hibernate level, now through the generic DAO: deleting the association must never delete
        // either side's real entity.
        Employee eve = employeeDao.create(employee("Eve"));
        Project project = projectDao.create(project("Customer Portal Revamp"));
        EmployeeProject employeeProject = new EmployeeProject();
        employeeProject.setEmployeeId(eve.getId());
        employeeProject.setProjectId(project.getId());
        employeeProject.setEmployee(eve);
        employeeProject.setProject(project);
        employeeProject.setRole("Advisor");
        employeeProjectDao.create(employeeProject);
        hibernateTemplate.clear();

        EmployeeProjectId id = new EmployeeProjectId(eve.getId(), project.getId());
        employeeProjectDao.delete(employeeProjectDao.findById(id));

        assertThrows(EntityNotFoundException.class, () -> employeeProjectDao.findById(id));
        assertEquals("Eve", employeeDao.findById(eve.getId()).getName());
        assertEquals("Customer Portal Revamp", projectDao.findById(project.getId()).getName());
    }
}
