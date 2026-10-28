/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.support.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * Many-to-Many between {@link Employee} and {@link Project}, modeled as its own composite-key entity (see
 * {@link EmployeeProjectId}) because the relationship carries a payload ({@code role}) - contrast with
 * {@link Employee#peers}, a pure join table with no payload at all, mapped directly as {@code @ManyToMany}.
 *
 * <p>Three things had to be verified directly, by actually inserting/reloading, not just by Hibernate accepting
 * the mapping at startup:
 * <ul>
 *   <li>Without an explicit {@code @Column(name = "employee_id")} on the plain scalar {@code @Id} field, it
 *   defaults to column {@code employeeId} - a DIFFERENT physical column from the association's
 *   {@code @JoinColumn(name = "employee_id")}. Hibernate then treats them as two unrelated columns instead of
 *   one shared one.</li>
 *   <li>With that mismatch, whichever side is marked {@code insertable = false} never gets a value written -
 *   it stays {@code NULL} in the row - which silently breaks the OTHER side too: the association's own
 *   {@code SELECT ... JOIN} reads that same (never-written, {@code NULL}) column, so {@code employee}/
 *   {@code project} come back {@code null} on every reload even though the insert itself "succeeded".</li>
 *   <li>{@code @MapsId} (deriving the scalar id from the association automatically) does not resolve the
 *   identifier early enough for this harness's call path - {@code WMGenericDaoImpl.create()} uses Hibernate's
 *   legacy {@code session.persist()}, which needs the full composite id resolved immediately, before
 *   {@code @MapsId}'s normal flush-time derivation has run. {@code @MapsId} was dropped entirely in favor of
 *   explicit scalar setters.</li>
 * </ul>
 * The fix: give the scalar {@code @Id} fields the SAME column name as their {@code @JoinColumn}s, make the
 * scalar fields the writable side, and make the associations {@code insertable = false, updatable = false} -
 * callers set both the scalar id (e.g. {@code setEmployeeId}) and the association (e.g. {@code setEmployee})
 * explicitly, exactly like {@link EmployeeAward} already requires for its own composite key.
 */
@Entity
@IdClass(EmployeeProjectId.class)
public class EmployeeProject implements Serializable {

    @Id
    @Column(name = "employee_id")
    private Long employeeId;

    @Id
    @Column(name = "project_id")
    private Long projectId;

    private String role;

    @ManyToOne
    @JoinColumn(name = "employee_id", insertable = false, updatable = false)
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "project_id", insertable = false, updatable = false)
    private Project project;

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(final Long employeeId) {
        this.employeeId = employeeId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(final Long projectId) {
        this.projectId = projectId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(final String role) {
        this.role = role;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(final Employee employee) {
        this.employee = employee;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(final Project project) {
        this.project = project;
    }
}
