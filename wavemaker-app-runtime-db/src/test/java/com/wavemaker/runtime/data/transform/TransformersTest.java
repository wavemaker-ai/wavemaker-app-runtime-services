/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.transform;

import java.util.Map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * Covers use-case-spec scenario "transform-alias-to-bean-factory".
 *
 * <p><b>Naming gap versus the spec:</b> the spec names the factory method {@code Transformers.aliasToBean}. The
 * real method is {@link Transformers#aliasToMappedClass(Class)} - same responsibility (return a per-type
 * {@link AliasToMappedClassResultTransformer}, caching one instance per class), different name.
 */
class TransformersTest {

    public static class Point {
        private int x;
        private int y;

        public int getX() {
            return x;
        }

        public void setX(final int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(final int y) {
            this.y = y;
        }
    }

    @Test
    void aliasToMappedClassReturnsATransformerConfiguredForTheGivenClassAndReusesItOnSubsequentCalls() {
        WMResultTransformer transformer = Transformers.aliasToMappedClass(Point.class);

        assertInstanceOf(AliasToMappedClassResultTransformer.class, transformer);
        assertSame(transformer, Transformers.aliasToMappedClass(Point.class));
    }

    @Test
    void theReturnedTransformerCorrectlyPopulatesAnInstanceOfTheRequestedType() {
        WMResultTransformer transformer = Transformers.aliasToMappedClass(Point.class);

        Object result = transformer.transformTuple(new Object[] {3, 4}, new String[] {"x", "y"});

        Point point = (Point) result;
        assertEquals(3, point.getX());
        assertEquals(4, point.getY());
    }

    @Test
    void eachReturnTypeGetsTheMatchingTransformer() {
        // A number or primitive needs no transformer; a Map or Object becomes a map, Void (for UPDATE/DELETE)
        // gives nothing, and any other class is filled field by field.
        assertTrue(Transformers.aliasToMappedClassOptional(Integer.class).isEmpty());
        assertTrue(Transformers.aliasToMappedClassOptional(long.class).isEmpty());
        assertSame(AliasToEntityLinkedHashMapTransformer.INSTANCE, Transformers.aliasToMappedClassOptional(Map.class).orElseThrow());
        assertSame(AliasToEntityLinkedHashMapTransformer.INSTANCE, Transformers.aliasToMappedClassOptional(Object.class).orElseThrow());
        assertInstanceOf(VoidTransformer.class, Transformers.aliasToMappedClassOptional(Void.class).orElseThrow());
        assertInstanceOf(AliasToMappedClassResultTransformer.class, Transformers.aliasToMappedClassOptional(Point.class).orElseThrow());
    }
}
