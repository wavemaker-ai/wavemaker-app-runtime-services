/*******************************************************************************
 * Copyright (C) 2022-2023 WaveMaker, Inc.
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
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.GenericFilterBean;

import com.wavemaker.runtime.web.wrapper.ExpoManifestResponseWrapper;

/**
 * Adds the headers required by Expo Go to the React Native bundle resources.
 * <ul>
 * <li>{@code rn-bundle/manifest-android.json} and {@code rn-bundle/manifest-ios.json} get the Expo protocol headers
 * along with the {@code application/expo+json} content type.</li>
 * <li>Every other resource under {@code rn-bundle/} gets only the Expo protocol headers.</li>
 * </ul>
 */
public class ExpoGoHeadersFilter extends GenericFilterBean {

    private static final Logger logger = LoggerFactory.getLogger(ExpoGoHeadersFilter.class);
    private static final String EXPO_PROTOCOL_VERSION_HEADER = "expo-protocol-version";
    private static final String EXPO_SFV_VERSION_HEADER = "expo-sfv-version";
    private static final String EXPO_PROTOCOL_VERSION = "1";
    private static final String EXPO_SFV_VERSION = "0";

    private final AntPathRequestMatcher androidManifestMatcher = new AntPathRequestMatcher("/rn-bundle/manifest-android.json");
    private final AntPathRequestMatcher iosManifestMatcher = new AntPathRequestMatcher("/rn-bundle/manifest-ios.json");
    private final AntPathRequestMatcher rnBundleMatcher = new AntPathRequestMatcher("/rn-bundle/**");

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        if (requestMatches(httpServletRequest)) {
            logger.debug("Adding Expo headers to request {}", httpServletRequest.getRequestURI());
            httpServletResponse.setHeader(EXPO_PROTOCOL_VERSION_HEADER, EXPO_PROTOCOL_VERSION);
            httpServletResponse.setHeader(EXPO_SFV_VERSION_HEADER, EXPO_SFV_VERSION);
        }
        ServletResponse chainResponse = isManifestRequest(httpServletRequest) ? new ExpoManifestResponseWrapper(httpServletResponse) : servletResponse;
        filterChain.doFilter(servletRequest, chainResponse);
    }

    private boolean requestMatches(HttpServletRequest httpServletRequest) {
        return this.rnBundleMatcher.matches(httpServletRequest);
    }

    private boolean isManifestRequest(HttpServletRequest httpServletRequest) {
        return androidManifestMatcher.matches(httpServletRequest) || iosManifestMatcher.matches(httpServletRequest);
    }
}
