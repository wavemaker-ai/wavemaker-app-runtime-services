/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.support;

import com.wavemaker.runtime.data.dao.WMSessionTemplate;

import com.wavemaker.runtime.data.dao.WMGenericTemporalDaoImpl;
import com.wavemaker.runtime.data.dao.query.types.wmql.WMQLDefaultTypeHelper;
import com.wavemaker.runtime.data.support.entity.Employee;

/**
 * A temporal DAO wired to {@link Employee}, which is <b>not</b> annotated {@code @TableTemporal} - used to
 * confirm {@code WMGenericTemporalDaoImpl.init()} rejects such a mismatch up front, rather than failing later
 * with a more confusing error.
 */
public class TestNonTemporalEmployeeDao extends WMGenericTemporalDaoImpl<Employee, Long> {

    public TestNonTemporalEmployeeDao(final WMSessionTemplate template) {
        super(Employee.class, template, new WMQLDefaultTypeHelper(), template);
    }
}
