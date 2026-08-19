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
package com.wavemaker.runtime.web.filter;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.MDC;

/**
 * Registered as the outermost filter (ahead of throwableTranslationFilter/firewallFilter) so that
 * wm.app.name is set for the whole filter chain, including requests rejected or erroring out in a
 * filter that runs before WMRequestFilter's own position in the chain.
 */
public class WMAppNameMdcFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        MDC.put(WMRequestFilter.APP_NAME_KEY, ((HttpServletRequest) request).getServletContext().getContextPath());
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(WMRequestFilter.APP_NAME_KEY);
        }
    }
}
