/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.support;

import com.wavemaker.runtime.data.dao.WMHibernateTemplate;

import com.wavemaker.runtime.data.dao.WMGenericTemporalDaoImpl;
import com.wavemaker.runtime.data.dao.query.types.wmql.WMQLDefaultTypeHelper;
import com.wavemaker.runtime.data.support.entity.TemporalEmployee;

/**
 * Minimal concrete {@code WMGenericTemporalDaoImpl} for {@link TemporalEmployee}, standing in for a generated
 * app's temporal DAO. Both {@code getTemplate()} and {@code getHistoryTemplate()} point at the same session in
 * this harness - real generated apps use a separate history datasource/template, but nothing here depends on
 * them being different.
 */
public class TestTemporalEmployeeDao extends WMGenericTemporalDaoImpl<TemporalEmployee, Long> {

    public TestTemporalEmployeeDao(final WMHibernateTemplate template) {
        super(TemporalEmployee.class, template, new WMQLDefaultTypeHelper(), template);
        init();
    }
}
