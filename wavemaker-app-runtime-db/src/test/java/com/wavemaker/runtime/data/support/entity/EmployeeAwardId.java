/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.support.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * {@code @IdClass} for {@link EmployeeAward} - exercises {@code CompositeIdentifierStrategy}, which nothing in
 * the pre-existing test suite touched at all before this fixture was added.
 */
public class EmployeeAwardId implements Serializable {

    private Long employeeId;

    private String awardCode;

    public EmployeeAwardId() {
    }

    public EmployeeAwardId(final Long employeeId, final String awardCode) {
        this.employeeId = employeeId;
        this.awardCode = awardCode;
    }

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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EmployeeAwardId)) {
            return false;
        }
        final EmployeeAwardId that = (EmployeeAwardId) o;
        return Objects.equals(employeeId, that.employeeId) && Objects.equals(awardCode, that.awardCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(employeeId, awardCode);
    }
}
