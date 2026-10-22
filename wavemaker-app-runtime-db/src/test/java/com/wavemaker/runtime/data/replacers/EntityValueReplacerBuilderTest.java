/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.replacers;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.commons.annotations.WMValueInject;
import com.wavemaker.runtime.commons.variable.Scope;
import com.wavemaker.runtime.commons.variable.VariableType;
import com.wavemaker.runtime.data.support.ServerVariables;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "Value Replacers" section: {@link EntityValueReplacerBuilder} scans an entity class for
 * {@code @WMValueInject} fields and builds one {@link FieldValueReplacer} per field, registered under each scope
 * the annotation lists; {@link EntityValueReplacer#apply} then runs the ones for the current scope. The SERVER
 * values are stubbed ("alice" for user_name, "5" for user_id).
 *
 * <p>That the replaced value is what gets persisted ("replacer-entity-field-before-persist") is checked against a
 * real database in {@code WMEntityInterceptorIntegrationTest}.
 */
class EntityValueReplacerBuilderTest {

    private final EntityValueReplacer replacer = new EntityValueReplacerBuilder().build(ReplaceableEntity.class);

    @BeforeEach
    void stubServerVariables() {
        ServerVariables.use(Map.of("user_name", "alice", "user_id", "5")::get);
    }

    @AfterEach
    void resetServerVariables() {
        ServerVariables.reset();
    }

    @Test
    void descriptorMapCoversEveryDeclaredFieldNotJustAnnotatedOnes() {
        assertEquals(Set.of("createdBy", "modifiedBy", "name"), replacer.getPropertyDescriptorMap().keySet());
    }

    @Test
    void anInsertWritesTheValueToEveryInsertScopedField() {
        ReplaceableEntity entity = entity();

        assertTrue(replacer.apply(new ListenerContext(entity, Scope.INSERT)));

        assertEquals("alice", entity.getCreatedBy());
        assertEquals("alice", entity.getModifiedBy());
        assertEquals("original", entity.getName());
    }

    @Test
    void anUpdateWritesOnlyTheFieldsRegisteredForUpdate() {
        ReplaceableEntity entity = entity();

        assertTrue(replacer.apply(new ListenerContext(entity, Scope.UPDATE)));

        assertEquals("original", entity.getCreatedBy());
        assertEquals("alice", entity.getModifiedBy());
    }

    @Test
    void applyIsANoOpForAScopeNoFieldIsRegisteredFor() {
        ReplaceableEntity entity = entity();

        assertFalse(replacer.apply(new ListenerContext(entity, Scope.DELETE)));

        assertEquals("original", entity.getCreatedBy());
        assertEquals("original", entity.getModifiedBy());
    }

    @Test
    void anEntityWithoutInjectedFieldsAppliesNothingForAnyScope() {
        // Employee has relationship fields of every kind but no @WMValueInject field.
        EntityValueReplacer employeeReplacer = new EntityValueReplacerBuilder().build(Employee.class);

        for (Scope scope : Scope.values()) {
            assertFalse(employeeReplacer.apply(new ListenerContext(new Employee(), scope)), scope.name());
        }
    }

    @Test
    void eachEntityClassGetsItsOwnIndependentlyBuiltReplacerNotASharedFieldScopedOne() {
        // The spec's "replacer-field-scoped-cross-entity" scenario assumes a FieldValueReplacer bound to a field NAME
        // applies to any entity type with a field by that name. Instead, build(Class) scans one class and its
        // FieldValueReplacers hold that class's PropertyDescriptors, so two classes with the same annotated field
        // each get their own replacer, and one class's replacer cannot be applied to the other.
        EntityValueReplacer otherReplacer = new EntityValueReplacerBuilder().build(AnotherReplaceableEntity.class);
        assertEquals(ReplaceableEntity.class,
            replacer.getPropertyDescriptorMap().get("createdBy").getWriteMethod().getDeclaringClass());
        assertEquals(AnotherReplaceableEntity.class,
            otherReplacer.getPropertyDescriptorMap().get("createdBy").getWriteMethod().getDeclaringClass());

        assertThrows(IllegalArgumentException.class,
            () -> otherReplacer.apply(new ListenerContext(new ReplaceableEntity(), Scope.INSERT)));
    }

    @Test
    void aServerValueOfAnotherTypeThanTheFieldCurrentlyFailsWithARawException() {
        // Bug: the value is passed to the setter as returned, never converted to the field's type (the provider
        // keeps the field type but does not use it). user_id is a String, so a numeric "created by" column fails
        // with "argument type mismatch", which FieldValueReplacer does not wrap, and the insert fails.
        EntityValueReplacer numericReplacer = new EntityValueReplacerBuilder().build(NumericCreatorEntity.class);

        assertThrows(IllegalArgumentException.class,
            () -> numericReplacer.apply(new ListenerContext(new NumericCreatorEntity(), Scope.INSERT)));
    }

    private static ReplaceableEntity entity() {
        ReplaceableEntity entity = new ReplaceableEntity();
        entity.setCreatedBy("original");
        entity.setModifiedBy("original");
        entity.setName("original");
        return entity;
    }

    public static class NumericCreatorEntity {

        @WMValueInject(type = VariableType.SERVER, name = "user_id", scopes = {Scope.INSERT})
        private Integer createdById;

        public Integer getCreatedById() {
            return createdById;
        }

        public void setCreatedById(Integer createdById) {
            this.createdById = createdById;
        }
    }
}
