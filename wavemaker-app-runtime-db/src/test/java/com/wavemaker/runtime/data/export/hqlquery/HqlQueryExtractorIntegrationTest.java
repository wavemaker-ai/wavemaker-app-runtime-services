/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.export.hqlquery;

import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code isFirstRow()} works around {@code org.hibernate.ScrollableResults} not reliably supporting
 * {@code isFirst()}/{@code getRow()} across dialects - it tracks position itself instead, incrementing on every
 * {@link HqlQueryExtractor#next()} call regardless of whether that call actually found another row.
 */
class HqlQueryExtractorIntegrationTest extends AbstractHibernateIntegrationTest {

    private Employee alice;
    private Employee bob;

    private HqlQueryExtractor extractorOverEmployeesOrderedById() {
        Session session = hibernateTemplate.getSessionFactory().getCurrentSession();
        alice = new Employee();
        alice.setName("Alice");
        session.persist(alice);
        bob = new Employee();
        bob.setName("Bob");
        session.persist(bob);
        session.flush();

        ScrollableResults results = session.createQuery("from Employee e order by e.id", Employee.class)
            .scroll(ScrollMode.FORWARD_ONLY);
        return new HqlQueryExtractor(results);
    }

    @Test
    void nextAdvancesThroughEveryRowAndReportsFalseOnceExhausted() throws Exception {
        HqlQueryExtractor extractor = extractorOverEmployeesOrderedById();

        assertTrue(extractor.next());
        assertTrue(extractor.next());
        assertFalse(extractor.next());
    }

    @Test
    void isFirstRowIsTrueOnlyImmediatelyAfterTheFirstNextCall() throws Exception {
        HqlQueryExtractor extractor = extractorOverEmployeesOrderedById();

        extractor.next();
        assertTrue(extractor.isFirstRow());

        extractor.next();
        assertFalse(extractor.isFirstRow());
    }

    @Test
    void getCurrentRowReturnsTheEntityAtTheCurrentCursorPosition() throws Exception {
        HqlQueryExtractor extractor = extractorOverEmployeesOrderedById();

        extractor.next();
        assertEquals(alice.getId(), ((Employee) extractor.getCurrentRow()).getId());

        extractor.next();
        assertEquals(bob.getId(), ((Employee) extractor.getCurrentRow()).getId());
    }
}
