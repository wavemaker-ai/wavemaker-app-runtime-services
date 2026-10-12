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

import tools.jackson.databind.JacksonModule;
import tools.jackson.datatype.hibernate5.jakarta.Hibernate5JakartaModule;
import com.wavemaker.runtime.commons.WMObjectMapperModuleProvider;

public class HibernateObjectMapperModuleProvider implements WMObjectMapperModuleProvider {

    @Override
    public JacksonModule getModule() {
        Hibernate5JakartaModule module = new Hibernate5JakartaModule();
        // uninitialized lazy proxies are written as null instead of hitting the database
        module.disable(Hibernate5JakartaModule.Feature.FORCE_LAZY_LOADING);
        return module;
    }
}
