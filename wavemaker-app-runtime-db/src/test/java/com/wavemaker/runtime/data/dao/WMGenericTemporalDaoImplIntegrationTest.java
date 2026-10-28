/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.hibernate.query.SyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.wavemaker.commons.InvalidInputException;
import com.wavemaker.commons.WMRuntimeException;
import com.wavemaker.runtime.data.annotations.TableTemporal;
import com.wavemaker.runtime.data.exception.EntityNotFoundException;
import com.wavemaker.runtime.data.periods.AsOfClause;
import com.wavemaker.runtime.data.periods.BetweenClause;
import com.wavemaker.runtime.data.periods.FromToClause;
import com.wavemaker.runtime.data.periods.PeriodClause;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.TestNonTemporalEmployeeDao;
import com.wavemaker.runtime.data.support.TestTemporalEmployeeDao;
import com.wavemaker.runtime.data.support.entity.Employee;
import com.wavemaker.runtime.data.support.entity.TemporalEmployee;
import com.wavemaker.runtime.data.support.entity.TemporalEmployeeAward;
import com.wavemaker.runtime.data.support.entity.TemporalEmployeeHistory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the "WMGenericTemporalDaoImpl" scenarios from the runtime-db use-case specification against
 * {@link TemporalEmployee} and its history entity {@link TemporalEmployeeHistory}.
 *
 * <p>Temporal tables do not work as the spec describes, and these tests pin what the code really does:
 * <ul>
 *   <li>{@code create}, {@code update} and {@code delete} are inherited unchanged from {@link WMGenericDaoImpl}: plain
 *   insert, in-place update and physical delete, with no period values and nothing written to the history table.</li>
 *   <li>The period methods ({@code findByPeriod}, {@code findByIdAndPeriod} and the period {@code update}/{@code delete}
 *   overloads) generate {@code for business_time as of} / {@code for portion of} HQL. That syntax came from the
 *   custom Hibernate 5.2.17.WM build, which is no longer used; stock Hibernate rejects it with a
 *   {@link SyntaxException}.</li>
 *   <li>{@code count()} goes through the same HQL but swallows the error and returns {@code Integer.MAX_VALUE}.</li>
 * </ul>
 * If temporal support is fixed, these tests fail and should be rewritten to check the specified behaviour.
 */
class WMGenericTemporalDaoImplIntegrationTest extends AbstractHibernateIntegrationTest {

    private TestTemporalEmployeeDao dao;

    @BeforeEach
    void createDao() {
        dao = new TestTemporalEmployeeDao(hibernateTemplate);
    }

    private static TemporalEmployee temporalEmployee(String name, boolean active) {
        TemporalEmployee employee = new TemporalEmployee();
        employee.setName(name);
        employee.setActive(active);
        return employee;
    }

    private static TemporalEmployeeAward award(TemporalEmployee employee, String code) {
        TemporalEmployeeAward award = new TemporalEmployeeAward();
        award.setEmployee(employee);
        award.setAwardCode(code);
        award.setAwardedOn(LocalDate.of(2024, 6, 1));
        return award;
    }

    private static PeriodClause asOfNow() {
        return new AsOfClause(TableTemporal.TemporalType.APPLICATION, new Timestamp(System.currentTimeMillis()));
    }

    private static long count(String entityName) {
        return ((Number) hibernateTemplate.execute(session ->
            session.createQuery("select count(*) from " + entityName).uniqueResult())).longValue();
    }

    /**
     * Asserts the call fails because stock Hibernate rejects the temporal HQL at {@code offendingWord}, and returns the
     * message, which quotes the generated HQL.
     */
    private static String assertTemporalSyntaxRejected(String offendingWord, Executable call) {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, call);
        assertInstanceOf(SyntaxException.class, thrown.getCause());
        assertTrue(thrown.getMessage().contains(offendingWord), thrown.getMessage());
        return thrown.getMessage();
    }

    /** init() resolves the history entity's updatable columns; the generated UPDATE must set every one of them. */
    private static void assertSetsEveryUpdatableProperty(String generatedHql) {
        assertTrue(generatedHql.contains("name = :wm_setter_name"), generatedHql);
        assertTrue(generatedHql.contains("active = :wm_setter_active"), generatedHql);
    }

    // ---- plain CRUD (inherited unchanged from WMGenericDaoImpl) ----

    @Test
    void plainCreateInsertsARowWithNoPeriodValuesBecauseCreateIsNotOverriddenForTemporalEntities() {
        // The spec expects create() to open a valid-from/valid-to period; it is a plain insert.
        Long id = dao.create(temporalEmployee("Alice", true)).getId();
        flushAndClear();

        assertEquals("Alice", dao.findById(id).getName());
        assertEquals(0L, count(TemporalEmployeeHistory.class.getSimpleName()));
    }

    @Test
    void plainUpdateOverwritesInPlaceRatherThanClosingAndInsertingAHistoryRow() {
        // The spec expects update() to close the current row and insert a new version; it updates in place.
        TemporalEmployee employee = dao.create(temporalEmployee("Bob", true));
        flushAndClear();
        employee.setActive(false);

        dao.update(employee);
        flushAndClear();

        assertFalse(dao.findById(employee.getId()).getActive());
        assertEquals(1L, count(TemporalEmployee.class.getSimpleName()), "no second version was inserted");
        assertEquals(0L, count(TemporalEmployeeHistory.class.getSimpleName()));
    }

    @Test
    void plainDeletePhysicallyRemovesTheRowRatherThanSoftClosingIt() {
        // The spec expects delete() to close the current row and keep it as history; it deletes the row.
        TemporalEmployee employee = dao.create(temporalEmployee("Carol", true));
        Long id = employee.getId();

        dao.delete(employee);
        flushAndClear();

        assertThrows(EntityNotFoundException.class, () -> dao.findById(id));
        assertEquals(0L, count(TemporalEmployeeHistory.class.getSimpleName()), "nothing was kept as history");
    }

    @Test
    void findByIdStillWorksBecauseItIsNeverDecoratedWithAPeriodClause() {
        // A plain lookup by id. The spec's "only the current version when several exist" cannot be tested, because
        // nothing ever creates a second version.
        Long id = dao.create(temporalEmployee("Dave", true)).getId();
        flushAndClear();

        assertEquals("Dave", dao.findById(id).getName());
    }

    @Test
    void listIsNotFilteredByPeriodBecauseListNeverRoutesThroughTheDecoratedQueryGenerator() {
        // The spec expects list() to return current rows only. list() never adds the period clause (if it did, it
        // would fail with the same syntax error as count()), so it returns every row. Page size 1 against 2 rows, so
        // the total comes from the count query.
        dao.create(temporalEmployee("Frank", true));
        dao.create(temporalEmployee("Grace", false));
        flushAndClear();

        Page<TemporalEmployee> page = dao.list(PageRequest.of(0, 1));

        assertEquals(2, page.getTotalElements());
    }

    @Test
    void historyTableNeverReceivesARowThroughAnyCurrentlyWorkingCodePath() {
        dao.create(temporalEmployee("Heidi", true));
        flushAndClear();

        assertEquals(0L, count(TemporalEmployeeHistory.class.getSimpleName()));
    }

    @Test
    void countSilentlyReturnsMaxValueInsteadOfSurfacingTheBrokenPeriodClauseHql() {
        // count() appends the same "as of" clause, but QueryHelper swallows the syntax error.
        dao.create(temporalEmployee("Ivan", true));

        assertEquals(Integer.MAX_VALUE, dao.count());
    }

    // ---- period methods (their HQL is rejected by stock Hibernate) ----

    @Test
    void findByPeriodThrowsTheInvalidHqlSyntaxError() {
        dao.create(temporalEmployee("Eve", true));

        assertTemporalSyntaxRejected("business_time",
            () -> dao.findByPeriod(List.of(asOfNow()), null, PageRequest.of(0, 10)));
    }

    @Test
    void findByPeriodWithBetweenClauseThrowsTheSameInvalidHqlSyntaxError() {
        dao.create(temporalEmployee("Eve", true));
        PeriodClause between = new BetweenClause(TableTemporal.TemporalType.APPLICATION,
            Timestamp.valueOf("2020-01-01 00:00:00"), Timestamp.valueOf("2030-01-01 00:00:00"));

        assertTemporalSyntaxRejected("business_time",
            () -> dao.findByPeriod(List.of(between), null, PageRequest.of(0, 10)));
    }

    @Test
    void findByPeriodWithFromToClauseThrowsTheSameInvalidHqlSyntaxError() {
        dao.create(temporalEmployee("Eve", true));
        PeriodClause fromTo = new FromToClause(TableTemporal.TemporalType.APPLICATION,
            Timestamp.valueOf("2020-01-01 00:00:00"), Timestamp.valueOf("2030-01-01 00:00:00"));

        assertTemporalSyntaxRejected("business_time",
            () -> dao.findByPeriod(List.of(fromTo), null, PageRequest.of(0, 10)));
    }

    @Test
    void findByPeriodRejectsAnUnknownSortFieldBeforeBuildingTheQuery() {
        // The one part of the period methods that works: the sort is validated against the history entity first.
        assertThrows(InvalidInputException.class,
            () -> dao.findByPeriod(List.of(asOfNow()), null, PageRequest.of(0, 10, Sort.by("noSuchField"))));
    }

    @Test
    void findByIdAndPeriodThrowsTheSameInvalidHqlSyntaxError() {
        Long id = dao.create(temporalEmployee("Judy", true)).getId();

        assertTemporalSyntaxRejected("business_time",
            () -> dao.findByIdAndPeriod(Map.of("id", id), List.of(asOfNow()), PageRequest.of(0, 10)));
    }

    @Test
    void periodClauseUpdateThrowsTheInvalidHqlSyntaxError() {
        Long id = dao.create(temporalEmployee("Karl", true)).getId();

        String hql = assertTemporalSyntaxRejected("portion",
            () -> dao.update(Map.of("id", id), asOfNow(), temporalEmployee("Karl Updated", false)));

        assertSetsEveryUpdatableProperty(hql);
    }

    @Test
    void periodClauseUpdateByFilterStringThrowsTheSameInvalidHqlSyntaxError() {
        dao.create(temporalEmployee("Mallory", true));

        String hql = assertTemporalSyntaxRejected("portion",
            () -> dao.update(asOfNow(), "name = 'Mallory'", temporalEmployee("Mallory Updated", false)));

        assertSetsEveryUpdatableProperty(hql);
    }

    @Test
    void periodClauseDeleteThrowsTheSameInvalidHqlSyntaxError() {
        Long id = dao.create(temporalEmployee("Liam", true)).getId();

        assertTemporalSyntaxRejected("portion", () -> dao.delete(Map.of("id", id), asOfNow()));
    }

    @Test
    void periodClauseDeleteByFilterStringThrowsTheSameInvalidHqlSyntaxError() {
        dao.create(temporalEmployee("Nora", true));

        assertTemporalSyntaxRejected("portion", () -> dao.delete(asOfNow(), "name = 'Nora'"));
    }

    // ---- relationships ----

    @Test
    void createWithAwardsPersistsBothTheTemporalEmployeeAndItsAwards() {
        TemporalEmployee employee = dao.create(temporalEmployee("Olivia", true));
        hibernateTemplate.persist(award(employee, "EMPLOYEE_OF_MONTH"));
        flushAndClear();

        long awardCount = ((Number) hibernateTemplate.execute(session -> session
            .createQuery("select count(*) from TemporalEmployeeAward a where a.employee.id = :id")
            .setParameter("id", employee.getId())
            .uniqueResult())).longValue();
        assertEquals(1L, awardCount);
    }

    @Test
    void deleteOfATemporalEmployeeThroughTheGenericTemporalDaoCascadesToItsAwards() {
        // The awards collection is lazy: it must be loaded before delete() so the cascade sees the children.
        TemporalEmployee employee = dao.create(temporalEmployee("Peggy", true));
        hibernateTemplate.persist(award(employee, "EMPLOYEE_OF_MONTH"));
        hibernateTemplate.persist(award(employee, "TOP_PERFORMER"));
        flushAndClear();

        TemporalEmployee reloaded = dao.findById(employee.getId());
        reloaded.getAwards().size();
        dao.delete(reloaded);
        flushAndClear();

        assertEquals(0L, count(TemporalEmployeeAward.class.getSimpleName()));
    }

    // ---- setup ----

    @Test
    void initThrowsWhenEntityLacksTableTemporalAnnotation() {
        TestNonTemporalEmployeeDao nonTemporalDao = new TestNonTemporalEmployeeDao(hibernateTemplate);

        WMRuntimeException thrown = assertThrows(WMRuntimeException.class, nonTemporalDao::init);

        // The entity's name is passed as the message argument (the message text itself is only the resource key).
        assertEquals(List.of(Employee.class.getName()), List.of(thrown.getMessageResourceHolder().getArgs()));
    }
}
