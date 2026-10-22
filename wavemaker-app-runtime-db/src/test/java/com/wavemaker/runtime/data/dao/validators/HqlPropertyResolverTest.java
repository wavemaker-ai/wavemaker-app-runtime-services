/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao.validators;

import java.lang.reflect.Field;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.data.support.entity.Department;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backs {@link SortValidator}'s field-existence check (and {@code util/CriteriaUtils}'s sort handling) -
 * validates a dotted property path against an entity's declared fields via reflection, one segment at a time.
 *
 * <p><b>Two discrepancies versus the runtime-db use-case spec's "Sort &amp; Validation" scenarios:</b>
 * <ul>
 *   <li><i>sort-property-resolver-direct/nested</i> assume {@code resolve(...)} returns a Hibernate type
 *   descriptor (e.g. {@code StringType}). The real method is {@code findField(propertyName, entityClass)} and
 *   it returns a plain {@code Optional<java.lang.reflect.Field>} - there is no Hibernate type resolution here at
 *   all; that happens elsewhere (see {@code dao/query/types/HqlParameterTypeResolverTest}).</li>
 *   <li><i>sort-unmapped-property-throws</i> assumes this class throws a descriptive exception for an unmapped
 *   path. It does not - an unmapped segment simply yields {@code Optional.empty()}. The actual throw happens one
 *   layer up, in {@link SortValidator#validate}, which is what wraps an empty {@code Optional} as an
 *   {@code InvalidInputException} (see {@code SortValidatorTest.unknownSortFieldThrowsInvalidInputException}).</li>
 * </ul>
 */
class HqlPropertyResolverTest {

    @Test
    void resolvesADirectFieldReturningItsJavaFieldNotAHibernateType() {
        Optional<Field> field = HqlPropertyResolver.findField("name", Employee.class);

        assertTrue(field.isPresent());
        assertEquals(String.class, field.get().getType());
    }

    @Test
    void resolvesADottedPathThroughARelatedEntityByTraversingItsAssociation() {
        Optional<Field> field = HqlPropertyResolver.findField("department.name", Employee.class);

        assertTrue(field.isPresent());
        assertEquals(String.class, field.get().getType());
    }

    @Test
    void anUnmappedDirectFieldYieldsAnEmptyOptionalRatherThanThrowing() {
        assertTrue(HqlPropertyResolver.findField("bogus", Employee.class).isEmpty());
    }

    @Test
    void anUnmappedSegmentAfterAValidFirstSegmentYieldsAnEmptyOptionalRatherThanThrowing() {
        assertTrue(HqlPropertyResolver.findField("department.bogus", Employee.class).isEmpty());
    }

    @Test
    void resolvesAFieldOnADifferentEntityClass() {
        assertTrue(HqlPropertyResolver.findField("location", Department.class).isPresent());
    }
}
