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
package com.wavemaker.runtime.data.exception.resolver;

import java.util.Optional;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.GenericJDBCException;
import org.hibernate.exception.SQLGrammarException;
import org.hibernate.QueryException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

import com.wavemaker.commons.MessageResource;
import com.wavemaker.commons.MessageResourceHolder;
import com.wavemaker.commons.WMRuntimeException;
import com.wavemaker.runtime.data.exception.BlobContentNotFoundException;
import com.wavemaker.runtime.data.exception.EntityNotFoundException;
import com.wavemaker.runtime.data.exception.QueryParameterMismatchException;
import com.wavemaker.runtime.exception.resolver.ErrorResolution;
import com.wavemaker.runtime.exception.resolver.ErrorResponseResolver;

/**
 * Handles the Hibernate and data service exceptions, so that runtime-core has no dependency on them. It is
 * registered as an {@link ErrorResponseResolver} only when runtime-db is on the classpath. Spring's generic
 * {@code DataIntegrityViolationException} stays in runtime-core, as other stores such as Mongo throw it too.
 */
public class DbExceptionResolver implements ErrorResponseResolver {

    private static final String INPUT_INVALID_MESSAGE = "The input parameters are not valid.";

    @Override
    public Optional<ErrorResolution> tryResolve(final Exception ex) {
        if (ex instanceof ConstraintViolationException) { //Hibernate jdbc exceptions
            return Optional.of(handleRuntimeException((RuntimeException) ex, HttpServletResponse.SC_BAD_REQUEST));
        } else if (ex instanceof GenericJDBCException || ex instanceof SQLGrammarException) {
            return Optional.of(handleRuntimeException((RuntimeException) ex, HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
        } else if (ex instanceof QueryParameterMismatchException) {
            return Optional.of(handleRuntimeException((RuntimeException) ex, HttpServletResponse.SC_BAD_REQUEST));
        } else if (ex instanceof InvalidDataAccessResourceUsageException && ex.getCause() instanceof QueryException) {
            // Not using the root cause for now.
            return Optional.of(new ErrorResolution(HttpServletResponse.SC_BAD_REQUEST, MessageResource.INVALID_INPUT, INPUT_INVALID_MESSAGE));
        } else if (ex instanceof EntityNotFoundException || ex instanceof BlobContentNotFoundException) {
            return Optional.of(resolveWMException((WMRuntimeException) ex, MessageResource.ENTITY_NOT_FOUND));
        }
        return Optional.empty();
    }

    private ErrorResolution handleRuntimeException(RuntimeException ex, int status) {
        String msg = ExceptionUtils.getRootCauseMessage(ex);
        return new ErrorResolution(status, MessageResource.UNEXPECTED_ERROR, getDefaultInternalServerErrorMsg(msg));
    }

    /**
     * Uses the exception's own MessageResource and args if it carries one, otherwise the given default.
     */
    private ErrorResolution resolveWMException(WMRuntimeException ex, MessageResource defaultMessageResource) {
        MessageResourceHolder messageResourceHolder = ex.getMessageResourceHolder();
        MessageResource messageResource = messageResourceHolder.getMessageResource();
        if (messageResource != null) {
            return new ErrorResolution(HttpServletResponse.SC_NOT_FOUND, messageResource, messageResourceHolder.getArgs());
        }
        return new ErrorResolution(HttpServletResponse.SC_NOT_FOUND, defaultMessageResource);
    }

    private String getDefaultInternalServerErrorMsg(String message) {
        return StringUtils.isNotBlank(message) ? message : "Internal Server Error";
    }
}
