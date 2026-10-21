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
package com.wavemaker.runtime.data.transform;

import java.util.LinkedHashMap;
import java.util.Map;


/**
 * @author <a href="mailto:anusha.dharmasagar@wavemaker.com">Anusha Dharmasagar</a>
 * @since 27/6/16
 */
public class AliasToEntityLinkedHashMapTransformer implements WMResultTransformer { // hibernate7: AliasedTupleSubsetResultTransformer removed in 6.0

    public static final AliasToEntityLinkedHashMapTransformer INSTANCE = new AliasToEntityLinkedHashMapTransformer();

    AliasToEntityLinkedHashMapTransformer() {
    }

    @Override
    public Object transformTuple(Object[] tuple, String[] aliases) {
        Map<String, Object> result = new LinkedHashMap<>(tuple.length);
        for (int i = 0; i < tuple.length; i++) {
            result.put(WMResultTransformer.getAlias(aliases, i), tuple[i]);
        }
        return result;
    }

    @Override
    public Object transformFromMap(final Map<String, Object> resultMap) {
        Map<String, Object> result = resultMap;
        if (!(resultMap instanceof LinkedHashMap)) {
            result = new LinkedHashMap<>(resultMap);
        }
        return result;
    }

    @Override
    public String aliasToFieldName(final String columnName) {
        return columnName;
    }

    @Override
    public String aliasFromFieldName(final String fieldName) {
        return fieldName;
    }

    @Override
    public boolean containsField(final String fieldName) {
        return true;
    }

    // hibernate7: no longer part of the transformer contract in 6/7, so never called. 5.6 only asked it when caching query
    // results, which Studio does not do. Kept for the move back to the app side.
    public boolean isTransformedValueATupleElement(String[] aliases, int tupleLength) {
        return false;
    }

    /**
     * Serialization hook for ensuring singleton uniqueing.
     *
     * @return The singleton instance : {@link #INSTANCE}
     */
    private Object readResolve() {
        return INSTANCE;
    }
}