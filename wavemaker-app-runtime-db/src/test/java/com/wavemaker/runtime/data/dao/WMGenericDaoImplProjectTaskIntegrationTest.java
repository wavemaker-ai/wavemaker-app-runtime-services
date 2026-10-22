/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.wavemaker.runtime.data.exception.EntityNotFoundException;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.TestProjectDao;
import com.wavemaker.runtime.data.support.TestTaskDao;
import com.wavemaker.runtime.data.support.entity.Project;
import com.wavemaker.runtime.data.support.entity.Task;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link WMGenericDaoImpl} on a one-to-many pair with a MANDATORY ({@code NOT NULL}) parent FK and an explicit
 * {@code cascade}/{@code orphanRemoval} - neither of which {@code Employee}/{@code Department} has. The generic CRUD,
 * paging, sort, search and count scenarios are covered once, against {@code Employee}, in
 * {@link WMGenericDaoImplIntegrationTest}; this class only tests what this data shape adds.
 */
class WMGenericDaoImplProjectTaskIntegrationTest extends AbstractHibernateIntegrationTest {

    private TestProjectDao projectDao;
    private TestTaskDao taskDao;

    @BeforeEach
    void createDaos() {
        projectDao = new TestProjectDao(hibernateTemplate);
        taskDao = new TestTaskDao(hibernateTemplate);
    }

    private static Project project(String name) {
        Project project = new Project();
        project.setName(name);
        project.setStartDate(LocalDate.of(2024, 1, 1));
        return project;
    }

    private static Task task(Project project, String title, boolean done) {
        Task task = new Task();
        task.setProject(project);
        task.setTitle(title);
        task.setDone(done);
        return task;
    }

    @Test
    void createWithoutTheMandatoryParentThrowsAConstraintViolation() {
        // Corner case specific to this data shape: Task.project is NOT NULL, contrast with Employee.department,
        // which is optional. WMGenericDaoImpl itself has no validation for this - the failure comes purely from
        // the database's own NOT NULL constraint, surfacing as a plain DataIntegrityViolationException, not
        // this module's EntityNotFoundException or any WMGenericDaoImpl-specific exception type.
        Task orphanTask = task(null, "Orphan task", false);

        assertThrows(DataIntegrityViolationException.class, () -> taskDao.create(orphanTask));
    }

    @Test
    void deleteOfAProjectThroughTheGenericDaoCascadesToItsTasks() {
        // The corner case this data shape exists to prove: Project.tasks has cascade=ALL, orphanRemoval=true at
        // the Hibernate mapping level. A first version of this test called projectDao.findById(...) and
        // immediately delete() on the result - that failed with a real NOT NULL constraint violation on
        // task.project_id, because @OneToMany defaults to LAZY: the same-session identity map handed back the
        // original "project" object, whose tasks collection was still the empty list set at construction, never
        // populated from the database. Cascade only acts on what Hibernate actually sees in the Java collection
        // at delete time, not on "whatever rows currently reference me" - so with an empty in-memory collection,
        // there was nothing to cascade, and the plain DELETE hit the real FK from the still-existing tasks.
        // Forcing the lazy collection to initialize (iterating it) before delete() is what makes cascade see
        // the real children and fire correctly - this is what actually confirms cascade works through
        // WMGenericDaoImpl.delete(), not a raw Session-level delete.
        Project project = projectDao.create(project("Customer Portal Revamp"));
        Task task1 = taskDao.create(task(project, "Design new UI mockups", true));
        Task task2 = taskDao.create(task(project, "Implement checkout flow", false));
        hibernateTemplate.flush();
        hibernateTemplate.clear();

        Project reloadedProject = projectDao.findById(project.getId());
        reloadedProject.getTasks().size();
        projectDao.delete(reloadedProject);
        hibernateTemplate.flush();

        assertThrows(EntityNotFoundException.class, () -> taskDao.findById(task1.getId()));
        assertThrows(EntityNotFoundException.class, () -> taskDao.findById(task2.getId()));
    }
}
