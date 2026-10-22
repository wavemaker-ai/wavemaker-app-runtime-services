/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Backs a "derived column" export field defined by a FreeMarker expression against each row's data (a
 * generated-entity instance, addressed as a JavaBean).
 */
class ExpressionFieldValueProviderTest {

    @Test
    void evaluatesAFreemarkerExpressionAgainstTheRowObjectAsABean() {
        ExpressionFieldValueProvider provider = new ExpressionFieldValueProvider("${name} (${salary})");

        Object value = provider.getValue(new Row("Alice", "50000"));

        assertEquals("Alice (50000)", value);
    }

    @Test
    void anInvalidExpressionAtEvaluationTimeIsSwallowedAndReturnsEmptyString() {
        // "${missing}" is valid FreeMarker syntax, but Row has no such property - by default FreeMarker treats
        // referencing an undefined variable as a runtime error, which getValue() catches and downgrades to "".
        ExpressionFieldValueProvider provider = new ExpressionFieldValueProvider("${missing}");

        Object value = provider.getValue(new Row("Alice", "50000"));

        assertEquals("", value);
    }

    public static class Row {
        private final String name;
        private final String salary;

        public Row(String name, String salary) {
            this.name = name;
            this.salary = salary;
        }

        public String getName() {
            return name;
        }

        public String getSalary() {
            return salary;
        }
    }
}
