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
package com.wavemaker.runtime.data;

import java.util.List;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner;
import org.springframework.util.ClassUtils;

import com.wavemaker.runtime.data.model.TemporalHistoryEntity;

/**
 * The entities of the package of a data service, which are the temporal history entities or all the other ones. The
 * history entities have a session factory of their own.
 */
public class FilteredManagedTypesFactoryBean implements FactoryBean<PersistenceManagedTypes> {

    private final String basePackage;

    private final boolean historyEntities;

    @Autowired
    private ResourceLoader resourceLoader;

    public FilteredManagedTypesFactoryBean(String basePackage, boolean historyEntities) {
        this.basePackage = basePackage;
        this.historyEntities = historyEntities;
    }

    @Override
    public PersistenceManagedTypes getObject() {
        PersistenceManagedTypes scanned = new PersistenceManagedTypesScanner(resourceLoader).scan(basePackage);
        List<String> managedClassNames = scanned.getManagedClassNames().stream()
            .filter(className -> TemporalHistoryEntity.class
                .isAssignableFrom(ClassUtils.resolveClassName(className, resourceLoader.getClassLoader())) == historyEntities)
            .toList();
        return PersistenceManagedTypes.of(managedClassNames, scanned.getManagedPackages());
    }

    @Override
    public Class<?> getObjectType() {
        return PersistenceManagedTypes.class;
    }
}
