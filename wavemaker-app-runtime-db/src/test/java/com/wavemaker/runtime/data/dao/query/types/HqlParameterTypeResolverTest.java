/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao.query.types;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HqlParameterTypeResolverTest {

    @Test
    void alwaysReturnsEmptyBecauseHibernateResolvesHqlParamTypesItself() {
        HqlParameterTypeResolver resolver = new HqlParameterTypeResolver();

        assertTrue(resolver.resolveType("anyParam").isEmpty());
        assertEquals(resolver.resolveType("x"), resolver.resolveType("y"));
    }
}
