/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.util;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.multipart.MultipartFile;

import com.wavemaker.runtime.commons.WMAppContext;
import com.wavemaker.runtime.data.support.AbstractHibernateIntegrationTest;
import com.wavemaker.runtime.data.support.ByteArrayMultipartFile;
import com.wavemaker.runtime.data.support.StubServletRequests;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Covers the one part of {@link WMMultipartUtils} that needs a real Hibernate session: setting a
 * {@code java.sql.Blob}-typed target field from an uploaded multipart part, which internally calls
 * {@code Hibernate.getLobCreator(session).createBlob(...)}. {@code WMAppContext} is wired the same way
 * {@code service/QueryDesignServiceImplIntegrationTest} does it - it's a Spring-only static singleton with no
 * container anywhere else in this harness.
 */
class WMMultipartUtilsBlobIntegrationTest extends AbstractHibernateIntegrationTest {

    private static final String SERVICE_ID = "test";

    @BeforeAll
    static void wireWmAppContextWithARealSessionFactory() throws Exception {
        Constructor<WMAppContext> constructor = WMAppContext.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        if (WMAppContext.getInstance() == null) {
            constructor.newInstance();
        }

        StaticApplicationContext context = new StaticApplicationContext();
        context.getDefaultListableBeanFactory().registerSingleton(SERVICE_ID + "SessionFactory", sessionFactory);
        WMAppContext.getInstance().setApplicationContext(context);
    }

    @Test
    void toObjectFromARequestSetsABlobTypedFieldUsingTheRealSessionsLobCreator() throws Exception {
        byte[] content = "blob content".getBytes(StandardCharsets.UTF_8);
        MultipartFile jsonFile = new ByteArrayMultipartFile("wm_data_json", "wm_data_json", "application/json", "{}".getBytes(StandardCharsets.UTF_8));
        MultipartFile blobFile = new ByteArrayMultipartFile("attachment", "attachment.bin", "application/octet-stream", content);

        var request = StubServletRequests.multipartHttpServletRequest(
            Map.of("wm_data_json", jsonFile, "attachment", blobFile), Map.of());

        BlobTarget result = WMMultipartUtils.toObject(request, BlobTarget.class, SERVICE_ID);

        assertArrayEquals(content, IOUtils.toByteArray(result.getAttachment().getBinaryStream()));
    }

    public static class BlobTarget {
        private Blob attachment;

        public Blob getAttachment() {
            return attachment;
        }

        public void setAttachment(final Blob attachment) {
            this.attachment = attachment;
        }
    }
}
