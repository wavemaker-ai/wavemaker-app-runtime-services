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
package com.wavemaker.runtime.data.dao.query.types;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hibernate.Session;
import org.hibernate.type.BasicType;
import org.hibernate.type.spi.TypeConfiguration;

import com.wavemaker.runtime.data.dao.query.types.wmql.WMQLTypeHelper;
import com.wavemaker.runtime.data.filter.WMQueryParamInfo;
import com.wavemaker.runtime.data.model.JavaType;
import com.wavemaker.runtime.data.model.queries.QueryParameter;
import com.wavemaker.runtime.data.model.queries.RuntimeQuery;
import com.wavemaker.runtime.data.util.HibernateUtils;

/**
 * @author <a href="mailto:dilip.gundu@wavemaker.com">Dilip Kumar</a>
 * @since 21/7/17
 */
public class RuntimeParameterTypeResolver implements ParameterTypeResolver {

    private final Map<String, BasicType<?>> typesMap;

    public RuntimeParameterTypeResolver(List<QueryParameter> parameters, TypeConfiguration typeConfiguration) {
        typesMap = new HashMap<>();
        for (QueryParameter queryParameter : parameters) {
            HibernateUtils.findType(typeConfiguration, queryParameter.getType().getClassName())
                .ifPresent(type -> typesMap.put(queryParameter.getName(), type));
        }
    }

    public RuntimeParameterTypeResolver(Map<String, WMQueryParamInfo> parameters, TypeConfiguration typeConfiguration, WMQLTypeHelper wmqlTypeHelper) {
        typesMap = new HashMap<>();
        for (Map.Entry<String, WMQueryParamInfo> queryParamInfoEntry : parameters.entrySet()) {
            String key = queryParamInfoEntry.getKey();
            JavaType javaType = queryParamInfoEntry.getValue().getJavaType();

            if (javaType != null) {
                if (wmqlTypeHelper != null) {
                    javaType = wmqlTypeHelper.aliasFor(javaType);
                }
                HibernateUtils.findType(typeConfiguration, javaType.getClassName()).ifPresent(type -> typesMap.put(key, type));
            }

        }
    }

    public static RuntimeParameterTypeResolver from(Session session, RuntimeQuery query) {
        return new RuntimeParameterTypeResolver(query.getParameters(), HibernateUtils.getTypeConfiguration(session));
    }

    @Override
    public Optional<BasicType<?>> resolveType(final String name) {
        return Optional.ofNullable(typesMap.get(name));
    }
}
