/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.util;

import java.util.List;

import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.model.ReferenceType;
import com.wavemaker.runtime.data.model.returns.ReturnProperty;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Extracts design-time result metadata (name/type/list-ness) from a real, already-executed HQL {@code Query} -
 * used by Studio's query designer to describe a custom query's output shape.
 */
class HQLQueryUtilsIntegrationTest extends AbstractHibernateIntegrationTest {

    @Test
    void multiColumnSelectProducesOnePrimitiveReturnPropertyPerAliasedColumn() {
        List<ReturnProperty> properties = hibernateTemplate.execute(session -> {
            Query<?> query = session.createQuery("select e.name as empName, e.salary as empSalary from Employee e");
            return HQLQueryUtils.extractMetaForHql(query);
        });

        assertEquals(2, properties.size());
        assertEquals("empName", properties.get(0).getName());
        assertEquals(ReferenceType.PRIMITIVE, properties.get(0).getFieldType().getType());
        assertEquals(String.class.getCanonicalName(), properties.get(0).getFieldType().getTypeRef());
        assertEquals("empSalary", properties.get(1).getName());
    }

    @Test
    void plainEntitySelectProducesASingleUnnamedEntityReturnProperty() {
        // The single-entity, no-explicit-alias case suppresses the property name (see extractMetaForHql) so
        // Studio doesn't generate a needless wrapper model class just to hold one entity reference.
        List<ReturnProperty> properties = hibernateTemplate.execute(session -> {
            Query<?> query = session.createQuery("from Employee");
            return HQLQueryUtils.extractMetaForHql(query);
        });

        assertEquals(1, properties.size());
        assertNull(properties.get(0).getName());
        assertEquals(ReferenceType.ENTITY, properties.get(0).getFieldType().getType());
        assertEquals(Employee.class.getName(), properties.get(0).getFieldType().getTypeRef());
    }
}
