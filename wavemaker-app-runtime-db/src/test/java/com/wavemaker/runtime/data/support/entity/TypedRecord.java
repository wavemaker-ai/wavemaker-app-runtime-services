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
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Version;

/**
 * Fixture entity with one field per basic type the runtime maps, so a flush-and-clear round trip can check that
 * each value comes back from the database with the same Java type and value. None of the other fixtures has
 * date-times, timestamps, BLOBs, enums or a version column.
 */
@Entity
public class TypedRecord implements Serializable {

    public enum Status {
        DRAFT, PUBLISHED
    }

    @Id
    @GeneratedValue
    private Long id;

    @Version
    private Integer version;

    private String text;

    private Boolean flag;

    private Character initial;

    private Short smallNumber;

    private Integer wholeNumber;

    private Double ratio;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    private LocalDate day;

    private LocalDateTime moment;

    private Timestamp stamp;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Enumerated(EnumType.ORDINAL)
    private Status ordinalStatus;

    @Lob
    private byte[] payload;

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(final Integer version) {
        this.version = version;
    }

    public String getText() {
        return text;
    }

    public void setText(final String text) {
        this.text = text;
    }

    public Boolean getFlag() {
        return flag;
    }

    public void setFlag(final Boolean flag) {
        this.flag = flag;
    }

    public Character getInitial() {
        return initial;
    }

    public void setInitial(final Character initial) {
        this.initial = initial;
    }

    public Short getSmallNumber() {
        return smallNumber;
    }

    public void setSmallNumber(final Short smallNumber) {
        this.smallNumber = smallNumber;
    }

    public Integer getWholeNumber() {
        return wholeNumber;
    }

    public void setWholeNumber(final Integer wholeNumber) {
        this.wholeNumber = wholeNumber;
    }

    public Double getRatio() {
        return ratio;
    }

    public void setRatio(final Double ratio) {
        this.ratio = ratio;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(final BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getDay() {
        return day;
    }

    public void setDay(final LocalDate day) {
        this.day = day;
    }

    public LocalDateTime getMoment() {
        return moment;
    }

    public void setMoment(final LocalDateTime moment) {
        this.moment = moment;
    }

    public Timestamp getStamp() {
        return stamp;
    }

    public void setStamp(final Timestamp stamp) {
        this.stamp = stamp;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(final Status status) {
        this.status = status;
    }

    public Status getOrdinalStatus() {
        return ordinalStatus;
    }

    public void setOrdinalStatus(final Status ordinalStatus) {
        this.ordinalStatus = ordinalStatus;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(final byte[] payload) {
        this.payload = payload;
    }
}
