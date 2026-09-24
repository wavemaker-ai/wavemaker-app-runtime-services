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
package com.wavemaker.runtime.data.exception.resolver;

import java.util.Optional;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.GenericJDBCException;
import org.hibernate.exception.SQLGrammarException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.hibernate5.HibernateQueryException;

import com.wavemaker.commons.MessageResource;
import com.wavemaker.commons.MessageResourceHolder;
import com.wavemaker.commons.WMRuntimeException;
import com.wavemaker.runtime.data.exception.BlobContentNotFoundException;
import com.wavemaker.runtime.data.exception.EntityNotFoundException;
import com.wavemaker.runtime.data.exception.QueryParameterMismatchException;
import com.wavemaker.runtime.exception.resolver.ErrorResolution;
import com.wavemaker.runtime.exception.resolver.ErrorResponseResolver;

/**
 * Handles Hibernate/JDBC-backed data-service exceptions, split out of {@code ApplicationRestServiceExceptionResolver}
 * so that runtime-core has no compile-time dependency on Hibernate/runtime-db exception types. Registered as a
 * plain {@code ErrorResponseResolver} bean (not a {@code HandlerExceptionResolver}) - {@code ApplicationRestServiceExceptionResolver}
 * auto-collects every {@code ErrorResponseResolver} bean and tries each one's {@link #tryResolve} in turn, so no
 * Spring MVC resolver-ordering coordination is needed between contributors; it's the only implementation of
 * {@code ErrorResponseResolver}. Only decides the status/message/args ({@link ErrorResolution}) - it never
 * builds the actual (XSS-sanitized) response itself, since that would require a compile-time dependency on
 * runtime-core's {@code XSSSecurityHandler} (which would be circular, since runtime-core already depends on
 * runtime-db). {@code ApplicationRestServiceExceptionResolver} does the actual building.
 */
public class DbExceptionResolver implements ErrorResponseResolver {

    private static final String INPUT_INVALID_MESSAGE = "The input parameters are not valid.";

    @Override
    public Optional<ErrorResolution> tryResolve(final Exception ex) {

        if (!isDbException(ex)) {
            return Optional.empty();
        }

        if (ex instanceof ConstraintViolationException) { //Hibernate jdbc exceptions
            return Optional.of(handleRuntimeException((RuntimeException) ex, HttpServletResponse.SC_BAD_REQUEST));
        } else if (ex instanceof DataIntegrityViolationException) {
            return Optional.of(handleDataIntegrityViolationException((DataIntegrityViolationException) ex));
        } else if (ex instanceof GenericJDBCException) {
            return Optional.of(handleRuntimeException((GenericJDBCException) ex, HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
        } else if (ex instanceof SQLGrammarException) {
            return Optional.of(handleRuntimeException((SQLGrammarException) ex, HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
        } else if (ex instanceof QueryParameterMismatchException) {
            return Optional.of(handleRuntimeException((QueryParameterMismatchException) ex, HttpServletResponse.SC_BAD_REQUEST));
        } else if (ex instanceof HibernateQueryException) {
            // Not using the root cause for now.
            return Optional.of(new ErrorResolution(HttpServletResponse.SC_BAD_REQUEST, MessageResource.INVALID_INPUT, INPUT_INVALID_MESSAGE));
        } else { // EntityNotFoundException || BlobContentNotFoundException (WM Runtime Exceptions)
            return Optional.of(resolveWMException((WMRuntimeException) ex, MessageResource.ENTITY_NOT_FOUND));
        }
    }

    private boolean isDbException(Exception ex) {
        return ex instanceof ConstraintViolationException
            || ex instanceof DataIntegrityViolationException
            || ex instanceof GenericJDBCException
            || ex instanceof SQLGrammarException
            || ex instanceof QueryParameterMismatchException
            || ex instanceof HibernateQueryException
            || ex instanceof EntityNotFoundException
            || ex instanceof BlobContentNotFoundException;
    }

    private ErrorResolution handleRuntimeException(RuntimeException ex, int status) {
        String msg = ExceptionUtils.getRootCauseMessage(ex);
        return new ErrorResolution(status, MessageResource.UNEXPECTED_ERROR, getDefaultInternalServerErrorMsg(msg));
    }

    private ErrorResolution handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        return new ErrorResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, MessageResource.DATA_INTEGRITY_VIOALATION,
            ex.getMostSpecificCause().getMessage());
    }

    /**
     * Mirrors ApplicationRestServiceExceptionResolver's own WM-exception handling for the one case this class
     * needs: use the exception's own MessageResource/args if it carries one, otherwise the given default.
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
