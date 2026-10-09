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
package com.wavemaker.runtime.commons.props;

/**
 * Replaces custom placeholder tokens (for example <code>{WebAppRoot}</code>) in property values.
 * Implementations are discovered through {@link java.util.ServiceLoader}, so a module that owns a token
 * registers its resolver in <code>META-INF/services</code> without the property configurer knowing about it.
 */
public interface PropertyTokenResolver {

    /**
     * @return true if the value contains a token that this resolver replaces
     */
    boolean canResolve(String value);

    /**
     * @return the value with this resolver's tokens replaced; only called when {@link #canResolve} is true
     */
    String resolve(String value);
}
