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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Spring-managed, singleton-cached resolver for injecting React (Next.js static export) page-specific inline
 * script SHA-256 hashes (from {@code csp-hashes.json}, at the webapp root) into a {@code Content-Security-Policy}
 * value. The hash file is parsed once at startup ({@link #init()}) into a page-key -> hash-token-list cache;
 * per-request work ({@link #resolveEffectivePolicy(HttpServletRequest, String)}) is resolving which page a
 * request maps to, then injecting that page's cached hashes into whichever directive of the given policy
 * actually governs script elements.
 *
 * <p>Every route in this app is served as its own static HTML document (e.g. {@code /} -> {@code index.html},
 * {@code /react-pages/Main} -> {@code react-pages/Main.html}) with its own distinct inline scripts (Next.js's
 * per-page RSC/hydration payload) -- confirmed by diffing actual served responses, not just the exported files
 * on disk. Only the document actually being returned needs its own hashes; in-app client-side/AJAX page
 * transitions never inject or execute another page's inline scripts into the current document.</p>
 */
public class ReactCspScriptHashResolver {

    private static final Logger logger = LoggerFactory.getLogger(ReactCspScriptHashResolver.class);

    private static final String CONTENT_HASHES_RESOURCE_PATH = "/csp-hashes.json";
    private static final String NEXT_RESOURCE_PATH = "/_next";
    private static final String INDEX_PAGE_KEY = "index.html";
    private static final String REACT_PAGE_NAME_VARIABLE = "pageName";

    private static final String[] SCRIPT_DIRECTIVE_NAMES = new String[]{"script-src-elem", "script-src", "default-src"};
    private static final Pattern SCRIPT_SRC_ELEM_PATTERN = Pattern.compile("(?i)\\bscript-src-elem\\b([^;]*)");
    private static final Pattern SCRIPT_SRC_PATTERN = Pattern.compile("(?i)\\bscript-src(?!-elem)\\b([^;]*)");
    private static final Pattern DEFAULT_SRC_PATTERN = Pattern.compile("(?i)\\bdefault-src\\b([^;]*)");
    private static final List<Pattern> SCRIPT_DIRECTIVE_PRECEDENCE = List.of(SCRIPT_SRC_ELEM_PATTERN, SCRIPT_SRC_PATTERN, DEFAULT_SRC_PATTERN);

    @Autowired
    private ServletContext servletContext;

    @Value("${security.general.csp.enabled}")
    private boolean cspEnabled;

    // cache: page key (as it appears in csp-hashes.json's "pages") -> that page's own hash tokens + shared
    private final Map<String, List<String>> hashTokensByPage = new HashMap<>();

    private final RequestMatcher rootPathMatcher = PathPatternRequestMatcher.withDefaults().matcher("/");
    private final RequestMatcher indexPathMatcher = PathPatternRequestMatcher.withDefaults().matcher("/index.html");
    private final RequestMatcher reactPagePathMatcher = PathPatternRequestMatcher.withDefaults().matcher("/react-pages/{" + REACT_PAGE_NAME_VARIABLE + "}");

    @PostConstruct
    public void init() {
        if (!cspEnabled) {
            logger.debug("CSP disabled; skipping React CSP script hash caching");
            return;
        }
        Set<String> nextResourcePaths = servletContext.getResourcePaths(NEXT_RESOURCE_PATH);
        boolean reactBuild = nextResourcePaths != null && !nextResourcePaths.isEmpty();
        if (!reactBuild) {
            logger.debug("{} not found in webapp; not a React build, skipping React CSP script hash caching", NEXT_RESOURCE_PATH);
            return;
        }
        loadAndCacheHashTokens();
    }

    /**
     * Given the CSP policy that's about to be served, injects the requested page's cached inline-script hashes
     * into whichever directive governs script elements, and returns the augmented policy. Returns {@code policy}
     * unchanged if this isn't a React build (cache empty), the request doesn't resolve to a known page, or the
     * policy has none of {@code script-src-elem}/{@code script-src}/{@code default-src} to inject into.
     */
    public String resolveEffectivePolicy(HttpServletRequest request, String policy) {
        if (hashTokensByPage.isEmpty()) {
            return policy;
        }
        String pageKey = resolvePageKey(request);
        List<String> hashTokens = pageKey == null ? null : hashTokensByPage.get(pageKey);
        if (hashTokens == null || hashTokens.isEmpty()) {
            logger.debug("No cached hashes for page key '{}' (request {}); leaving CSP policy unchanged", pageKey, request.getRequestURI());
            return policy;
        }
        String effectivePolicy = injectHashes(policy, pageKey, hashTokens);
        logger.debug("Resolved page key '{}' for request {} -> CSP policy: {}", pageKey, request.getRequestURI(), effectivePolicy);
        return effectivePolicy;
    }

    private void loadAndCacheHashTokens() {
        logger.debug("Loading React CSP script hashes from {}", CONTENT_HASHES_RESOURCE_PATH);
        try (InputStream inputStream = servletContext.getResourceAsStream(CONTENT_HASHES_RESOURCE_PATH)) {
            if (inputStream == null) {
                logger.warn("React CSP script hashes file not found at {}; inline React scripts will not be allowlisted", CONTENT_HASHES_RESOURCE_PATH);
                return;
            }
            JsonNode root = new ObjectMapper().readTree(inputStream);
            List<String> shared = toList(root.get("shared"));
            JsonNode pages = root.get("pages");
            if (pages != null) {
                pages.fields().forEachRemaining(page -> {
                    List<String> hashTokens = new ArrayList<>(shared);
                    hashTokens.addAll(toList(page.getValue()));
                    hashTokensByPage.put(page.getKey(), hashTokens);
                });
            }
            logger.info("Cached React CSP script hashes for {} page(s) from {}: {}", hashTokensByPage.size(), CONTENT_HASHES_RESOURCE_PATH, hashTokensByPage.keySet());
        } catch (IOException e) {
            logger.error("Error while reading React CSP script hashes file at {}", CONTENT_HASHES_RESOURCE_PATH, e);
        }
    }

    private String resolvePageKey(HttpServletRequest request) {
        if (rootPathMatcher.matches(request) || indexPathMatcher.matches(request)) {
            return INDEX_PAGE_KEY;
        }
        RequestMatcher.MatchResult matchResult = reactPagePathMatcher.matcher(request);
        if (matchResult.isMatch()) {
            String pageName = matchResult.getVariables().get(REACT_PAGE_NAME_VARIABLE);
            String fileName = pageName.contains(".") ? pageName : pageName + ".html";
            return "react-pages/" + fileName;
        }
        return null;
    }

    private static List<String> toList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> values.add(item.asText()));
        }
        return values;
    }

    private static String injectHashes(String policy, String pageKey, List<String> hashTokens) {
        String hashSuffix = " " + String.join(" ", hashTokens);
        for (int i = 0; i < SCRIPT_DIRECTIVE_PRECEDENCE.size(); i++) {
            Matcher matcher = SCRIPT_DIRECTIVE_PRECEDENCE.get(i).matcher(policy);
            if (matcher.find()) {
                int insertPosition = matcher.end();
                String updatedPolicy = policy.substring(0, insertPosition) + hashSuffix + policy.substring(insertPosition);
                logger.debug("Injected {} hash(es) for page '{}' into '{}' directive; resulting CSP policy: {}",
                    hashTokens.size(), pageKey, SCRIPT_DIRECTIVE_NAMES[i], updatedPolicy);
                return updatedPolicy;
            }
        }
        logger.warn("CSP policy has no script-src-elem, script-src, or default-src directive; skipping hash injection for page '{}'", pageKey);
        return policy;
    }
}
