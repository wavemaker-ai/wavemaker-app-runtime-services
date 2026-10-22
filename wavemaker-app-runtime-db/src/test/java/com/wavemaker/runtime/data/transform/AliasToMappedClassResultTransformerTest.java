/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.transform;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.BeanWrapperImpl;

import com.wavemaker.runtime.data.annotations.ColumnAlias;
import com.wavemaker.runtime.data.exception.TypeMappingException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * Covers use-case-spec scenario "transform-dto-mapping": each row becomes an instance of the target class, and each
 * value is converted to its field's type when the database returns a different one (an Integer for a Long field,
 * a Timestamp for a LocalDateTime field, ...).
 */
class AliasToMappedClassResultTransformerTest {

    public static class EmployeeSummary {
        private String name;
        private BigDecimal salary;

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public BigDecimal getSalary() {
            return salary;
        }

        public void setSalary(final BigDecimal salary) {
            this.salary = salary;
        }
    }

    private final AliasToMappedClassResultTransformer transformer =
        new AliasToMappedClassResultTransformer(EmployeeSummary.class);

    @Test
    void transformTupleInstantiatesTheTargetClassWithFieldsPopulatedFromMatchingAliases() {
        String[] aliases = {"name", "salary"};
        Object[] tuple = {"Alice", new BigDecimal("50000")};

        Object result = transformer.transformTuple(tuple, aliases);

        assertInstanceOf(EmployeeSummary.class, result);
        EmployeeSummary summary = (EmployeeSummary) result;
        assertEquals("Alice", summary.getName());
        assertEquals(new BigDecimal("50000"), summary.getSalary());
    }

    @Test
    void transformTupleIgnoresAnAliasWithNoMatchingFieldWithoutThrowing() {
        String[] aliases = {"name", "unmappedColumn"};
        Object[] tuple = {"Bob", "some value with no matching field"};

        Object result = transformer.transformTuple(tuple, aliases);

        assertEquals("Bob", ((EmployeeSummary) result).getName());
    }

    static Stream<Arguments> conversions() {
        return Stream.of(
            Arguments.of("Integer to Long", "id", 5, 5L),
            Arguments.of("BigInteger to Long", "id", BigInteger.TEN, 10L),
            Arguments.of("BigDecimal to Long", "id", new BigDecimal("7"), 7L),
            Arguments.of("Long to Integer", "count", 5L, 5),
            Arguments.of("Integer to Short", "small", 3, (short) 3),
            Arguments.of("BigDecimal to Double", "ratio", new BigDecimal("0.5"), 0.5d),
            Arguments.of("Double to BigDecimal", "amount", 0.1d, new BigDecimal("0.1")),
            Arguments.of("Integer to String", "text", 42, "42"),
            Arguments.of("String to Character", "initial", "Q", 'Q'),
            Arguments.of("Integer 1 to Boolean", "flag", 1, true),
            Arguments.of("Timestamp to LocalDateTime", "moment", Timestamp.valueOf("2024-03-15 10:20:30"),
                LocalDateTime.of(2024, 3, 15, 10, 20, 30)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("conversions")
    void aValueOfAnotherTypeIsConvertedToTheFieldsType(String description, String field, Object dbValue, Object expected) {
        Object row = new AliasToMappedClassResultTransformer(TypedRow.class)
            .transformTuple(new Object[]{dbValue}, new String[]{field});

        assertEquals(expected, new BeanWrapperImpl(row).getPropertyValue(field));
    }

    @Test
    void aTextBooleanIsCurrentlyReadWithHibernatesYesNoRule() {
        // Bug: a String reaching a Boolean field goes through Hibernate's BooleanTypeDescriptor, which only treats
        // "Y" (and "T") as true, so a column that stores booleans as 'true'/'false' text is read as false without
        // any error. This is Hibernate-internal behaviour, so it is likely to change with the Hibernate upgrade.
        assertEquals(true, flagFrom("Y"));
        assertEquals(false, flagFrom("N"));
        assertEquals(false, flagFrom("true"));
    }

    @Test
    void aLocalDateEnumOrPrimitiveFieldCurrentlyFailsToMap() {
        // Bug: only the WaveMaker JavaTypes are converted. A LocalDate or enum field gets the raw value (a
        // java.sql.Date, a String) and the setter rejects it; a null for a primitive field cannot be set either.
        // Generated DTOs use java.sql.Date and wrapper types, so this only affects hand-written DTOs.
        AliasToMappedClassResultTransformer typedRows = new AliasToMappedClassResultTransformer(TypedRow.class);

        assertThrows(TypeMappingException.class, () -> typedRows.transformTuple(
            new Object[]{java.sql.Date.valueOf("2024-03-15")}, new String[]{"day"}));
        assertThrows(TypeMappingException.class, () -> typedRows.transformTuple(
            new Object[]{"GREEN"}, new String[]{"color"}));
        assertThrows(TypeMappingException.class, () -> typedRows.transformTuple(
            new Object[]{null}, new String[]{"primitive"}));
    }

    @Test
    void aColumnAliasOnTheGetterMapsTheColumnToTheField() {
        AliasToMappedClassResultTransformer typedRows = new AliasToMappedClassResultTransformer(TypedRow.class);

        TypedRow row = (TypedRow) typedRows.transformTuple(new Object[]{"g"}, new String[]{"getter_alias"});

        assertEquals("g", row.getViaGetter());
        // The same mapping is used to translate between column and field names, e.g. for sorting.
        assertEquals("viaGetter", typedRows.aliasToFieldName("getter_alias"));
        assertEquals("getter_alias", typedRows.aliasFromFieldName("viaGetter"));
        assertTrue(typedRows.containsField("viaGetter"));
        assertFalse(typedRows.containsField("noSuchField"));
        assertEquals("noSuchColumn", typedRows.aliasToFieldName("noSuchColumn"));
    }

    @Test
    void aListOfChildRowsBecomesAListOfChildObjects() {
        TypedRow row = (TypedRow) new AliasToMappedClassResultTransformer(TypedRow.class).transformTuple(
            new Object[]{List.of(Map.of("name", "a"), Map.of("name", "b"))}, new String[]{"children"});

        assertEquals(List.of("a", "b"), row.getChildren().stream().map(Child::getName).toList());
    }

    private static Object flagFrom(String dbValue) {
        Object row = new AliasToMappedClassResultTransformer(TypedRow.class)
            .transformTuple(new Object[]{dbValue}, new String[]{"flag"});
        return ((TypedRow) row).getFlag();
    }

    public enum Color {
        RED, GREEN
    }

    public static class TypedRow {

        private Long id;

        private Integer count;

        private Short small;

        private Double ratio;

        private BigDecimal amount;

        private String text;

        private Character initial;

        private Boolean flag;

        private LocalDateTime moment;

        private LocalDate day;

        private Color color;

        private int primitive;

        private String viaGetter;

        private List<Child> children;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public Short getSmall() {
            return small;
        }

        public void setSmall(Short small) {
            this.small = small;
        }

        public Double getRatio() {
            return ratio;
        }

        public void setRatio(Double ratio) {
            this.ratio = ratio;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public Character getInitial() {
            return initial;
        }

        public void setInitial(Character initial) {
            this.initial = initial;
        }

        public Boolean getFlag() {
            return flag;
        }

        public void setFlag(Boolean flag) {
            this.flag = flag;
        }

        public LocalDateTime getMoment() {
            return moment;
        }

        public void setMoment(LocalDateTime moment) {
            this.moment = moment;
        }

        public LocalDate getDay() {
            return day;
        }

        public void setDay(LocalDate day) {
            this.day = day;
        }

        public Color getColor() {
            return color;
        }

        public void setColor(Color color) {
            this.color = color;
        }

        public int getPrimitive() {
            return primitive;
        }

        public void setPrimitive(int primitive) {
            this.primitive = primitive;
        }

        @ColumnAlias("getter_alias")
        public String getViaGetter() {
            return viaGetter;
        }

        public void setViaGetter(String viaGetter) {
            this.viaGetter = viaGetter;
        }

        public List<Child> getChildren() {
            return children;
        }

        public void setChildren(List<Child> children) {
            this.children = children;
        }
    }

    public static class Child {

        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
