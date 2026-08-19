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
package com.wavemaker.runtime.web.servlet;

import java.io.IOException;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import org.slf4j.MDC;

import com.wavemaker.runtime.web.filter.WMRequestFilter;

/**
 * A load-on-startup servlet's init() is called by the container after all ServletContextListeners
 * have returned, so it runs outside any listener's or filter's wm.app.name MDC scope and its logs
 * would otherwise go unattributed (missing from the per-app log file). This wrapper sets wm.app.name
 * around delegate.init() without needing to modify the delegate servlet's own source (needed since
 * DispatcherServlet is a third-party class).
 *
 * The same gap exists in reverse on undeploy: the container calls servlet destroy() before running
 * any listener's contextDestroyed(), so destroy() is wrapped the same way, using the context path
 * captured at init() time since destroy() isn't given a ServletConfig.
 */
public class MdcAwareServletWrapper implements Servlet {

    private final Servlet delegate;
    private volatile String contextPath;

    public MdcAwareServletWrapper(Servlet delegate) {
        this.delegate = delegate;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        contextPath = config.getServletContext().getContextPath();
        MDC.put(WMRequestFilter.APP_NAME_KEY, contextPath);
        try {
            delegate.init(config);
        } finally {
            MDC.remove(WMRequestFilter.APP_NAME_KEY);
        }
    }

    @Override
    public ServletConfig getServletConfig() {
        return delegate.getServletConfig();
    }

    @Override
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        delegate.service(req, res);
    }

    @Override
    public String getServletInfo() {
        return delegate.getServletInfo();
    }

    @Override
    public void destroy() {
        MDC.put(WMRequestFilter.APP_NAME_KEY, contextPath);
        try {
            delegate.destroy();
        } finally {
            MDC.remove(WMRequestFilter.APP_NAME_KEY);
        }
    }
}
