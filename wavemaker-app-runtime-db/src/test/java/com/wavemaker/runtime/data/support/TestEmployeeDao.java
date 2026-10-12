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
import com.wavemaker.runtime.data.support.entity.Employee;

/**
 * Minimal concrete {@code WMGenericDaoImpl} for {@link Employee}, standing in for a generated app's DAO
 * (there is no Spring container in this test harness to wire {@code @Autowired}/{@code @PostConstruct}).
 */
public class TestEmployeeDao extends WMGenericDaoImpl<Employee, Long> {

    public TestEmployeeDao(final WMHibernateTemplate template) {
        super(Employee.class, template, new WMQLDefaultTypeHelper());
        init();
    }
}
