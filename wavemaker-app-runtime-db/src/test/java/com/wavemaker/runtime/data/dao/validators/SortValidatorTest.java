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

import com.wavemaker.commons.InvalidInputException;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Guards against HQL sort-clause injection via an unvalidated sort property name - see
 * {@code WMGenericDaoImpl.search}/{@code list}, which call this before building any query.
 */
class SortValidatorTest {

    private final SortValidator validator = new SortValidator();

    @Test
    void nullPageableIsANoOp() {
        assertDoesNotThrow(() -> validator.validate(null, Employee.class));
    }

    @Test
    void unsortedPageableIsANoOp() {
        assertDoesNotThrow(() -> validator.validate(PageRequest.of(0, 10), Employee.class));
    }

    @Test
    void validDirectSortFieldPasses() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("name"));

        assertDoesNotThrow(() -> validator.validate(pageable, Employee.class));
    }

    @Test
    void validDottedSortFieldPasses() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("department.name"));

        assertDoesNotThrow(() -> validator.validate(pageable, Employee.class));
    }

    @Test
    void unknownSortFieldThrowsInvalidInputException() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("notAField"));

        assertThrows(InvalidInputException.class, () -> validator.validate(pageable, Employee.class));
    }
}
