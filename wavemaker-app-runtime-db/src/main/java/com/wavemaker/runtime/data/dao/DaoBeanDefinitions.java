/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc.
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
package com.wavemaker.runtime.data.dao;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.ResolvableType;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

import com.wavemaker.commons.WMRuntimeException;

/**
 * Registers a {@link WMGenericDaoImpl}, or a {@link WMGenericTemporalDaoImpl} for a temporal entity, for every DAO
 * interface of a data service. A DAO interface is a plain interface extending {@link WMGenericDao} or
 * {@link WMGenericTemporalDao} in the dao package of the service. The bean is named
 * {@code <serviceId>.<DaoInterfaceSimpleName>} and gets its entity class from the generic type of the interface.
 */
public abstract class DaoBeanDefinitions {

    private DaoBeanDefinitions() {
    }

    public static void register(BeanDefinitionRegistry registry, String daoPackage, String serviceId) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isIndependent() && beanDefinition.getMetadata().isInterface();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(WMGenericDao.class));

        for (BeanDefinition candidate : scanner.findCandidateComponents(daoPackage)) {
            registerDao(registry, candidate.getBeanClassName(), serviceId);
        }
    }

    private static void registerDao(BeanDefinitionRegistry registry, String daoInterfaceName, String serviceId) {
        Class<?> daoInterface = ClassUtils.resolveClassName(daoInterfaceName, null);

        Class<?> entityClass = ResolvableType.forClass(WMGenericDao.class, daoInterface).getGeneric(0).resolve();
        if (entityClass == null) {
            throw new WMRuntimeException("Could not resolve entity type for DAO interface " + daoInterfaceName);
        }

        boolean temporal = WMGenericTemporalDao.class.isAssignableFrom(daoInterface);
        BeanDefinitionBuilder builder = BeanDefinitionBuilder
            .genericBeanDefinition(temporal ? WMGenericTemporalDaoImpl.class : WMGenericDaoImpl.class)
            .addConstructorArgValue(entityClass)
            .addConstructorArgReference(serviceId + "Template")
            .addConstructorArgReference(serviceId + "WMQLTypeHelper");
        if (temporal) {
            builder.addConstructorArgReference(serviceId + "HistoryTemplate");
        }

        // A bean the project already defines under this name is the user's override, so it is kept.
        String beanName = serviceId + "." + daoInterface.getSimpleName();
        if (!registry.containsBeanDefinition(beanName)) {
            registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
        }
    }
}
