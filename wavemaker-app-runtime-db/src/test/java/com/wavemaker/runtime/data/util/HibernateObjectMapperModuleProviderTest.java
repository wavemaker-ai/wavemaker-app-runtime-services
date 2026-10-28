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
package com.wavemaker.runtime.data.util;

import java.util.List;

import org.hibernate.collection.spi.PersistentBag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.commons.WMObjectMapper;

public class HibernateObjectMapperModuleProviderTest {

    @Test
    public void uninitializedLazyCollectionIsWrittenAsNull() throws Exception {
        // an uninitialized collection with no session would fail with LazyInitializationException without the module
        Parent parent = new Parent();
        parent.setChildren(new PersistentBag());
        String json = WMObjectMapper.getInstance().writeValueAsString(parent);
        Assertions.assertEquals("{\"children\":null}", json);
    }

    public static class Parent {

        private List<String> children;

        public List<String> getChildren() {
            return children;
        }

        public void setChildren(List<String> children) {
            this.children = children;
        }
    }
}
