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

package com.wavemaker.runtime.web.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.util.UrlPathHelper;

public class ReactRoutingFilter extends GenericFilterBean {

    private static final Logger reactRoutelogger = LoggerFactory.getLogger(ReactRoutingFilter.class);
    private static final String INDEX_HTML_PATH = "/index.html";
    private final AntPathRequestMatcher pagePathMatcher = new AntPathRequestMatcher("/react-pages/*");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (requestMatches(httpRequest)) {
            reactRoutelogger.info("ReactRoutingFilter matched request {}", httpRequest.getRequestURI());
            reactRoutelogger.debug("Forwarding request {} to {}", new UrlPathHelper().getPathWithinApplication(httpRequest), INDEX_HTML_PATH);
            request.getRequestDispatcher(INDEX_HTML_PATH).forward(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean requestMatches(HttpServletRequest httpRequest) {
        return this.pagePathMatcher.matches(httpRequest);
    }
}