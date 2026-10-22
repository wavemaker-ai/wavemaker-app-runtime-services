/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.support.entity;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * A plain (non-temporal) child of {@link TemporalEmployee}, mirroring {@link Project}/{@link Task}'s
 * mandatory, cascading one-to-many shape exactly - the question this fixture exists to answer is whether
 * {@code WMGenericTemporalDaoImpl}'s inherited {@code delete()} (the same unmodified
 * {@code WMGenericDaoImpl.delete()} - see {@code WMGenericTemporalDaoImplIntegrationTest}'s class javadoc) still
 * cascades correctly when the parent is a {@code @TableTemporal} entity, not just a plain one.
 */
@Entity
public class TemporalEmployeeAward implements Serializable {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private TemporalEmployee employee;

    private String awardCode;

    private LocalDate awardedOn;

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public TemporalEmployee getEmployee() {
        return employee;
    }

    public void setEmployee(final TemporalEmployee employee) {
        this.employee = employee;
    }

    public String getAwardCode() {
        return awardCode;
    }

    public void setAwardCode(final String awardCode) {
        this.awardCode = awardCode;
    }

    public LocalDate getAwardedOn() {
        return awardedOn;
    }

    public void setAwardedOn(final LocalDate awardedOn) {
        this.awardedOn = awardedOn;
    }
}
