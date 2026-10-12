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
package com.wavemaker.runtime.exception.resolver;

import java.util.Optional;

/**
 * Lets a module contribute exception handling to the application's REST exception resolver without runtime-core
 * depending on that module. A resolver only decides the {@link ErrorResolution}; the core resolver builds the
 * response. Every {@code ErrorResponseResolver} bean in the application context is tried first, and the core
 * resolver falls back to its own handling when none of them recognises the exception.
 */
public interface ErrorResponseResolver {

    /**
     * @return the resolution for {@code ex}, or {@link Optional#empty()} if this resolver does not recognise it
     */
    Optional<ErrorResolution> tryResolve(Exception ex);
}
