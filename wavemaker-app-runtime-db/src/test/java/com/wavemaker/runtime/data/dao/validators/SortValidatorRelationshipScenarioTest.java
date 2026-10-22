/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao.validators;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.wavemaker.runtime.data.support.entity.EmployeeAward;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Re-covers {@link SortValidator} against two relationship-fixture shapes {@code SortValidatorTest} never
 * exercises - it only ever validates against {@link Employee}, which has a single surrogate {@code @GeneratedValue}
 * {@code Long} id.
 */
class SortValidatorRelationshipScenarioTest {

    private final SortValidator validator = new SortValidator();

    @Test
    void sortingByAnIdClassCompositeKeysOwnScalarFieldPassesValidation() {
        // EmployeeAward has no single surrogate id at all - its identity is two @Id scalar fields
        // (employeeId/awardCode) declared directly on the entity, backing an @IdClass. Confirms
        // HqlPropertyResolver.findField resolves a composite key's own field exactly like any other scalar
        // field - nothing about @IdClass changes how it's found by reflection.
        Pageable pageable = PageRequest.of(0, 10, Sort.by("awardCode"));

        assertDoesNotThrow(() -> validator.validate(pageable, EmployeeAward.class));
    }

    @Test
    void sortingByAToManyCollectionAssociationPassesValidationEvenThoughItCannotActuallyBeOrderedByInHql() {
        // A real, confirmed gap: SortValidator only checks "does a field by this name exist" via
        // HqlPropertyResolver.findField - it has no concept of a field being sortable at all. A to-many
        // collection association (Employee.peers, a @ManyToMany) passes this check exactly like a plain scalar
        // field would, even though "order by peers" is not valid HQL - a collection property can't be used as
        // a sort key without a join. That failure, if it ever happened, would only surface downstream at actual
        // query execution (QueryHelper.applySortingForHqlQuery), never here. Existence-checking, not
        // sortability-checking, is what this validator actually does.
        Pageable pageable = PageRequest.of(0, 10, Sort.by("peers"));

        assertDoesNotThrow(() -> validator.validate(pageable, Employee.class));
    }
}
