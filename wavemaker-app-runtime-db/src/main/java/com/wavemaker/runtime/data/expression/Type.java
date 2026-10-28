/*******************************************************************************
 * Copyright (C) 2022-2023 WaveMaker, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.wavemaker.runtime.data.expression;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import com.wavemaker.commons.MessageResource;
import com.wavemaker.commons.WMRuntimeException;
import com.wavemaker.runtime.data.util.QueryParserConstants;

/**
 * @author <a href="mailto:sunil.pulugula@wavemaker.com">Sunil Kumar</a>
 * @since 17/2/16
 */
public enum Type implements Criteria {

    STARTING_WITH("startswith") {
        @Override
        public Predicate predicate(final CriteriaBuilder builder, final Expression<?> name, final Object value) {
            return builder.like(builder.lower(name.as(String.class)), (String.valueOf(value) + "%").toLowerCase());
        }
    }, ENDING_WITH("endswith") {
        @Override
        public Predicate predicate(final CriteriaBuilder builder, final Expression<?> name, final Object value) {
            return builder.like(builder.lower(name.as(String.class)), ("%" + String.valueOf(value)).toLowerCase());
        }
    }, CONTAINING("containing") {
        @Override
        public Predicate predicate(final CriteriaBuilder builder, final Expression<?> name, final Object value) {
            return builder.like(builder.lower(name.as(String.class)), ("%" + String.valueOf(value) + "%").toLowerCase());
        }
    }, EQUALS("=") {
        @Override
        public Predicate predicate(final CriteriaBuilder builder, final Expression<?> name, final Object value) {
            if (value == null) {
                throw new IllegalArgumentException("Equals expression should not have null value, either collection or array or primitive values supported.");
            }

            Predicate criterion;
            if (value instanceof Collection values) {
                if (values.isEmpty()) {
                    throw new IllegalArgumentException("Equals expression should have a collection/array of values with at-least one entry.");
                }
                criterion = name.in(values);
            } else if (value.getClass().isArray()) {
                final Object[] values = (Object[]) value;
                if (values.length == 0) {
                    throw new IllegalArgumentException("Equals expression should have a collection/array of values with at-least one entry.");
                }
                criterion = name.in(values);
            } else {
                criterion = builder.equal(name, value);
            }
            return criterion;
        }
    }, NOT_EQUALS("!=") {
        @Override
        public Predicate predicate(final CriteriaBuilder builder, final Expression<?> name, final Object value) {
            return builder.notEqual(name, value);
        }
    }, BETWEEN("between") {
        @Override
        public Predicate predicate(final CriteriaBuilder builder, final Expression<?> name, final Object value) {
            Predicate criterion;
            if (value instanceof Collection collection) {
                if (collection.size() != 2) {
                    throw new IllegalArgumentException("Between expression should have a collection/array of values with just two entries.");
                }

                Iterator iterator = collection.iterator();
                criterion = builder.between((Expression) name, (Comparable) iterator.next(), (Comparable) iterator.next());
            } else if (value.getClass().isArray()) {
                Object[] array = (Object[]) value;
                if (array.length != 2) {
                    throw new IllegalArgumentException("Between expression should have a array/array of values with just two entries.");
                }

                criterion = builder.between((Expression) name, (Comparable) array[0], (Comparable) array[1]);
            } else {
                throw new IllegalArgumentException("Between expression should have a collection/array of values with just two entries.");
            }
            return criterion;
        }
    }, LESS_THAN("<") {
        @Override
        public Predicate predicate(final CriteriaBuilder builder, final Expression<?> name, final Object value) {
            return builder.lessThan((Expression) name, (Comparable) value);
        }
    }, LESS_THAN_OR_EQUALS("<=") {
        @Override
        public Predicate predicate(final CriteriaBuilder builder, final Expression<?> name, final Object value) {
            return builder.lessThanOrEqualTo((Expression) name, (Comparable) value);
        }
    }, GREATER_THAN(">") {
        @Override
        public Predicate predicate(final CriteriaBuilder builder, final Expression<?> name, final Object value) {
            return builder.greaterThan((Expression) name, (Comparable) value);
        }
    }, GREATER_THAN_OR_EQUALS(">=") {
        @Override
        public Predicate predicate(final CriteriaBuilder builder, final Expression<?> name, final Object value) {
            return builder.greaterThanOrEqualTo((Expression) name, (Comparable) value);
        }
    }, NULL("null") {
        @Override
        public Predicate predicate(final CriteriaBuilder builder, final Expression<?> name, final Object value) {
            return builder.isNull(name);
        }
    }, EMPTY("empty") {
        @Override
        public Predicate predicate(final CriteriaBuilder builder, final Expression<?> name, final Object value) {
            return builder.equal(name, "");
        }
    }, LIKE("like") {
        @Override
        public Predicate predicate(final CriteriaBuilder builder, final Expression<?> name, final Object value) {
            return builder.like(name.as(String.class), String.valueOf(value));
        }
    }, IN("in") {
        @Override
        public Predicate predicate(final CriteriaBuilder builder, final Expression<?> name, final Object value) {
            if (value instanceof Collection) {
                return name.in((Collection) value);
            }
            throw new WMRuntimeException(MessageResource.create("com.wavemaker.runtime.unexpected.value.type"), value.getClass());
        }
    }, NULL_OR_EMPTY("nullorempty") {
        @Override
        public Predicate predicate(final CriteriaBuilder builder, final Expression<?> name, final Object value) {
            return builder.or(builder.equal(name, ""), builder.isNull(name));
        }
    }, IS("is") {
        @Override
        public Predicate predicate(final CriteriaBuilder builder, final Expression<?> name, final Object value) {
            String castedValue = (String) value;
            if (QueryParserConstants.NULL.equalsIgnoreCase(castedValue)) {
                return NULL.predicate(builder, name, value);
            } else if (QueryParserConstants.NOTNULL.equalsIgnoreCase(castedValue)) {
                return builder.not(NULL.predicate(builder, name, value));
            } else if (QueryParserConstants.NULL_OR_EMPTY.equalsIgnoreCase(castedValue)) {
                return NULL_OR_EMPTY.predicate(builder, name, value);
            } else if (QueryParserConstants.EMPTY.equalsIgnoreCase(castedValue)) {
                return EMPTY.predicate(builder, name, value);
            }
            throw new WMRuntimeException(MessageResource.create("com.wavemaker.runtime.invalid.IS.operator.value"), value.getClass());
        }
    };

    static Map<String, Type> nameVsType = new HashMap<>();

    static {
        for (Type type : Type.values()) {
            nameVsType.put(type.getName(), type);
        }
    }

    private String name;

    Type(String name) {
        this.name = name;
    }

    public static Type valueFor(String typeName) {
        return nameVsType.get(typeName.toLowerCase());

    }

    public String getName() {
        return name;
    }
}
