package com.wavemaker.runtime.data.regsvc;

import java.io.Serializable;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import com.wavemaker.runtime.commons.annotations.WMValueInject;
import com.wavemaker.runtime.commons.variable.Scope;
import com.wavemaker.runtime.commons.variable.VariableType;

@Entity
public class AuditedWidget implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;

    @WMValueInject(type = VariableType.SERVER, name = "user_name", scopes = {Scope.INSERT, Scope.UPDATE})
    private String modifiedBy;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }
}
