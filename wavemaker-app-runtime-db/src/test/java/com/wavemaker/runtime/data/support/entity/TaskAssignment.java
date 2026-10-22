/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.support.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * Many-to-Many modeled as its own entity (an "association class") rather than a pure join table - needed
 * because the relationship itself carries a surrogate id and payload ({@link #assignedOn}/{@link #hoursLogged})
 * beyond what {@link EmployeeProject}'s flat composite-key style can hold without ambiguity.
 */
@Entity
public class TaskAssignment implements Serializable {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    private LocalDate assignedOn;

    private BigDecimal hoursLogged;

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(final Task task) {
        this.task = task;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(final Employee employee) {
        this.employee = employee;
    }

    public LocalDate getAssignedOn() {
        return assignedOn;
    }

    public void setAssignedOn(final LocalDate assignedOn) {
        this.assignedOn = assignedOn;
    }

    public BigDecimal getHoursLogged() {
        return hoursLogged;
    }

    public void setHoursLogged(final BigDecimal hoursLogged) {
        this.hoursLogged = hoursLogged;
    }
}
