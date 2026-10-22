/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.support.entity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import com.wavemaker.runtime.data.annotations.TableTemporal;

/**
 * Fixture for {@code WMGenericTemporalDaoImpl} tests - its history side is {@link TemporalEmployeeHistory}.
 * {@code name}/{@code active} are mirrored on both sides under the same property names, since
 * {@code WMGenericTemporalDaoImpl.init()} maps a history property back onto the parent entity by name.
 *
 * <p>{@link #awards} is a plain, non-temporal one-to-many child (see {@link TemporalEmployeeAward}), added
 * specifically to verify that cascade-delete still works correctly when the parent is {@code @TableTemporal} -
 * {@code create}/{@code update}/{@code delete} are inherited unchanged from {@code WMGenericDaoImpl} regardless,
 * so this is the same cascade mechanism already proven on {@link Project}/{@link Task}, re-confirmed here on a
 * temporal parent rather than a new code path.
 */
@Entity
@TableTemporal(value = TableTemporal.TemporalType.APPLICATION, historyClass = TemporalEmployeeHistory.class)
public class TemporalEmployee implements Serializable {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    private Boolean active;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TemporalEmployeeAward> awards = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public List<TemporalEmployeeAward> getAwards() {
        return awards;
    }

    public void setAwards(List<TemporalEmployeeAward> awards) {
        this.awards = awards;
    }
}
