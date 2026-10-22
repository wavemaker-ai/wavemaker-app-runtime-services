/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.util;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import com.wavemaker.commons.InvalidInputException;
import com.wavemaker.commons.WMRuntimeException;
import com.wavemaker.runtime.data.model.JavaType;
import com.wavemaker.runtime.data.model.queries.QueryParameter;
import com.wavemaker.runtime.data.support.ByteArrayMultipartFile;
import com.wavemaker.runtime.data.support.StubServletRequests;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MultipartQueryUtilsTest {

    private static QueryParameter parameter(String name) {
        QueryParameter parameter = new QueryParameter();
        parameter.setName(name);
        parameter.setType(JavaType.STRING);
        return parameter;
    }

    @Test
    void readContentParsesTheWmDataJsonFilePartWhenPresent() {
        String json = "{\"name\":\"Alice\"}";
        MultipartFile jsonFile = new ByteArrayMultipartFile(
            "wm_data_json", "wm_data_json", "application/json", json.getBytes(StandardCharsets.UTF_8));
        var request = StubServletRequests.multipartHttpServletRequest(
            Map.of("wm_data_json", jsonFile), Map.of());

        TestParameterHolder result = MultipartQueryUtils.readContent(request, TestParameterHolder.class);

        assertEquals("Alice", result.name);
    }

    @Test
    void readContentFallsBackToTheWmDataJsonRequestParameterWhenNoFilePartIsPresent() {
        var request = StubServletRequests.multipartHttpServletRequest(
            Map.of(), Map.of("wm_data_json", "{\"name\":\"Bob\"}"));

        TestParameterHolder result = MultipartQueryUtils.readContent(request, TestParameterHolder.class);

        assertEquals("Bob", result.name);
    }

    @Test
    void readContentThrowsWhenNeitherTheFilePartNorTheRequestParameterArePresent() {
        var request = StubServletRequests.multipartHttpServletRequest(Map.of(), Map.of());

        assertThrows(InvalidInputException.class, () -> MultipartQueryUtils.readContent(request, TestParameterHolder.class));
    }

    @Test
    void setMultipartsWritesEachNonJsonPartsBytesAsTheMatchingParametersTestValue() {
        QueryParameter photo = parameter("photo");
        QueryParameter resume = parameter("resume");
        List<QueryParameter> parameters = List.of(photo, resume);

        MultiValueMap<String, MultipartFile> parts = new LinkedMultiValueMap<>();
        parts.add("photo", new ByteArrayMultipartFile("photo", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3}));
        parts.add("wm_data_json", new ByteArrayMultipartFile("wm_data_json", "wm_data_json", "application/json", "{}".getBytes(StandardCharsets.UTF_8)));

        MultipartQueryUtils.setMultiparts(parameters, parts);

        assertArrayEquals(new byte[] {1, 2, 3}, (byte[]) photo.getTestValue());
        // wm_data_json is the JSON body part itself, not a parameter - it must never be treated as one.
        assertEquals(null, resume.getTestValue());
    }

    @Test
    void setMultipartsThrowsWhenAPartNameDoesNotMatchAnyDeclaredParameter() {
        MultiValueMap<String, MultipartFile> parts = new LinkedMultiValueMap<>();
        parts.add("unknownField", new ByteArrayMultipartFile("unknownField", "f", "text/plain", new byte[] {9}));

        assertThrows(WMRuntimeException.class, () -> MultipartQueryUtils.setMultiparts(List.of(parameter("photo")), parts));
    }

    public static class TestParameterHolder {
        public String name;

        public void setName(String name) {
            this.name = name;
        }
    }
}
