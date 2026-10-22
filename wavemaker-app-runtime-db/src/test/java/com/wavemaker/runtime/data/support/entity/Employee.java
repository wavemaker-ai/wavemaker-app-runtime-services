/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.support.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedNativeQuery;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToOne;

/**
 * Fixture entity used by runtime-db integration tests to exercise CRUD, criteria/search, joins and aggregation.
 * The named queries exist only to give {@code WMQueryExecutorImpl}/{@code SessionBackedQueryProvider} tests a
 * real registered query to look up by name. {@code Employee.native.byActive} exists specifically so
 * {@code NamedQueryExporterCallbackTest} can exercise the native-query branch of {@code NamedQueryExporterCallback}
 * (it's distinguished from the HQL query at runtime purely by which annotation registered it, not by name).
 * It deliberately projects a flat, DTO-shaped row rather than declaring {@code resultClass = Employee.class} -
 * a native query that hydrates the full entity collides with the export path's own result transformer
 * (verified directly: it produces a garbled CSV walking into {@code LocalDate}'s internal fields instead of
 * real columns).
 */
@Entity
@NamedQueries({
    @NamedQuery(name = "Employee.byActive", query = "from Employee e where e.active = :active"),
    @NamedQuery(name = "Employee.byActive__count", query = "select count(*) from Employee e where e.active = :active"),
    @NamedQuery(name = "Employee.deactivateAll", query = "update Employee e set e.active = false"),
    // A multi-column HQL projection, for exporting rows that are not entities.
    @NamedQuery(name = "Employee.namesAndSalariesByActive", query = "select e.name as employeeName, e.salary as salary from Employee e where e.active = :active")
})
// The alias is double-quoted deliberately: an UNQUOTED alias gets folded to each database's own default case
// (HSQLDB upper-cases it, Postgres lower-cases it) - a double-quoted alias is preserved exactly as written on
// both, giving a result-set column label that's the same regardless of which database is running the suite.
@NamedNativeQuery(name = "Employee.native.byActive", query = "select name as \"emp_name\", salary as \"salary\" from Employee where active = :active")
// Same query with upper-case aliases, for sorted paging. A sorted native query is wrapped as
// "select * from (...) order by <alias>", and WMHSQLDialect deliberately does not quote identifiers, so on HSQLDB that
// ORDER BY alias is folded to upper case and only matches an upper-case alias. Postgres quotes it, so it still matches.
@NamedNativeQuery(name = "Employee.native.byActiveUpperCaseAliases", query = "select name as \"EMP_NAME\", salary as \"SALARY\" from Employee where active = :active")
public class Employee implements Serializable {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    private BigDecimal salary;

    private LocalDate hireDate;

    private Boolean active;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    // Self-referencing hierarchy (parent-child) - ON DELETE semantics are a DAO/DB-level concern tested via
    // the real deletion scenarios; this is purely the Hibernate-side association.
    @ManyToOne
    @JoinColumn(name = "manager_id")
    private Employee manager;

    // One-to-One, shared-PK style (contrast with badge below) - owning side is EmployeeProfile, which is
    // why this is "mappedBy" here and cascades/orphanRemoval live on this, the inverse side: deleting an
    // Employee through Hibernate should delete its profile with it, same as the DB's own ON DELETE CASCADE.
    @OneToOne(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private EmployeeProfile profile;

    // One-to-One, separate-unique-FK style (contrast with profile above).
    @OneToOne(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private EmployeeBadge badge;

    // Self-referencing Many-to-Many (symmetric peer links) - distinct from manager's directed hierarchy.
    @ManyToMany
    @JoinTable(name = "employee_peer",
        joinColumns = @JoinColumn(name = "employee_id"),
        inverseJoinColumns = @JoinColumn(name = "peer_id"))
    private List<Employee> peers = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

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

    public LocalDate getHireDate() {
        return hireDate;
    }

    public void setHireDate(final LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(final Boolean active) {
        this.active = active;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(final Department department) {
        this.department = department;
    }

    public Employee getManager() {
        return manager;
    }

    public void setManager(final Employee manager) {
        this.manager = manager;
    }

    public EmployeeProfile getProfile() {
        return profile;
    }

    public void setProfile(final EmployeeProfile profile) {
        this.profile = profile;
    }

    public EmployeeBadge getBadge() {
        return badge;
    }

    public void setBadge(final EmployeeBadge badge) {
        this.badge = badge;
    }

    public List<Employee> getPeers() {
        return peers;
    }

    public void setPeers(final List<Employee> peers) {
        this.peers = peers;
    }
}
