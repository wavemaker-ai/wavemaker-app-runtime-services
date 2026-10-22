/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.TestTypedRecordDao;
import com.wavemaker.runtime.data.support.entity.TypedRecord;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Writes one value of every basic type through {@link WMGenericDaoImpl}, flushes and clears the session, then
 * reloads it, so every assertion is about what the database returned rather than the cached Java object. This is
 * the type-mapping safety net for the Hibernate upgrade: a change in how any of these types is bound or read
 * (scale, precision, enum storage, LOB handling, versioning) fails here.
 */
class WMGenericDaoImplTypeRoundTripIntegrationTest extends AbstractHibernateIntegrationTest {

    // Microsecond precision: the most both HSQLDB and Postgres TIMESTAMP columns keep.
    private static final LocalDateTime MOMENT = LocalDateTime.of(2024, 2, 29, 23, 59, 58, 123_456_000);
    private static final Timestamp STAMP = Timestamp.valueOf(LocalDateTime.of(1999, 12, 31, 0, 0, 1, 654_321_000));

    private TestTypedRecordDao dao;

    @BeforeEach
    void createDao() {
        dao = new TestTypedRecordDao(hibernateTemplate);
    }

    private TypedRecord fullyPopulated() {
        TypedRecord record = new TypedRecord();
        record.setText("héllo wörld");
        record.setFlag(Boolean.TRUE);
        record.setInitial('Z');
        record.setSmallNumber((short) -32768);
        record.setWholeNumber(Integer.MAX_VALUE);
        record.setRatio(0.125d);
        record.setAmount(new BigDecimal("45000"));
        record.setDay(LocalDate.of(2024, 2, 29));
        record.setMoment(MOMENT);
        record.setStamp(STAMP);
        record.setStatus(TypedRecord.Status.PUBLISHED);
        record.setOrdinalStatus(TypedRecord.Status.PUBLISHED);
        record.setPayload(new byte[]{0, 1, -1, 127, -128});
        return record;
    }

    @Test
    void everyBasicTypeComesBackFromTheDatabaseWithTheSameJavaTypeAndValue() {
        TypedRecord original = fullyPopulated();
        Long id = dao.create(original).getId();
        flushAndClear();

        TypedRecord reloaded = dao.findById(id);

        assertNotSame(original, reloaded);
        assertEquals("héllo wörld", reloaded.getText());
        assertEquals(Boolean.TRUE, reloaded.getFlag());
        assertEquals(Character.valueOf('Z'), reloaded.getInitial());
        assertEquals(Short.valueOf((short) -32768), reloaded.getSmallNumber());
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), reloaded.getWholeNumber());
        assertEquals(Double.valueOf(0.125d), reloaded.getRatio());
        assertEquals(LocalDate.of(2024, 2, 29), reloaded.getDay());
        assertEquals(MOMENT, reloaded.getMoment());
        assertEquals(STAMP, reloaded.getStamp());
        assertEquals(TypedRecord.Status.PUBLISHED, reloaded.getStatus());
        assertEquals(TypedRecord.Status.PUBLISHED, reloaded.getOrdinalStatus());
        assertArrayEquals(new byte[]{0, 1, -1, 127, -128}, reloaded.getPayload());
    }

    @Test
    void aBigDecimalIsReadBackAtTheColumnScaleNotTheScaleItWasWrittenWith() {
        Long id = dao.create(fullyPopulated()).getId();
        flushAndClear();

        BigDecimal amount = dao.findById(id).getAmount();

        // Written as 45000 (scale 0), the column is scale 2: equal by value, but BigDecimal.equals also compares
        // scale, so callers using equals() see a difference. Pin both facts.
        assertEquals(0, new BigDecimal("45000").compareTo(amount));
        assertEquals(2, amount.scale());
    }

    @Test
    void enumsAreStoredByNameOrByOrdinalAsMapped() {
        Long id = dao.create(fullyPopulated()).getId();
        flushAndClear();

        Object[] stored = hibernateTemplate.execute(session -> (Object[]) session.createNativeQuery(
                "select status, ordinalStatus from TypedRecord where id = :id")
            .setParameter("id", id)
            .uniqueResult());

        assertEquals("PUBLISHED", stored[0]);
        assertEquals(TypedRecord.Status.PUBLISHED.ordinal(), ((Number) stored[1]).intValue());
    }

    @Test
    void nullValuesRoundTripAsNullForEveryNullableType() {
        Long id = dao.create(new TypedRecord()).getId();
        flushAndClear();

        TypedRecord reloaded = dao.findById(id);

        assertNull(reloaded.getText());
        assertNull(reloaded.getFlag());
        assertNull(reloaded.getInitial());
        assertNull(reloaded.getSmallNumber());
        assertNull(reloaded.getWholeNumber());
        assertNull(reloaded.getRatio());
        assertNull(reloaded.getAmount());
        assertNull(reloaded.getDay());
        assertNull(reloaded.getMoment());
        assertNull(reloaded.getStamp());
        assertNull(reloaded.getStatus());
        assertNull(reloaded.getOrdinalStatus());
        assertNull(reloaded.getPayload());
    }

    @Test
    void anUpdateIsPersistedAndIncrementsTheVersion() {
        Long id = dao.create(fullyPopulated()).getId();
        flushAndClear();
        TypedRecord loaded = dao.findById(id);
        Integer versionBefore = loaded.getVersion();

        loaded.setText("changed");
        loaded.setStatus(TypedRecord.Status.DRAFT);
        loaded.setPayload(new byte[]{42});
        dao.update(loaded);
        flushAndClear();

        TypedRecord reloaded = dao.findById(id);
        assertEquals("changed", reloaded.getText());
        assertEquals(TypedRecord.Status.DRAFT, reloaded.getStatus());
        assertArrayEquals(new byte[]{42}, reloaded.getPayload());
        assertEquals(versionBefore + 1, reloaded.getVersion());
    }

    @Test
    void updatingAStaleCopyFailsWithAnOptimisticLockingException() {
        Long id = dao.create(fullyPopulated()).getId();
        flushAndClear();
        TypedRecord stale = dao.findById(id);
        hibernateTemplate.evict(stale);

        TypedRecord current = dao.findById(id);
        current.setText("first writer");
        dao.update(current);
        flushAndClear();

        stale.setText("second writer");
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            dao.update(stale);
            hibernateTemplate.flush();
        });
    }
}
