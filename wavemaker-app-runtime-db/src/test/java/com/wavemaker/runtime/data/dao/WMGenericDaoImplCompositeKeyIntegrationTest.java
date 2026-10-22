/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.exception.EntityNotFoundException;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.TestEmployeeAwardDao;
import com.wavemaker.runtime.data.support.entity.EmployeeAward;
import com.wavemaker.runtime.data.support.entity.EmployeeAwardId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers scenario "dao-create-composite-pk" from the runtime-db use-case specification: creating an entity with
 * a composite primary key ({@code @IdClass}) populates every key field, and {@code findById} with the composite
 * key class locates the row via {@code CompositeIdentifierStrategy}. Nothing in the module's original test suite
 * exercised this path at all before this fixture was added.
 */
class WMGenericDaoImplCompositeKeyIntegrationTest extends AbstractHibernateIntegrationTest {

    private TestEmployeeAwardDao dao;

    @BeforeEach
    void createDao() {
        dao = new TestEmployeeAwardDao(hibernateTemplate);
    }

    @Test
    void createWithACompositePrimaryKeyPopulatesEveryKeyFieldAndFindByIdLocatesItByTheCompositeKey() {
        EmployeeAward award = new EmployeeAward();
        award.setEmployeeId(42L);
        award.setAwardCode("EMP_OF_MONTH");
        award.setAwardedOn(LocalDate.of(2024, 3, 1));

        dao.create(award);
        flushAndClear();

        EmployeeAward found = dao.findById(new EmployeeAwardId(42L, "EMP_OF_MONTH"));
        assertEquals(LocalDate.of(2024, 3, 1), found.getAwardedOn());
    }

    @Test
    void findByIdWithAnUnmatchedCompositeKeyThrows() {
        assertThrows(EntityNotFoundException.class, () -> dao.findById(new EmployeeAwardId(1L, "NO_SUCH_AWARD")));
    }

    @Test
    void aCompositeKeyMustMatchOnEveryPartNotJustOne() {
        dao.create(award(42L, "EMP_OF_MONTH"));
        dao.create(award(43L, "TEAM_PLAYER"));
        flushAndClear();

        // Each half of the key exists on some row, but no row has both.
        assertThrows(EntityNotFoundException.class, () -> dao.findById(new EmployeeAwardId(42L, "TEAM_PLAYER")));
        assertEquals(LocalDate.of(2024, 3, 1), dao.findById(new EmployeeAwardId(43L, "TEAM_PLAYER")).getAwardedOn());
    }

    @Test
    void deletingByACompositeKeyRemovesOnlyThatRow() {
        dao.create(award(42L, "EMP_OF_MONTH"));
        dao.create(award(42L, "TEAM_PLAYER"));
        flushAndClear();

        dao.delete(dao.findById(new EmployeeAwardId(42L, "EMP_OF_MONTH")));
        flushAndClear();

        assertThrows(EntityNotFoundException.class, () -> dao.findById(new EmployeeAwardId(42L, "EMP_OF_MONTH")));
        assertEquals("TEAM_PLAYER", dao.findById(new EmployeeAwardId(42L, "TEAM_PLAYER")).getAwardCode());
    }

    private static EmployeeAward award(long employeeId, String code) {
        EmployeeAward award = new EmployeeAward();
        award.setEmployeeId(employeeId);
        award.setAwardCode(code);
        award.setAwardedOn(LocalDate.of(2024, 3, 1));
        return award;
    }
}
