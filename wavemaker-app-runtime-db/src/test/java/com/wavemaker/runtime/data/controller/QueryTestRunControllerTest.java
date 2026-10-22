/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.controller;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.wavemaker.runtime.data.model.DesignServiceResponse;
import com.wavemaker.runtime.data.model.queries.RuntimeQuery;
import com.wavemaker.runtime.data.service.QueryDesignService;
import com.wavemaker.runtime.data.support.TestReflectionUtils;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Covers "design-query-http-endpoint" from the runtime-db use-case spec. This module has no Spring MVC test
 * infrastructure (no MockMvc, no spring-test) and this codebase never uses Mockito, so rather than a real HTTP
 * round trip, this calls each controller method directly with a hand-written {@link QueryDesignService} stub
 * (recording what it was called with, returning a canned response) wired in via
 * {@link TestReflectionUtils#setField} in place of the {@code @Autowired} field - verifying the controller
 * really does delegate, with the arguments it was given, and returns exactly what the service returned.
 *
 * <p>{@code MultipartHttpServletRequest} (needed to implement {@link QueryDesignService}'s multipart-based
 * overload) transitively needs {@code jakarta.servlet.http.HttpServletRequest}, which is only a
 * {@code compileOnly} dependency of this module's {@code main} source set - it was missing from the {@code
 * test} classpath until {@code testImplementation(appDependenciesLibs.jakarta.servlet.api)} was added to
 * {@code wavemaker-app-runtime-db.gradle.kts}, matching the existing {@code compileOnly} alias in main.
 */
class QueryTestRunControllerTest {

    private static final class RecordingQueryDesignService implements QueryDesignService {

        String capturedServiceId;
        MultipartHttpServletRequest capturedRequest;
        RuntimeQuery capturedQuery;
        Pageable capturedPageable;
        final DesignServiceResponse responseToReturn = new DesignServiceResponse();

        @Override
        public DesignServiceResponse testRunQuery(String serviceId, MultipartHttpServletRequest request, Pageable pageable) {
            this.capturedServiceId = serviceId;
            this.capturedRequest = request;
            this.capturedPageable = pageable;
            return responseToReturn;
        }

        @Override
        public DesignServiceResponse testRunQuery(String serviceId, RuntimeQuery query, Pageable pageable) {
            throw new UnsupportedOperationException("not exercised by this test");
        }

        @Override
        public Object executeQuery(String serviceId, RuntimeQuery query, Pageable pageable) {
            this.capturedServiceId = serviceId;
            this.capturedQuery = query;
            this.capturedPageable = pageable;
            return responseToReturn;
        }
    }

    @Test
    void testRunQueryDelegatesToQueryDesignServiceWithTheSameServiceIdRequestAndPageableAndReturnsItsResponse() {
        RecordingQueryDesignService service = new RecordingQueryDesignService();
        QueryTestRunController controller = new QueryTestRunController();
        TestReflectionUtils.setField(controller, "queryDesignService", service);
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10, Sort.by("name"));
        MultipartHttpServletRequest request = null;

        DesignServiceResponse response = controller.testRunQuery("myService", request, pageable);

        assertSame(service.responseToReturn, response);
        assertSame("myService", service.capturedServiceId);
        assertSame(request, service.capturedRequest);
        assertSame(pageable, service.capturedPageable);
    }

    @Test
    void executeQueryDelegatesToQueryDesignServiceWithTheSameServiceIdQueryAndPageableAndReturnsItsResponse() {
        RecordingQueryDesignService service = new RecordingQueryDesignService();
        QueryTestRunController controller = new QueryTestRunController();
        TestReflectionUtils.setField(controller, "queryDesignService", service);
        RuntimeQuery query = new RuntimeQuery();
        query.setQueryString("from Employee");
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 5);

        Object response = controller.executeQuery("myService", query, pageable);

        assertSame(service.responseToReturn, response);
        assertSame("myService", service.capturedServiceId);
        assertSame(query, service.capturedQuery);
        assertSame(pageable, service.capturedPageable);
    }
}
