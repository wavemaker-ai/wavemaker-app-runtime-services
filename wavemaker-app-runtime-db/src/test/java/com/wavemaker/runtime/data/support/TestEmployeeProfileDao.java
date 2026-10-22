/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.support;

import org.springframework.orm.hibernate5.HibernateTemplate;

import com.wavemaker.runtime.data.dao.WMGenericDaoImpl;
import com.wavemaker.runtime.data.dao.query.types.wmql.WMQLDefaultTypeHelper;
import com.wavemaker.runtime.data.support.entity.EmployeeProfile;

/**
 * Minimal concrete {@code WMGenericDaoImpl} for {@link EmployeeProfile} (shared-PK one-to-one).
 */
public class TestEmployeeProfileDao extends WMGenericDaoImpl<EmployeeProfile, Long> {

    public TestEmployeeProfileDao(final HibernateTemplate template) {
        super(EmployeeProfile.class, template, new WMQLDefaultTypeHelper());
        init();
    }
}
