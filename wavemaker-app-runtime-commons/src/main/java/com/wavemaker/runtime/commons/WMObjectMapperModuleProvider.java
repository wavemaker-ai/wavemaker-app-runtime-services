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
package com.wavemaker.runtime.commons;

import tools.jackson.databind.JacksonModule;

/**
 * Contributes a Jackson {@link JacksonModule} to the serializer of {@link WMObjectMapper}. Implementations are discovered
 * through {@link java.util.ServiceLoader}, so modules that need optional libraries (like Hibernate) can live in the
 * jar that owns the library and are skipped when that jar is not present.
 */
public interface WMObjectMapperModuleProvider {

    JacksonModule getModule();
}
