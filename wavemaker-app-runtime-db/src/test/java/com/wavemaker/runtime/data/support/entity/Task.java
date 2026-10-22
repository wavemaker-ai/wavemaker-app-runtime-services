/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.support.entity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;

/**
 * The mandatory (non-nullable) side of {@link Project}'s one-to-many - contrast with {@link Employee#department},
 * which is optional. Also itself a cascading parent to {@link TaskAssignment}, the "association class" that
 * models employee-to-task assignment with its own identity and payload.
 *
 * <p>{@code Task.byProjectName} exists specifically to give {@code WMQueryExecutorImplHqlRelationshipIntegrationTest}
 * a registered named query that joins across a mandatory to-one association (contrast with
 * {@code Employee.byActive}, which filters on a plain scalar field) - proving {@code WMQueryExecutorImpl}'s named-
 * query path correctly resolves and binds parameters through a relationship traversal, not just direct columns.
 */
@Entity
@NamedQueries({
    @NamedQuery(name = "Task.byProjectName", query = "from Task t where t.project.name = :name"),
    @NamedQuery(name = "Task.byProjectName__count", query = "select count(*) from Task t where t.project.name = :name")
})
public class Task implements Serializable {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    private String title;

    private Boolean done;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TaskAssignment> assignments = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(final Project project) {
        this.project = project;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public Boolean getDone() {
        return done;
    }

    public void setDone(final Boolean done) {
        this.done = done;
    }

    public List<TaskAssignment> getAssignments() {
        return assignments;
    }

    public void setAssignments(final List<TaskAssignment> assignments) {
        this.assignments = assignments;
    }
}
