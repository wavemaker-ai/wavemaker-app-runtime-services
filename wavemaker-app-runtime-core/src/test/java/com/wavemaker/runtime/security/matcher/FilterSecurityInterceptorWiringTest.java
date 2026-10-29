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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.SecurityConfig;
import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.access.expression.DefaultWebSecurityExpressionHandler;
import org.springframework.security.web.access.expression.ExpressionBasedFilterInvocationSecurityMetadataSource;
import org.springframework.security.web.access.expression.WebExpressionVoter;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;
import org.springframework.security.web.util.matcher.RequestMatcher;

public class FilterSecurityInterceptorWiringTest {

    @Test
    public void interceptorWiringPassesStartupValidation() {
        assertDoesNotThrow(() -> interceptor().afterPropertiesSet());
    }

    @Test
    public void firstMatchingRuleWins() {
        FilterSecurityInterceptor interceptor = interceptor();

        assertEquals("permitAll", expression(interceptor, "/services/security/info"));
        assertEquals("hasRole('ROLE_ADMIN')", expression(interceptor, "/services/admin/users"));
        assertEquals("authenticated", expression(interceptor, "/services/other"));
    }

    @Test
    public void requestWithNoMatchingRuleHasNoAttributes() {
        LinkedHashMap<RequestMatcher, Collection<ConfigAttribute>> requestMap = new LinkedHashMap<>();
        requestMap.put(AntPathRequestMatcher.antMatcher("/services/**"), SecurityConfig.createList("authenticated"));
        FilterSecurityInterceptor interceptor = interceptor(requestMap);

        assertTrue(interceptor.getSecurityMetadataSource().getAttributes(filterInvocation("/metrics")).isEmpty());
    }

    private String expression(FilterSecurityInterceptor interceptor, String path) {
        Collection<ConfigAttribute> attributes = interceptor.getSecurityMetadataSource().getAttributes(filterInvocation(path));
        return attributes.iterator().next().toString();
    }

    private FilterSecurityInterceptor interceptor() {
        LinkedHashMap<RequestMatcher, Collection<ConfigAttribute>> requestMap = new LinkedHashMap<>();
        requestMap.put(AntPathRequestMatcher.antMatcher("/services/security/**"), SecurityConfig.createList("permitAll"));
        requestMap.put(AntPathRequestMatcher.antMatcher("/services/admin/**"), SecurityConfig.createList("hasRole('ROLE_ADMIN')"));
        requestMap.put(AntPathRequestMatcher.antMatcher("/**"), SecurityConfig.createList("authenticated"));
        return interceptor(requestMap);
    }

    private FilterSecurityInterceptor interceptor(LinkedHashMap<RequestMatcher, Collection<ConfigAttribute>> requestMap) {
        SecurityExpressionHandler<FilterInvocation> expressionHandler = new DefaultWebSecurityExpressionHandler();
        WebExpressionVoter voter = new WebExpressionVoter();
        voter.setExpressionHandler(expressionHandler);

        FilterSecurityInterceptor interceptor = new FilterSecurityInterceptor();
        interceptor.setSecurityMetadataSource(new ExpressionBasedFilterInvocationSecurityMetadataSource(requestMap, expressionHandler));
        interceptor.setAccessDecisionManager(new AffirmativeBased(List.of(voter)));
        interceptor.setAuthenticationManager(mock(AuthenticationManager.class));
        return interceptor;
    }

    private FilterInvocation filterInvocation(String path) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getServletPath()).thenReturn(path);
        return new FilterInvocation(request, mock(HttpServletResponse.class), mock(FilterChain.class));
    }
}
