/*******************************************************************************
 * Copyright (C) 2026 WaveMaker, Inc.
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

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.http.HttpMethod;

public class AntPathRequestMatcherTest {

    @Test
    public void exactPatternMatchesOnlyThatPath() {
        AntPathRequestMatcher matcher = AntPathRequestMatcher.antMatcher("/foo");

        assertTrue(matcher.matches(request("GET", "/foo", null)));
        assertFalse(matcher.matches(request("GET", "/foo/bar", null)));
        assertFalse(matcher.matches(request("GET", "/foobar", null)));
    }

    @Test
    public void trailingSlashIsNotEquivalentToPatternWithoutIt() {
        assertFalse(AntPathRequestMatcher.antMatcher("/foo").matches(request("GET", "/foo/", null)));
        assertFalse(AntPathRequestMatcher.antMatcher("/foo/").matches(request("GET", "/foo", null)));
    }

    @Test
    public void trailingDoubleStarMatchesBaseAndDescendantsOnly() {
        AntPathRequestMatcher matcher = AntPathRequestMatcher.antMatcher("/foo/**");

        assertTrue(matcher.matches(request("GET", "/foo", null)));
        assertTrue(matcher.matches(request("GET", "/foo/", null)));
        assertTrue(matcher.matches(request("GET", "/foo/bar/baz", null)));
        assertFalse(matcher.matches(request("GET", "/foobar", null)));
    }

    @Test
    public void matchAllPatternsMatchEverything() {
        assertTrue(AntPathRequestMatcher.antMatcher("/**").matches(request("GET", "/any/path", null)));
        assertTrue(new AntPathRequestMatcher("**").matches(request("GET", "/any/path", null)));
    }

    @Test
    public void midPathDoubleStarMatchesAnyNumberOfSegments() {
        AntPathRequestMatcher matcher = AntPathRequestMatcher.antMatcher("/a/**/b");

        assertTrue(matcher.matches(request("GET", "/a/b", null)));
        assertTrue(matcher.matches(request("GET", "/a/x/y/b", null)));
        assertFalse(matcher.matches(request("GET", "/a/x/y/c", null)));
    }

    @Test
    public void singleStarMatchesOneSegment() {
        AntPathRequestMatcher matcher = AntPathRequestMatcher.antMatcher("/oauth2/*/callback");

        assertTrue(matcher.matches(request("GET", "/oauth2/google/callback", null)));
        assertFalse(matcher.matches(request("GET", "/oauth2/a/b/callback", null)));
    }

    @Test
    public void encodedSlashIsKeptInsideSingleSegment() {
        AntPathRequestMatcher matcher = AntPathRequestMatcher.antMatcher("/files/*");

        assertTrue(matcher.matches(request("GET", "/files/a%2Fb", null)));
    }

    @Test
    public void httpMethodRestrictsTheMatch() {
        AntPathRequestMatcher matcher = AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/foo");

        assertTrue(matcher.matches(request("GET", "/foo", null)));
        assertFalse(matcher.matches(request("POST", "/foo", null)));
    }

    @Test
    public void matchingIsCaseSensitive() {
        assertFalse(AntPathRequestMatcher.antMatcher("/Foo").matches(request("GET", "/foo", null)));
    }

    @Test
    public void servletPathAndPathInfoAreJoined() {
        AntPathRequestMatcher matcher = AntPathRequestMatcher.antMatcher("/services/x");

        assertTrue(matcher.matches(request("GET", "/services", "/x")));
    }

    @Test
    public void extractsUriTemplateVariables() {
        AntPathRequestMatcher matcher = AntPathRequestMatcher.antMatcher("/oauth2/{providerId}/callback");

        assertEquals("google", matcher.extractUriTemplateVariables(request("GET", "/oauth2/google/callback", null)).get("providerId"));
    }

    @Test
    public void registrationIdPatternMatchesLoginPaths() {
        AntPathRequestMatcher matcher = AntPathRequestMatcher.antMatcher("/login/saml2/sso/**");

        assertTrue(matcher.matches(request("POST", "/login/saml2/sso/okta", null)));
        assertTrue(matcher.matches(request("POST", "/login/saml2/sso", null)));
    }

    private HttpServletRequest request(String method, String servletPath, String pathInfo) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getServletPath()).thenReturn(servletPath);
        when(request.getPathInfo()).thenReturn(pathInfo);
        return request;
    }
}
