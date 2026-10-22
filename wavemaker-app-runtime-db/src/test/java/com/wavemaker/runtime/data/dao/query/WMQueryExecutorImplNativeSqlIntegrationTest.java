/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao.query;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.wavemaker.runtime.data.annotations.ColumnAlias;
import com.wavemaker.runtime.data.model.JavaType;
import com.wavemaker.runtime.data.model.queries.QueryParameter;
import com.wavemaker.runtime.data.model.queries.QueryType;
import com.wavemaker.runtime.data.model.queries.RuntimeQuery;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the "WMQueryExecutor - Native SQL" scenarios from the runtime-db use-case specification, against
 * {@link WMQueryExecutorImpl}'s native-SQL path ({@link RuntimeQuery} with {@code nativeSql=true}). Named native
 * queries mapped onto DTOs, and binding typed parameters (lists, nulls, dates), are covered in the sections at the
 * end of the class.
 *
 * <p>"native-sqlserver-datetime-type" from the spec isn't practically exercisable end-to-end against an
 * HSQLDB-backed harness (no real SQL Server to route to) - it's covered instead by the existing unit test
 * {@code WMQLSqlServerTypeHelperTest}, which tests the type-mapping logic directly.
 */
class WMQueryExecutorImplNativeSqlIntegrationTest extends AbstractHibernateIntegrationTest {

    private WMQueryExecutorImpl executor;

    @BeforeEach
    void createExecutor() throws ReflectiveOperationException {
        executor = new WMQueryExecutorImpl();
        executor.setTemplate(hibernateTemplate);
        Method init = WMQueryExecutorImpl.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(executor);
    }

    private Employee newEmployee(String name, boolean active) {
        Employee employee = new Employee();
        employee.setName(name);
        employee.setActive(active);
        hibernateTemplate.save(employee);
        return employee;
    }

    @Test
    void anAdHocNativeSqlQueryReturnsRowsAsListOfMapsKeyedByColumnName() {
        newEmployee("Alice", true);
        newEmployee("Bob", false);
        flushAndClear();

        // The alias is double-quoted deliberately: an unquoted reference gets folded to each database's own
        // default case (HSQLDB upper-cases it, Postgres lower-cases it), which would make the row key below
        // differ depending on which database the suite happens to be running against.
        RuntimeQuery nativeQuery = new RuntimeQuery(
            "select name as \"name\", active as \"active\" from Employee order by name", true, QueryType.SELECT,
            List.of());
        // Page size 1 against 2 rows, so the total comes from the wrapper count query.
        Page<Object> page = executor.executeRuntimeQuery(nativeQuery, PageRequest.of(0, 1));

        assertEquals(2, page.getTotalElements());
        assertEquals(Map.of("name", "Alice", "active", true), page.getContent().get(0));
    }

    @Test
    void aNamedNativeQueryRequestedAsMapsReturnsOneMapPerRowKeyedByAlias() {
        newEmployee("Alice", true, "45000", null);
        newEmployee("Bob", true, "60000", null);
        newEmployee("Carol", false, "70000", null);
        flushAndClear();

        Page<Map> page = executor.executeNamedQuery(NATIVE_QUERY, Map.of("active", true), Map.class,
            PageRequest.of(0, 10));

        List<Map> rows = page.getContent().stream()
            .sorted(Comparator.comparing(row -> (String) row.get("emp_name"))).toList();
        assertEquals(List.of("Alice", "Bob"), rows.stream().map(row -> row.get("emp_name")).toList());
        assertEquals(0, new BigDecimal("45000").compareTo((BigDecimal) rows.get(0).get("salary")));
        assertEquals(Set.of("emp_name", "salary"), rows.get(0).keySet());
    }

    @Test
    void aPaginatedNativeQueryDerivesItsTotalFromAWrapperCountQuery() {
        for (int i = 0; i < 5; i++) {
            newEmployee("Employee" + i, true);
        }
        flushAndClear();

        RuntimeQuery nativeQuery = new RuntimeQuery("select name as \"name\" from Employee order by name", true,
            QueryType.SELECT, List.of());
        Page<Object> secondPage = executor.executeRuntimeQuery(nativeQuery, PageRequest.of(1, 2));

        assertEquals(5, secondPage.getTotalElements());
        assertEquals(List.of(Map.of("name", "Employee2"), Map.of("name", "Employee3")), secondPage.getContent());
    }

    @Test
    void aPageBeyondTheLastIsEmptyButReportsTheRealTotal() {
        for (int i = 0; i < 3; i++) {
            newEmployee("Employee" + i, true);
        }
        flushAndClear();

        RuntimeQuery nativeQuery = new RuntimeQuery("select name as \"name\" from Employee", true, QueryType.SELECT,
            List.of());
        Page<Object> page = executor.executeRuntimeQuery(nativeQuery, PageRequest.of(5, 2));

        assertEquals(3, page.getTotalElements());
        assertTrue(page.getContent().isEmpty());
    }

    @Test
    void aSortOnANativeQueryThatAlreadyHasOrderByWorksBecauseTheQueryIsWrapped() {
        // Contrast with HQL, where the sort is appended as a second ORDER BY and fails: a sorted native query is
        // wrapped as "select * from (<query>) order by <alias>", so its own ORDER BY stays inside. The alias is upper
        // case because WMHSQLDialect does not quote the wrapping ORDER BY alias.
        newEmployee("Bob", true);
        newEmployee("Carol", true);
        newEmployee("Alice", true);
        flushAndClear();

        RuntimeQuery nativeQuery = new RuntimeQuery("select name as \"NAME\" from Employee order by id", true,
            QueryType.SELECT, List.of());
        Page<Object> page = executor.executeRuntimeQuery(nativeQuery, PageRequest.of(0, 10, Sort.by("NAME")));

        assertEquals(List.of(Map.of("NAME", "Alice"), Map.of("NAME", "Bob"), Map.of("NAME", "Carol")),
            page.getContent());
    }

    @Test
    void aNativeDmlStatementReturnsTheJdbcAffectedRowCount() {
        newEmployee("Alice", true);
        newEmployee("Bob", true);
        flushAndClear();

        RuntimeQuery update = new RuntimeQuery(
            "update Employee set active = false", true, QueryType.UPDATE, List.of());

        int updated = executor.executeRuntimeQueryForUpdate(update);
        flushAndClear();

        assertEquals(2, updated);
        Long stillActive = hibernateTemplate.execute(session -> session
            .createQuery("select count(e) from Employee e where e.active = true", Long.class).uniqueResult());
        assertEquals(0L, stillActive);
    }

    // ---- Named native queries mapped onto a DTO ----
    //
    // Runs the registered Employee.native.byActive query (name as "emp_name", salary as "salary", filtered by
    // :active) through executeNamedQuery(name, params, Class, Pageable) and maps each row onto a DTO, end to end. This
    // is the real result-set path of AliasToMappedClassResultTransformer, Hibernate's ResultTransformer, which
    // Hibernate 6 replaces with TupleTransformer.
    //
    // Sorting uses Employee.native.byActiveUpperCaseAliases instead: a sorted native query is wrapped as
    // "select * from (...) order by <alias>", and WMHSQLDialect does not quote that alias, so on HSQLDB it only
    // matches an upper-case alias.

    private static final String NATIVE_QUERY = "Employee.native.byActive";
    private static final String SORTABLE_NATIVE_QUERY = "Employee.native.byActiveUpperCaseAliases";

    /** Maps both columns; the name through {@code @ColumnAlias}, the salary by matching field name. */
    public static class NameAndSalary {
        @ColumnAlias("emp_name")
        private String employeeName;

        private BigDecimal salary;

        public String getEmployeeName() {
            return employeeName;
        }

        public void setEmployeeName(String employeeName) {
            this.employeeName = employeeName;
        }

        public BigDecimal getSalary() {
            return salary;
        }

        public void setSalary(BigDecimal salary) {
            this.salary = salary;
        }
    }

    /** Declares the salary as a Double, so the JDBC BigDecimal has to be converted. */
    public static class NameAndDoubleSalary {
        @ColumnAlias("emp_name")
        private String employeeName;

        private Double salary;

        public String getEmployeeName() {
            return employeeName;
        }

        public void setEmployeeName(String employeeName) {
            this.employeeName = employeeName;
        }

        public Double getSalary() {
            return salary;
        }

        public void setSalary(Double salary) {
            this.salary = salary;
        }
    }

    /** Has no field for the salary column, and one field the query never returns. */
    public static class NameOnly {
        @ColumnAlias("emp_name")
        private String employeeName;

        private String department;

        public String getEmployeeName() {
            return employeeName;
        }

        public void setEmployeeName(String employeeName) {
            this.employeeName = employeeName;
        }

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }
    }

    /** Row type for {@link #SORTABLE_NATIVE_QUERY}, whose aliases are upper case. */
    public static class UpperCaseAliasRow {
        @ColumnAlias("EMP_NAME")
        private String employeeName;

        @ColumnAlias("SALARY")
        private BigDecimal salary;

        public String getEmployeeName() {
            return employeeName;
        }

        public void setEmployeeName(String employeeName) {
            this.employeeName = employeeName;
        }

        public BigDecimal getSalary() {
            return salary;
        }

        public void setSalary(BigDecimal salary) {
            this.salary = salary;
        }
    }

    /** Saves the active employees Carol (70000), Alice (45000) and Bob (60000). */
    private void saveThreeActiveEmployeesWithSalaries() {
        newEmployee("Carol", true, "70000", null);
        newEmployee("Alice", true, "45000", null);
        newEmployee("Bob", true, "60000", null);
    }

    private static <T> List<T> byName(List<T> rows, Function<T, String> name) {
        return rows.stream().sorted(Comparator.comparing(name)).toList();
    }

    @Test
    void eachRowIsMappedOntoTheDtoThroughColumnAliasAndFieldName() {
        saveThreeActiveEmployeesWithSalaries();
        newEmployee("Dave", false, "99000", null);
        flushAndClear();

        Page<NameAndSalary> page = executor.executeNamedQuery(NATIVE_QUERY, Map.of("active", true),
            NameAndSalary.class, PageRequest.of(0, 10));

        List<NameAndSalary> rows = byName(page.getContent(), NameAndSalary::getEmployeeName);
        assertEquals(List.of("Alice", "Bob", "Carol"), rows.stream().map(NameAndSalary::getEmployeeName).toList());
        assertEquals(0, new BigDecimal("45000").compareTo(rows.get(0).getSalary()));
        assertEquals(0, new BigDecimal("60000").compareTo(rows.get(1).getSalary()));
        assertEquals(0, new BigDecimal("70000").compareTo(rows.get(2).getSalary()));
    }

    @Test
    void aJdbcValueIsConvertedToTheDtoFieldsDeclaredType() {
        newEmployee("Alice", true, "45000", null);
        flushAndClear();

        Page<NameAndDoubleSalary> page = executor.executeNamedQuery(NATIVE_QUERY, Map.of("active", true),
            NameAndDoubleSalary.class, PageRequest.of(0, 10));

        NameAndDoubleSalary alice = page.getContent().get(0);
        assertEquals("Alice", alice.getEmployeeName());
        assertEquals(Double.valueOf(45000d), alice.getSalary());
    }

    @Test
    void aColumnWithoutAMatchingFieldIsIgnoredAndAnUnreturnedFieldStaysNull() {
        newEmployee("Dave", false, "99000", null);
        flushAndClear();

        Page<NameOnly> page = executor.executeNamedQuery(NATIVE_QUERY, Map.of("active", false), NameOnly.class,
            PageRequest.of(0, 10));

        assertEquals(1, page.getContent().size());
        assertEquals("Dave", page.getContent().get(0).getEmployeeName());
        assertNull(page.getContent().get(0).getDepartment());
    }

    @Test
    void sortingByADtoFieldDescendingUsesItsSqlAliasAndPagesInThatOrder() {
        saveThreeActiveEmployeesWithSalaries();
        flushAndClear();

        // "employeeName" has to be translated back to its "EMP_NAME" alias in the wrapping ORDER BY.
        Page<UpperCaseAliasRow> secondPage = executor.executeNamedQuery(SORTABLE_NATIVE_QUERY, Map.of("active", true),
            UpperCaseAliasRow.class, PageRequest.of(1, 1, Sort.by(Sort.Order.desc("employeeName"))));

        assertEquals(1, secondPage.getContent().size());
        assertEquals("Bob", secondPage.getContent().get(0).getEmployeeName());
    }

    @Test
    void sortingByASecondDtoFieldOrdersTheWholeResult() {
        saveThreeActiveEmployeesWithSalaries();
        flushAndClear();

        Page<UpperCaseAliasRow> page = executor.executeNamedQuery(SORTABLE_NATIVE_QUERY, Map.of("active", true),
            UpperCaseAliasRow.class, PageRequest.of(0, 10, Sort.by(Sort.Order.desc("salary"))));

        assertEquals(List.of("Carol", "Bob", "Alice"),
            page.getContent().stream().map(UpperCaseAliasRow::getEmployeeName).toList());
        assertEquals(0, new BigDecimal("70000").compareTo(page.getContent().get(0).getSalary()));
    }

    @Test
    void withoutARegisteredCountQueryTheTotalFallsBackToIntegerMaxValue() {
        saveThreeActiveEmployeesWithSalaries();
        flushAndClear();

        // There is no "Employee.native.byActive__count" query, so PaginatedQueryCallback cannot count and reports
        // Integer.MAX_VALUE as the total. Callers that page on this must not trust getTotalElements().
        Page<NameAndSalary> page = executor.executeNamedQuery(NATIVE_QUERY, Map.of("active", true),
            NameAndSalary.class, PageRequest.of(0, 10));

        assertEquals(3, page.getContent().size());
        assertEquals(Integer.MAX_VALUE, page.getTotalElements());
    }

    // ---- Runtime queries with typed QueryParameters ----
    //
    // These bind real QueryParameters through executeRuntimeQuery, so RuntimeParametersProvider,
    // QueryHelper.prepareParam/convertValue and ParametersProvider.configure all run. Values are given as strings, the
    // way the designer sends them, and converted by the Hibernate-backed converters in JavaTypeConverters.
    // Each query pages with size 1 against 2 matching rows, so the total is only right if the count query binds the
    // same parameters. The same tests for HQL are in WMQueryExecutorImplHqlIntegrationTest.

    /**
     * Saves, in id order: Alice (active, 45000, 2020-01-15), Alice (inactive, 50000, 2021-06-01), Bob (active, 45000,
     * 2020-01-15) and Carol (inactive, no salary, no hire date). Returns the ids in that order.
     */
    private List<Long> saveTypedParameterFixture() {
        List<Long> ids = List.of(
            newEmployee("Alice", true, "45000", LocalDate.of(2020, 1, 15)),
            newEmployee("Alice", false, "50000", LocalDate.of(2021, 6, 1)),
            newEmployee("Bob", true, "45000", LocalDate.of(2020, 1, 15)),
            newEmployee("Carol", false, null, null));
        flushAndClear();
        return ids;
    }

    private Long newEmployee(String name, boolean active, String salary, LocalDate hireDate) {
        Employee employee = newEmployee(name, active);
        employee.setSalary(salary == null ? null : new BigDecimal(salary));
        employee.setHireDate(hireDate);
        return employee.getId();
    }

    private Page<Object> runWithParameter(String where, QueryParameter parameter) {
        String query = "select name as \"name\" from Employee where " + where + " order by id";
        return executor.executeRuntimeQuery(
            new RuntimeQuery(query, true, QueryType.SELECT, List.of(parameter)), PageRequest.of(0, 1));
    }

    /** A native query row is a map from column alias to value. */
    private static Object valueOf(Object row, String column) {
        return ((Map<?, ?>) row).get(column);
    }

    @Test
    void aStringParameterIsBoundAndCounted() {
        saveTypedParameterFixture();

        Page<Object> page = runWithParameter("name = :name", new QueryParameter("name", JavaType.STRING, false, "Alice"));

        assertEquals(2, page.getTotalElements());
        assertEquals("Alice", valueOf(page.getContent().get(0), "name"));
    }

    @Test
    void aBooleanParameterGivenAsAStringIsConvertedAndBound() {
        saveTypedParameterFixture();

        Page<Object> page = runWithParameter("active = :active",
            new QueryParameter("active", JavaType.BOOLEAN, false, "true"));

        assertEquals(2, page.getTotalElements());
        assertEquals("Alice", valueOf(page.getContent().get(0), "name"));
    }

    @Test
    void aBigDecimalParameterGivenAsAStringIsConvertedAndBound() {
        saveTypedParameterFixture();

        Page<Object> page = runWithParameter("salary = :salary",
            new QueryParameter("salary", JavaType.BIG_DECIMAL, false, "45000.00"));

        assertEquals(2, page.getTotalElements());
        assertEquals("Alice", valueOf(page.getContent().get(0), "name"));
    }

    @Test
    void aDateParameterGivenAsAStringIsConvertedAndBound() {
        saveTypedParameterFixture();

        Page<Object> page = runWithParameter("hireDate = :hireDate",
            new QueryParameter("hireDate", JavaType.DATE, false, "2020-01-15"));

        assertEquals(2, page.getTotalElements());
        assertEquals("Alice", valueOf(page.getContent().get(0), "name"));
    }

    @Test
    void aListParameterExpandsIntoAnInClause() {
        List<Long> ids = saveTypedParameterFixture();
        // Bob's and Carol's ids, as strings.
        List<Object> bobAndCarol = List.of(String.valueOf(ids.get(2)), String.valueOf(ids.get(3)));

        Page<Object> page = runWithParameter("id in (:ids)", new QueryParameter("ids", JavaType.LONG, true, bobAndCarol));

        assertEquals(2, page.getTotalElements());
        assertEquals("Bob", valueOf(page.getContent().get(0), "name"));
    }

    @Test
    void aNullParameterIsBoundAsSqlNullAndMatchesNothingWithEquals() {
        saveTypedParameterFixture();

        // "= NULL" never matches in SQL, not even Carol's null salary.
        Page<Object> page = runWithParameter("salary = :salary",
            new QueryParameter("salary", JavaType.BIG_DECIMAL, false, null));

        assertEquals(0, page.getTotalElements());
        assertEquals(0, page.getContent().size());
    }

    @Test
    void theSecondPageOfAParameterizedQueryReturnsTheNextRowInOrder() {
        saveTypedParameterFixture();
        RuntimeQuery query = new RuntimeQuery("select salary as \"salary\" from Employee where name = :name order by id", true, QueryType.SELECT,
            List.of(new QueryParameter("name", JavaType.STRING, false, "Alice")));

        Page<Object> firstPage = executor.executeRuntimeQuery(query, PageRequest.of(0, 1));
        Page<Object> secondPage = executor.executeRuntimeQuery(query, PageRequest.of(1, 1));

        assertEquals(2, secondPage.getTotalElements());
        assertEquals(1, secondPage.getContent().size());
        assertEquals(0, new BigDecimal("45000").compareTo((BigDecimal) valueOf(firstPage.getContent().get(0), "salary")));
        assertEquals(0, new BigDecimal("50000").compareTo((BigDecimal) valueOf(secondPage.getContent().get(0), "salary")));
    }
}
