/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/**
 * {@code HttpServletRequest}/{@code MultipartHttpServletRequest} are large interfaces with no lightweight,
 * dependency-free implementation available here (no spring-test, no Mockito in this module). Since both are
 * pure interfaces (no abstract classes to extend), a JDK dynamic {@link Proxy} gives a real implementation of
 * exactly the handful of methods a given test actually needs, without hand-writing dozens of unused overrides -
 * any method not explicitly stubbed throws {@link UnsupportedOperationException} naming itself, so a test that
 * unexpectedly depends on more of the interface than it stubbed fails loudly and specifically, not silently.
 */
public final class StubServletRequests {

    private StubServletRequests() {
    }

    public static HttpServletRequest httpServletRequest(final Map<String, String> parameters) {
        return proxy(HttpServletRequest.class, (proxy, method, args) -> {
            if ("getParameter".equals(method.getName())) {
                return parameters.get((String) args[0]);
            }
            throw unstubbed(method);
        });
    }

    public static MultipartHttpServletRequest multipartHttpServletRequest(
        final Map<String, MultipartFile> files, final Map<String, String> parameters) {
        return proxy(MultipartHttpServletRequest.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getFile":
                    return files.get((String) args[0]);
                case "getFileMap":
                    return files;
                case "getParameter":
                    return parameters.get((String) args[0]);
                default:
                    throw unstubbed(method);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(StubServletRequests.class.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static UnsupportedOperationException unstubbed(final Method method) {
        return new UnsupportedOperationException(
            "This stub request does not implement " + method.getName() + "() - the test using it doesn't stub "
                + "this method because the code under test wasn't expected to call it. If you're seeing this, "
                + "either the test's stub is incomplete or the production code now calls something new.");
    }
}
