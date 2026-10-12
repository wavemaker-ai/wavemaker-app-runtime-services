/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.support;

import com.wavemaker.runtime.data.dao.WMHibernateTemplate;

import com.wavemaker.runtime.data.dao.WMGenericDaoImpl;
import com.wavemaker.runtime.data.dao.query.types.wmql.WMQLDefaultTypeHelper;
import com.wavemaker.runtime.data.support.entity.EmployeeAward;
import com.wavemaker.runtime.data.support.entity.EmployeeAwardId;

/**
 * Minimal concrete {@code WMGenericDaoImpl} for the composite-key {@link EmployeeAward} entity.
 */
public class TestEmployeeAwardDao extends WMGenericDaoImpl<EmployeeAward, EmployeeAwardId> {

    public TestEmployeeAwardDao(final WMHibernateTemplate template) {
        super(EmployeeAward.class, template, new WMQLDefaultTypeHelper());
        init();
    }
}
