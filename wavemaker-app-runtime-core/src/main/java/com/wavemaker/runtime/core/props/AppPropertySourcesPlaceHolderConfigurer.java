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

package com.wavemaker.runtime.core.props;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.StringValueResolver;

import com.wavemaker.commons.properties.EnvironmentRegisteringPropertySourcesPlaceHolderConfigurer;
import com.wavemaker.commons.util.SystemUtils;
import com.wavemaker.runtime.commons.props.PropertyTokenResolver;

public class AppPropertySourcesPlaceHolderConfigurer extends EnvironmentRegisteringPropertySourcesPlaceHolderConfigurer {

    private final List<PropertyTokenResolver> tokenResolvers = new ArrayList<>();

    @Override
    protected void doProcessProperties(ConfigurableListableBeanFactory beanFactoryToProcess,
                                       final StringValueResolver valueResolver) {

        tokenResolvers.clear();
        tokenResolvers.addAll(beanFactoryToProcess.getBeansOfType(PropertyTokenResolver.class, false, false).values());
        AnnotationAwareOrderComparator.sort(tokenResolvers);
        StringValueResolver updatedValueResolver = strVal -> convertPropertyValue(valueResolver.resolveStringValue(strVal));
        super.doProcessProperties(beanFactoryToProcess, updatedValueResolver);
    }

    @Override
    protected String convertPropertyValue(String value) {
        if (org.apache.commons.lang3.StringUtils.isBlank(value)) {
            return value;
        }
        if (SystemUtils.isEncrypted(value)) {
            return SystemUtils.decrypt(value);
        }

        for (PropertyTokenResolver tokenResolver : tokenResolvers) {
            if (tokenResolver.canResolve(value)) {
                value = tokenResolver.resolve(value);
            }
        }
        return value;
    }
}
