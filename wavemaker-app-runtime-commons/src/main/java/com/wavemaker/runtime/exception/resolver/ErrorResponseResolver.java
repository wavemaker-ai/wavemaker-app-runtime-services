/*******************************************************************************
 * Copyright (C) 2024-2025 WaveMaker, Inc.
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
 * Lets a module contribute its own exception handling to {@code ApplicationRestServiceExceptionResolver}
 * (runtime-core) without being a separately-registered {@code HandlerExceptionResolver} bean coordinated via
 * Spring MVC's resolver ordering, and without needing a callback into runtime-core to build the actual
 * response - a resolver only decides the {@link ErrorResolution} (status/message/args); building the
 * XSS-sanitized {@code ModelAndView} stays entirely in {@code ApplicationRestServiceExceptionResolver}.
 * {@code ApplicationRestServiceExceptionResolver} auto-collects every {@code ErrorResponseResolver} bean
 * ({@code @Autowired List<ErrorResponseResolver>}) and tries each in turn, falling back to its own generic
 * handling if none of them resolve the exception. {@code DbExceptionResolver} (runtime-db) is currently the
 * only implementation, contributed only when runtime-db is present.
 */
public interface ErrorResponseResolver {

    /**
     * Attempt to resolve {@code ex}. Returns {@link Optional#empty()} if this resolver doesn't recognise the
     * exception, so the caller can move on to the next contributor (or its own fallback handling).
     */
    Optional<ErrorResolution> tryResolve(Exception ex);
}
