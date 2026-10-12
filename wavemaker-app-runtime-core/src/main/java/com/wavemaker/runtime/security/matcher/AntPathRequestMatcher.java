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
package com.wavemaker.runtime.security.matcher;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public final class AntPathRequestMatcher implements RequestMatcher {

    private static final String MATCH_ALL = "/**";

    private final Matcher matcher;

    private final String pattern;

    private final HttpMethod httpMethod;

    private final boolean caseSensitive;

    public AntPathRequestMatcher(String pattern) {
        this(pattern, null);
    }

    public AntPathRequestMatcher(String pattern, String httpMethod) {
        this(pattern, httpMethod, true);
    }

    public AntPathRequestMatcher(String pattern, String httpMethod, boolean caseSensitive) {
        Assert.hasText(pattern, "Pattern cannot be null or empty");
        this.caseSensitive = caseSensitive;
        if (pattern.equals(MATCH_ALL) || pattern.equals("**")) {
            pattern = MATCH_ALL;
            this.matcher = null;
        } else if (pattern.endsWith(MATCH_ALL) && pattern.indexOf('?') == -1 && pattern.indexOf('{') == -1
            && pattern.indexOf('}') == -1 && pattern.indexOf('*') == pattern.length() - 2) {
            this.matcher = new SubpathMatcher(pattern.substring(0, pattern.length() - 3), caseSensitive);
        } else {
            this.matcher = new SpringAntMatcher(pattern, caseSensitive);
        }
        this.pattern = pattern;
        this.httpMethod = StringUtils.hasText(httpMethod) ? HttpMethod.valueOf(httpMethod) : null;
    }

    public static AntPathRequestMatcher antMatcher(HttpMethod method, String pattern) {
        Assert.notNull(method, "method cannot be null");
        Assert.hasText(pattern, "pattern cannot be empty");
        return new AntPathRequestMatcher(pattern, method.name());
    }

    public static AntPathRequestMatcher antMatcher(HttpMethod method) {
        Assert.notNull(method, "method cannot be null");
        return new AntPathRequestMatcher(MATCH_ALL, method.name());
    }

    public static AntPathRequestMatcher antMatcher(String pattern) {
        Assert.hasText(pattern, "pattern cannot be empty");
        return new AntPathRequestMatcher(pattern);
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        if (this.httpMethod != null && StringUtils.hasText(request.getMethod())
            && this.httpMethod != HttpMethod.valueOf(request.getMethod())) {
            return false;
        }
        if (this.pattern.equals(MATCH_ALL)) {
            return true;
        }
        return this.matcher.matches(getRequestPath(request));
    }

    @Override
    public MatchResult matcher(HttpServletRequest request) {
        if (!matches(request)) {
            return MatchResult.notMatch();
        }
        if (this.matcher == null) {
            return MatchResult.match();
        }
        return MatchResult.match(this.matcher.extractUriTemplateVariables(getRequestPath(request)));
    }

    public Map<String, String> extractUriTemplateVariables(HttpServletRequest request) {
        return matcher(request).getVariables();
    }

    public String getPattern() {
        return this.pattern;
    }

    private String getRequestPath(HttpServletRequest request) {
        String url = request.getServletPath();
        String pathInfo = request.getPathInfo();
        if (pathInfo != null) {
            url = StringUtils.hasLength(url) ? url + pathInfo : pathInfo;
        }
        return url;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AntPathRequestMatcher other)) {
            return false;
        }
        return this.pattern.equals(other.pattern) && this.httpMethod == other.httpMethod
            && this.caseSensitive == other.caseSensitive;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.pattern, this.httpMethod, this.caseSensitive);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Ant [pattern='").append(this.pattern).append("'");
        if (this.httpMethod != null) {
            sb.append(", ").append(this.httpMethod);
        }
        sb.append("]");
        return sb.toString();
    }

    private interface Matcher {

        boolean matches(String path);

        Map<String, String> extractUriTemplateVariables(String path);
    }

    private static final class SpringAntMatcher implements Matcher {

        private final AntPathMatcher antMatcher;

        private final String pattern;

        private SpringAntMatcher(String pattern, boolean caseSensitive) {
            this.pattern = pattern;
            this.antMatcher = new AntPathMatcher();
            this.antMatcher.setTrimTokens(false);
            this.antMatcher.setCaseSensitive(caseSensitive);
        }

        @Override
        public boolean matches(String path) {
            return this.antMatcher.match(this.pattern, path);
        }

        @Override
        public Map<String, String> extractUriTemplateVariables(String path) {
            return this.antMatcher.extractUriTemplateVariables(this.pattern, path);
        }
    }

    private static final class SubpathMatcher implements Matcher {

        private final String subpath;

        private final int length;

        private final boolean caseSensitive;

        private SubpathMatcher(String subpath, boolean caseSensitive) {
            Assert.isTrue(!subpath.contains("*"), "subpath cannot contain \"*\"");
            this.subpath = caseSensitive ? subpath : subpath.toLowerCase(Locale.ROOT);
            this.length = subpath.length();
            this.caseSensitive = caseSensitive;
        }

        @Override
        public boolean matches(String path) {
            if (!this.caseSensitive) {
                path = path.toLowerCase(Locale.ROOT);
            }
            return path.startsWith(this.subpath) && (path.length() == this.length || path.charAt(this.length) == '/');
        }

        @Override
        public Map<String, String> extractUriTemplateVariables(String path) {
            return Collections.emptyMap();
        }
    }
}
