/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.data.domain.PageRequest;

import com.wavemaker.runtime.commons.WMAppContext;
import com.wavemaker.runtime.data.dao.query.WMQueryExecutorImpl;
import com.wavemaker.runtime.data.model.DesignServiceResponse;
import com.wavemaker.runtime.data.model.ReferenceType;
import com.wavemaker.runtime.data.model.queries.QueryType;
import com.wavemaker.runtime.data.model.queries.RuntimeQuery;
import com.wavemaker.runtime.data.model.returns.ReturnProperty;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.entity.Department;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Re-covers {@code QueryDesignServiceImplIntegrationTest}'s "schema metadata is extracted from a real HQL query"
 * guarantee against a relationship-field projection - that class only ever runs {@code "from Employee e order by
 * e.name"}, a plain whole-entity select. {@link com.wavemaker.runtime.data.util.HQLQueryUtils#extractMetaForHql}
 * has a branch keyed on {@code Hibernate Type.isAssociationType()} (tagging a column {@code ReferenceType.ENTITY}
 * instead of {@code PRIMITIVE}) and a special case that nulls a property's name when the query has exactly one
 * return column of entity type - both read directly from the source, neither previously proven against a query
 * that selects a relationship field rather than the entity's own row.
 */
class QueryDesignServiceImplRelationshipIntegrationTest extends AbstractHibernateIntegrationTest {

    private static final String SERVICE_ID = "test";

    @BeforeAll
    static void wireWmAppContextWithRealCollaborators() throws Exception {
        Constructor<WMAppContext> constructor = WMAppContext.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        if (WMAppContext.getInstance() == null) {
            constructor.newInstance();
        }

        WMQueryExecutorImpl queryExecutor = new WMQueryExecutorImpl();
        queryExecutor.setTemplate(hibernateTemplate);
        Method init = WMQueryExecutorImpl.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(queryExecutor);

        StaticApplicationContext context = new StaticApplicationContext();
        context.getDefaultListableBeanFactory().registerSingleton(SERVICE_ID + "TransactionManager", transactionManager);
        context.getDefaultListableBeanFactory().registerSingleton(SERVICE_ID + "WMQueryExecutor", queryExecutor);
        context.getDefaultListableBeanFactory().registerSingleton(SERVICE_ID + "SessionFactory", sessionFactory);

        WMAppContext.getInstance().setApplicationContext(context);
    }

    private static RuntimeQuery hqlRuntimeQuery(String queryString) {
        RuntimeQuery query = new RuntimeQuery();
        query.setQueryString(queryString);
        query.setNativeSql(false);
        query.setType(QueryType.SELECT);
        return query;
    }

    private void seedOneEmployeeWithADepartment() {
        Department engineering = new Department();
        engineering.setName("Engineering");
        engineering.setLocation("Building 2");
        sessionFactory.getCurrentSession().persist(engineering);

        Employee employee = new Employee();
        employee.setName("Alice");
        employee.setDepartment(engineering);
        sessionFactory.getCurrentSession().persist(employee);
        sessionFactory.getCurrentSession().flush();
        transactionManager.commit(transactionStatus);
    }

    @Test
    void selectingARelationshipFieldDirectlyMarksItAsAnEntityReturnTypeAndNullsItsNameJustLikeSelectingTheWholeEntityDoes() {
        seedOneEmployeeWithADepartment();

        QueryDesignServiceImpl service = new QueryDesignServiceImpl();
        RuntimeQuery query = hqlRuntimeQuery("select e.department from Employee e");

        DesignServiceResponse response = service.testRunQuery(SERVICE_ID, query, PageRequest.of(0, 10));

        List<ReturnProperty> properties = response.getReturnProperties();
        assertEquals(1, properties.size());
        assertEquals(ReferenceType.ENTITY, properties.get(0).getFieldType().getType());
        // Same name-nulling special case HQLQueryUtils applies to a bare "from Employee e" select - it only
        // checks "exactly one entity-typed return column", not "the column is the queried entity itself", so a
        // single relationship-field projection gets the exact same treatment.
        assertNull(properties.get(0).getName());
    }

    @Test
    void aMixedProjectionOfAScalarFieldAndARelationshipFieldTypesEachColumnCorrectlyButNeitherGetsThePropertyPathAsItsName() {
        seedOneEmployeeWithADepartment();

        QueryDesignServiceImpl service = new QueryDesignServiceImpl();
        RuntimeQuery query = hqlRuntimeQuery("select e.name, e.department from Employee e");

        DesignServiceResponse response = service.testRunQuery(SERVICE_ID, query, PageRequest.of(0, 10));

        List<ReturnProperty> properties = response.getReturnProperties();
        assertEquals(2, properties.size());
        // The name-nulling special case only fires with exactly ONE return column, so it doesn't apply here -
        // but a genuinely different finding turned up in writing this: with no explicit "as" alias in the HQL,
        // Hibernate's query.getReturnAliases() does NOT fall back to the selected property path ("name"/
        // "department") the way one might assume - it returns the plain positional index as a string ("0"/"1")
        // instead. Each column is still typed correctly by HQLQueryUtils.extractMetaForHql though - this only
        // affects the NAME, not the TYPE classification the rest of this test class is actually about.
        assertEquals("0", properties.get(0).getName());
        assertEquals(ReferenceType.PRIMITIVE, properties.get(0).getFieldType().getType());
        assertEquals("1", properties.get(1).getName());
        assertEquals(ReferenceType.ENTITY, properties.get(1).getFieldType().getType());
    }
}
