/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.util;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import com.wavemaker.runtime.commons.file.model.DownloadResponse;
import com.wavemaker.runtime.data.support.ByteArrayMultipartFile;
import com.wavemaker.runtime.data.support.StubServletRequests;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-unit coverage of {@link WMMultipartUtils} - everything here needs no Hibernate session at all. The one
 * method that does (the {@code java.sql.Blob}-typed field branch of {@code toObject(MultipartHttpServletRequest,
 * Class, String)}, reached only when a target field's type is {@code Blob}) is covered separately in
 * {@link WMMultipartUtilsBlobIntegrationTest}.
 */
class WMMultipartUtilsTest {

    @Test
    void toObjectFromAMultipartFileParsesItsJsonContent() throws Exception {
        MultipartFile file = new ByteArrayMultipartFile("f", "f.json", "application/json",
            "{\"name\":\"Alice\"}".getBytes(StandardCharsets.UTF_8));

        MultipartTarget result = WMMultipartUtils.toObject(file, MultipartTarget.class);

        assertEquals("Alice", result.getName());
    }

    @Test
    void toObjectFromAPlainJsonStringParsesIt() throws Exception {
        MultipartTarget result = WMMultipartUtils.toObject("{\"name\":\"Bob\"}", MultipartTarget.class);

        assertEquals("Bob", result.getName());
    }

    @Test
    void toObjectFromARequestSetsFileFieldsFromTheOtherMultipartParts() {
        MultipartFile jsonFile = new ByteArrayMultipartFile("wm_data_json", "wm_data_json", "application/json",
            "{\"name\":\"Carol\"}".getBytes(StandardCharsets.UTF_8));
        MultipartFile photoFile = new ByteArrayMultipartFile("photo", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3});

        var request = StubServletRequests.multipartHttpServletRequest(
            Map.of("wm_data_json", jsonFile, "photo", photoFile), Map.of());

        MultipartTarget result = WMMultipartUtils.toObject(request, MultipartTarget.class);

        assertEquals("Carol", result.getName());
        assertArrayEquals(new byte[] {1, 2, 3}, result.getPhoto());
    }

    @Test
    void updateLobsContentCopiesTheOldByteArrayWhenTheNewInstancesFieldIsEmpty() {
        MultipartTarget oldInstance = new MultipartTarget();
        oldInstance.setPhoto(new byte[] {9, 9, 9});
        MultipartTarget newInstance = new MultipartTarget();
        newInstance.setPhoto(new byte[0]);

        MultipartTarget result = WMMultipartUtils.updateLobsContent(oldInstance, newInstance);

        assertArrayEquals(new byte[] {9, 9, 9}, result.getPhoto());
    }

    @Test
    void updateLobsContentLeavesTheNewValueAloneWhenItIsAlreadyPresent() {
        MultipartTarget oldInstance = new MultipartTarget();
        oldInstance.setPhoto(new byte[] {9, 9, 9});
        MultipartTarget newInstance = new MultipartTarget();
        newInstance.setPhoto(new byte[] {1, 1, 1});

        MultipartTarget result = WMMultipartUtils.updateLobsContent(oldInstance, newInstance);

        assertArrayEquals(new byte[] {1, 1, 1}, result.getPhoto());
    }

    @Test
    void toByteArrayReturnsNullForANullFileRatherThanThrowing() {
        assertNull(WMMultipartUtils.toByteArray(null));
    }

    @Test
    void toByteArrayReturnsTheFilesRawBytes() {
        MultipartFile file = new ByteArrayMultipartFile("f", "f.txt", "text/plain", new byte[] {5, 6, 7});

        assertArrayEquals(new byte[] {5, 6, 7}, WMMultipartUtils.toByteArray(file));
    }

    @Test
    void guessContentTypeDetectsAPlainTextByteStream() {
        String contentType = WMMultipartUtils.guessContentType("just some plain text content".getBytes(StandardCharsets.UTF_8));

        assertTrue(contentType.startsWith("text/plain"), "expected a text/plain content type, got: " + contentType);
    }

    @Test
    void buildDownloadResponseSniffsContentTypeFromTheStreamEvenWhenAFilenameIsGiven() {
        // Surprising real behavior: this method always tries Tika content-sniffing on the actual bytes FIRST
        // (getMimeTypeFromInputStream), and only falls back to guessing from the filename's own extension if
        // that fails. The filename parameter supplies the base name, not the extension - if the sniffed
        // extension doesn't match what's already on the filename, it gets appended, even if that means a
        // filename ending up with two extensions. This is the opposite priority order from
        // buildDownloadResponseForBlob, which prefers the filename-based guess first.
        var request = StubServletRequests.httpServletRequest(Map.of("filename", "report.csv"));

        DownloadResponse response = WMMultipartUtils.buildDownloadResponse(
            request, new java.io.ByteArrayInputStream("a,b,c".getBytes(StandardCharsets.UTF_8)), true);

        assertEquals("report.csv.txt", response.getFileName());
        assertNotNull(response.getContentType());
        assertTrue(!response.isInline(), "download=true should mean not inline");
    }

    @Test
    void buildDownloadResponseGeneratesARandomFilenameWhenNoneIsProvided() {
        var request = StubServletRequests.httpServletRequest(Map.of());

        DownloadResponse response = WMMultipartUtils.buildDownloadResponse(
            request, new java.io.ByteArrayInputStream("a,b,c".getBytes(StandardCharsets.UTF_8)), false);

        assertNotNull(response.getFileName());
        assertTrue(response.isInline(), "download=false should mean inline");
    }

    @Test
    void buildDownloadResponseForBlobReadsAByteArrayReturningFieldByReflection() {
        MultipartTarget instance = new MultipartTarget();
        instance.setPhoto(new byte[] {1, 2, 3, 4});
        var request = StubServletRequests.httpServletRequest(Map.of());

        DownloadResponse response = WMMultipartUtils.buildDownloadResponseForBlob(instance, "photo", request, false);

        assertNotNull(response.getFileName());
        assertNotNull(response.getContentType());
    }

    public static class MultipartTarget {
        private String name;
        private byte[] photo;

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public byte[] getPhoto() {
            return photo;
        }

        public void setPhoto(final byte[] photo) {
            this.photo = photo;
        }
    }
}
