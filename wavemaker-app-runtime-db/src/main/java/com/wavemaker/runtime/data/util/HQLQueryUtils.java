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
package com.wavemaker.runtime.data.util;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.query.Query;
import org.hibernate.query.spi.SqmQuery;
import org.hibernate.query.sqm.tree.domain.SqmPluralValuedSimplePath;
import org.hibernate.query.sqm.tree.domain.SqmPath;
import org.hibernate.query.sqm.tree.select.SqmSelectStatement;
import org.hibernate.query.sqm.tree.select.SqmSelection;

import com.wavemaker.runtime.data.model.ReferenceType;
import com.wavemaker.runtime.data.model.returns.FieldType;
import com.wavemaker.runtime.data.model.returns.ReturnProperty;
import com.wavemaker.runtime.data.transform.WMResultTransformer;

public class HQLQueryUtils {

    public static List<ReturnProperty> extractMetaForHql(final Query<?> query) {
        if (!(query instanceof SqmQuery<?> sqmQuery) || !(sqmQuery.getSqmStatement() instanceof SqmSelectStatement<?> select)) {
            throw new IllegalArgumentException("The query is not a HQL select query");
        }
        final List<SqmSelection<?>> selections = select.getQuerySpec().getSelectClause().getSelections();
        final String[] returnAliases = selections.stream().map(SqmSelection::getAlias).toArray(String[]::new);
        List<ReturnProperty> properties = new ArrayList<>(selections.size());
        for (int i = 0; i < selections.size(); i++) {
            final SqmSelection<?> selection = selections.get(i);
            ReturnProperty property = new ReturnProperty();
            property.setName(WMResultTransformer.getAlias(returnAliases, i));

            FieldType fieldType = new FieldType();
            if (selection.getSelectableNode() instanceof SqmPluralValuedSimplePath<?>) {
                fieldType.setList(true);
            }
            final boolean entity = selection.getSelectableNode() instanceof SqmPath<?> path
                && path.getReferencedPathSource().getPathType() instanceof jakarta.persistence.metamodel.EntityType<?>;
            fieldType.setType(entity ? ReferenceType.ENTITY : ReferenceType.PRIMITIVE);

            final Class<?> javaType = selection.getNodeJavaType() == null ? null : selection.getNodeJavaType().getJavaTypeClass();
            fieldType.setTypeRef(javaType == null ? Object.class.getCanonicalName() : javaType.getCanonicalName());
            property.setFieldType(fieldType);

            if (i == 0 && selections.size() == 1 && fieldType.getType() == ReferenceType.ENTITY) {
                // setting property name to for avoiding creating new model class
                // in case of query returning only entity.
                property.setName(null);
            }
            properties.add(property);
        }
        return properties;
    }
}
