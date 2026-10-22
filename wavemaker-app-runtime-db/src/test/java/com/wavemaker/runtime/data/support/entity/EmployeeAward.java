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
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;

/**
 * Composite-primary-key fixture entity (see {@link EmployeeAwardId}) used to exercise
 * {@code WMGenericDaoImpl}'s {@code CompositeIdentifierStrategy} path.
 */
@Entity
@IdClass(EmployeeAwardId.class)
public class EmployeeAward implements Serializable {

    @Id
    private Long employeeId;

    @Id
    private String awardCode;

    private LocalDate awardedOn;

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(final Long employeeId) {
        this.employeeId = employeeId;
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
