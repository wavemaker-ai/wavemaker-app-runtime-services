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

import com.wavemaker.commons.MessageResource;

/**
 * The decision an {@link ErrorResponseResolver} makes about an exception - the HTTP status and message to use -
 * without building the actual (XSS-sanitized) response, since that requires runtime-core's
 * {@code XSSSecurityHandler}, which a resolver contributed by another module (e.g. runtime-db) must not depend
 * on directly. {@code ApplicationRestServiceExceptionResolver} turns this plain data into the real response.
 */
public final class ErrorResolution {

    private final int status;
    private final MessageResource messageResource;
    private final Object[] args;

    public ErrorResolution(final int status, final MessageResource messageResource, final Object... args) {
        this.status = status;
        this.messageResource = messageResource;
        this.args = args;
    }

    public int getStatus() {
        return status;
    }

    public MessageResource getMessageResource() {
        return messageResource;
    }

    public Object[] getArgs() {
        return args;
    }
}
