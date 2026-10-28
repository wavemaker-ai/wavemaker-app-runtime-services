/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.support;

import com.wavemaker.runtime.data.dao.WMSessionTemplate;

import com.wavemaker.runtime.data.dao.WMGenericDaoImpl;
import com.wavemaker.runtime.data.dao.query.types.wmql.WMQLDefaultTypeHelper;
import com.wavemaker.runtime.data.support.entity.Task;

/**
 * Minimal concrete {@code WMGenericDaoImpl} for {@link Task}.
 */
public class TestTaskDao extends WMGenericDaoImpl<Task, Long> {

    public TestTaskDao(final WMSessionTemplate template) {
        super(Task.class, template, new WMQLDefaultTypeHelper());
        init();
    }
}
