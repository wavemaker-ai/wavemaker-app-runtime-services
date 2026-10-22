/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.wavemaker.commons.WMRuntimeException;
import com.wavemaker.runtime.data.expression.AttributeType;
import com.wavemaker.runtime.data.expression.QueryFilter;
import com.wavemaker.runtime.data.expression.Type;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.TestEmployeeDao;
import com.wavemaker.runtime.data.support.entity.Department;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Runs {@link WMGenericDaoImpl#search} once per filter {@link Type} against real rows and asserts exactly which
 * rows match. This is the Criteria API path ({@code CriteriaUtils}) that Hibernate 6 removes, so every operator
 * has to keep returning the same rows after it is rewritten. Filter values are passed as strings with an
 * {@link AttributeType}, the way the client sends them, so the {@code DaoUtils.validateQueryFilters} conversion
 * runs as well.
 *
 * <p>Fixture rows, in id order: Alice, Bob, Carol, "" (empty name) and a row with a null name. A null name is
 * shown as {@code <null>} in the expected lists.
 */
class WMGenericDaoImplSearchFilterIntegrationTest extends AbstractHibernateIntegrationTest {

    private static final String NULL_NAME = "<null>";

    private TestEmployeeDao dao;

    @BeforeEach
    void createDaoAndData() {
        dao = new TestEmployeeDao(hibernateTemplate);

        Department engineering = department("Engineering");
        Department sales = department("Sales");
        employee("Alice", true, "45000", LocalDate.of(2020, 1, 15), engineering);
        employee("Bob", true, "60000", LocalDate.of(2021, 3, 1), sales);
        employee("Carol", false, null, null, engineering);
        employee("", false, "30000", LocalDate.of(2019, 7, 7), null);
        employee(null, false, "70000", LocalDate.of(2022, 12, 31), null);
        flushAndClear();
    }

    private Department department(String name) {
        Department department = new Department();
        department.setName(name);
        hibernateTemplate.save(department);
        return department;
    }

    private void employee(String name, boolean active, String salary, LocalDate hireDate, Department department) {
        Employee employee = new Employee();
        employee.setName(name);
        employee.setActive(active);
        employee.setSalary(salary == null ? null : new BigDecimal(salary));
        employee.setHireDate(hireDate);
        employee.setDepartment(department);
        hibernateTemplate.save(employee);
    }

    private List<String> searchNames(Sort sort, QueryFilter... filters) {
        Page<Employee> page = dao.search(filters, PageRequest.of(0, 20, sort));
        return namesOf(page);
    }

    private static List<String> namesOf(Page<Employee> page) {
        return page.getContent().stream()
            .map(employee -> employee.getName() == null ? NULL_NAME : employee.getName())
            .toList();
    }

    private static QueryFilter filter(String attribute, Object value, Type type, AttributeType attributeType) {
        return new QueryFilter(attribute, value, type, attributeType);
    }

    static Stream<Arguments> singleFilterCases() {
        return Stream.of(
            arguments("startswith is case-insensitive", filter("name", "al", Type.STARTING_WITH, AttributeType.STRING),
                List.of("Alice")),
            arguments("endswith is case-insensitive", filter("name", "OB", Type.ENDING_WITH, AttributeType.STRING),
                List.of("Bob")),
            arguments("containing", filter("name", "ar", Type.CONTAINING, AttributeType.STRING), List.of("Carol")),
            arguments("equals a single value", filter("name", "Bob", Type.EQUALS, AttributeType.STRING),
                List.of("Bob")),
            arguments("equals a list becomes IN", filter("name", List.of("Alice", "Carol"), Type.EQUALS,
                AttributeType.STRING), List.of("Alice", "Carol")),
            arguments("equals an array becomes IN", filter("name", new Object[]{"Bob", "Carol"}, Type.EQUALS,
                AttributeType.STRING), List.of("Bob", "Carol")),
            arguments("equals on a boolean given as a string", filter("active", "false", Type.EQUALS,
                AttributeType.BOOLEAN), List.of("Carol", "", NULL_NAME)),
            arguments("not equals excludes SQL nulls", filter("name", "Bob", Type.NOT_EQUALS, AttributeType.STRING),
                List.of("Alice", "Carol", "")),
            arguments("between is inclusive", filter("salary", List.of("45000", "60000"), Type.BETWEEN,
                AttributeType.BIG_DECIMAL), List.of("Alice", "Bob")),
            arguments("less than", filter("salary", "45000", Type.LESS_THAN, AttributeType.BIG_DECIMAL),
                List.of("")),
            arguments("less than or equals", filter("salary", "45000", Type.LESS_THAN_OR_EQUALS,
                AttributeType.BIG_DECIMAL), List.of("Alice", "")),
            arguments("greater than", filter("salary", "60000", Type.GREATER_THAN, AttributeType.BIG_DECIMAL),
                List.of(NULL_NAME)),
            arguments("greater than or equals", filter("salary", "60000", Type.GREATER_THAN_OR_EQUALS,
                AttributeType.BIG_DECIMAL), List.of("Bob", NULL_NAME)),
            arguments("null", filter("salary", null, Type.NULL, AttributeType.BIG_DECIMAL), List.of("Carol")),
            arguments("empty", filter("name", "", Type.EMPTY, AttributeType.STRING), List.of("")),
            arguments("null or empty", filter("name", "", Type.NULL_OR_EMPTY, AttributeType.STRING),
                List.of("", NULL_NAME)),
            arguments("like is case-sensitive", filter("name", "%o%", Type.LIKE, AttributeType.STRING),
                List.of("Bob", "Carol")),
            arguments("in", filter("name", List.of("Alice", "Bob"), Type.IN, AttributeType.STRING),
                List.of("Alice", "Bob")),
            arguments("is null", filter("name", "null", Type.IS, AttributeType.STRING), List.of(NULL_NAME)),
            arguments("is not null", filter("name", "notnull", Type.IS, AttributeType.STRING),
                List.of("Alice", "Bob", "Carol", "")),
            arguments("is empty", filter("name", "empty", Type.IS, AttributeType.STRING), List.of("")),
            arguments("is null or empty", filter("name", "nullorempty", Type.IS, AttributeType.STRING),
                List.of("", NULL_NAME)),
            arguments("equals on a related property", filter("department.name", "Engineering", Type.EQUALS,
                AttributeType.STRING), List.of("Alice", "Carol"))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("singleFilterCases")
    void eachFilterTypeReturnsExactlyTheMatchingRows(String description, QueryFilter filter, List<String> expected) {
        assertEquals(expected, searchNames(Sort.by("id"), filter));
    }

    @Test
    void aFilterAndASortOnTheSameRelatedEntityShareOneAlias() {
        // The filter creates the "department" alias; the sort must reuse it instead of creating a duplicate,
        // which Hibernate rejects.
        List<String> names = searchNames(Sort.by(Sort.Order.desc("department.name"), Sort.Order.asc("name")),
            filter("department.name", List.of("Engineering", "Sales"), Type.IN, AttributeType.STRING));

        assertEquals(List.of("Bob", "Alice", "Carol"), names);
    }

    @Test
    void sortingDescendingByOneKeyThenAscendingByAnother() {
        List<String> names = searchNames(Sort.by(Sort.Order.desc("active"), Sort.Order.asc("hireDate")),
            filter("hireDate", "notnull", Type.IS, AttributeType.STRING));

        // active=true first (Alice 2020, Bob 2021), then active=false by hire date ("" 2019, null name 2022).
        assertEquals(List.of("Alice", "Bob", "", NULL_NAME), names);
    }

    @Test
    void aLaterPageReturnsTheNextRowsInSortOrderWithTheFullTotal() {
        Page<Employee> secondPage = dao.search(new QueryFilter[0], PageRequest.of(1, 2, Sort.by("salary")));

        assertEquals(5, secondPage.getTotalElements());
        assertEquals(3, secondPage.getTotalPages());
        // Carol's null salary sorts first on HSQLDB and last on Postgres, which shifts every page by one row.
        List<String> expected = isPostgres() ? List.of("Bob", NULL_NAME) : List.of("Alice", "Bob");
        assertEquals(expected, namesOf(secondPage));
    }

    @Test
    void equalsWithANullValueIsRejected() {
        // DaoUtils.validateQueryFilters skips null values, so the EQUALS criterion sees the null and refuses it.
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> dao.search(new QueryFilter[]{filter("name", null, Type.EQUALS, AttributeType.STRING)},
                PageRequest.of(0, 10)));

        assertEquals("Equals expression should not have null value, either collection or array or primitive values "
            + "supported.", exception.getMessage());
    }

    @Test
    void equalsWithAnEmptyListIsRejected() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> dao.search(new QueryFilter[]{filter("name", List.of(), Type.EQUALS, AttributeType.STRING)},
                PageRequest.of(0, 10)));

        assertEquals("Equals expression should have a collection/array of values with at-least one entry.",
            exception.getMessage());
    }

    @Test
    void betweenWithOtherThanTwoValuesIsRejected() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> dao.search(new QueryFilter[]{filter("salary", List.of("1", "2", "3"), Type.BETWEEN,
                AttributeType.BIG_DECIMAL)}, PageRequest.of(0, 10)));

        assertEquals("Between expression should have a collection/array of values with just two entries.",
            exception.getMessage());
    }

    @Test
    void inWithASingleNonCollectionValueIsRejected() {
        assertThrows(WMRuntimeException.class,
            () -> dao.search(new QueryFilter[]{filter("name", "Alice", Type.IN, AttributeType.STRING)},
                PageRequest.of(0, 10)));
    }

    @Test
    void isWithAnUnknownKeywordIsRejected() {
        assertThrows(WMRuntimeException.class,
            () -> dao.search(new QueryFilter[]{filter("name", "something", Type.IS, AttributeType.STRING)},
                PageRequest.of(0, 10)));
    }
}
