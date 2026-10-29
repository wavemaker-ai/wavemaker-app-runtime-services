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
package com.wavemaker.runtime.commons;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpHeaders;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.PropertyNamingStrategy;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.AnnotatedMethod;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.FilterProvider;
import tools.jackson.databind.ext.sql.JavaSqlDateSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.TypeFactory;
import com.wavemaker.commons.json.Jackson2AccessorNamingStrategy;
import com.wavemaker.commons.json.deserializer.HttpHeadersDeSerializer;
import com.wavemaker.commons.json.deserializer.WMDateDeSerializer;
import com.wavemaker.commons.json.deserializer.WMSqlDateDeSerializer;
import com.wavemaker.commons.json.module.WMJacksonModule;
import com.wavemaker.commons.json.serializer.HttpHeadersSerializer;
import com.wavemaker.commons.json.serializer.NoOpByteArraySerializer;
import com.wavemaker.commons.json.serializer.WMLocalDateTimeSerializer;
import com.wavemaker.runtime.commons.mixins.SliceMixin;

public class WMObjectMapper extends JsonMapper {

    private static final Logger logger = LoggerFactory.getLogger(WMObjectMapper.class);

    private static final Jackson2AccessorNamingStrategy.Provider ACCESSOR_NAMING = new Jackson2AccessorNamingStrategy.Provider();
    private static final WMObjectMapper INSTANCE = new WMObjectMapper();
    private static final WMPropertyNamingStrategy PROPERTY_NAMING_STRATEGY = new WMPropertyNamingStrategy();

    private volatile ObjectMapper readMapper;
    private volatile ObjectMapper writeMapper;

    protected WMObjectMapper() {
        super(JsonMapper.builderWithJackson2Defaults()
            .enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            .addModule(new SimpleModule("WMHttpHeaders")
                .addSerializer(HttpHeaders.class, new HttpHeadersSerializer())
                .addDeserializer(HttpHeaders.class, new HttpHeadersDeSerializer()))
            .accessorNaming(ACCESSOR_NAMING));
        readMapper = createReadMapper();
        writeMapper = createWriteMapper();
    }

    public static WMObjectMapper getInstance() {
        return INSTANCE;
    }

    /**
     * Method that can be used to serialize any Java value as
     * JSON output, written to File provided.
     */
    @Override
    public void writeValue(File resultFile, Object value) {
        writeMapper.writeValue(resultFile, value);
    }

    @Override
    public void writeValue(OutputStream out, Object value) {
        writeMapper.writeValue(out, value);
    }

    @Override
    public void writeValue(Writer w, Object value) {
        writeMapper.writeValue(w, value);
    }

    @Override
    public String writeValueAsString(Object value)
        throws JacksonException {
        return writeMapper.writeValueAsString(value);
    }

    @Override
    public byte[] writeValueAsBytes(Object value)
        throws JacksonException {
        return writeMapper.writeValueAsBytes(value);
    }

    @Override
    public void writeValue(JsonGenerator jgen, Object value) {
        writeMapper.writeValue(jgen, value);
    }

    @Override
    public void writeTree(JsonGenerator jgen, JsonNode rootNode) {
        writeMapper.writeValue(jgen, rootNode);
    }

    @Override
    public ObjectWriter writer() {
        return writeMapper.writer();
    }

    @Override
    public ObjectWriter writerWithView(final Class<?> serializationView) {
        return writeMapper.writerWithView(serializationView);
    }

    @Override
    public ObjectWriter writer(final FilterProvider filterProvider) {
        return writeMapper.writer(filterProvider);
    }

    @Override
    public <T> T readValue(File src, Class<T> valueType) {
        return readMapper.readValue(src, valueType);
    }

    @Override
    public <T> T readValue(File src, TypeReference<T> valueTypeRef) {
        return readMapper.readValue(src, valueTypeRef);
    }

    @Override
    public <T> T readValue(File src, JavaType valueType) {
        return readMapper.readValue(src, valueType);
    }

    @Override
    public <T> T readValue(String content, Class<T> valueType) throws JacksonException {
        return readMapper.readValue(content, valueType);
    }

    @Override
    public <T> T readValue(String content, TypeReference<T> valueTypeRef) throws JacksonException {
        return readMapper.readValue(content, valueTypeRef);
    }

    @Override
    public <T> T readValue(String content, JavaType valueType) throws JacksonException {
        return readMapper.readValue(content, valueType);
    }

    @Override
    public <T> T readValue(Reader src, Class<T> valueType) {
        return readMapper.readValue(src, valueType);
    }

    @Override
    public <T> T readValue(Reader src, TypeReference<T> valueTypeRef) {
        return readMapper.readValue(src, valueTypeRef);
    }

    @Override
    public <T> T readValue(Reader src, JavaType valueType) {
        return readMapper.readValue(src, valueType);
    }

    @Override
    public <T> T readValue(InputStream src, Class<T> valueType) {
        return readMapper.readValue(src, valueType);
    }

    @Override
    public <T> T readValue(InputStream src, TypeReference<T> valueTypeRef) {
        return readMapper.readValue(src, valueTypeRef);
    }

    @Override
    public <T> T readValue(byte[] src, Class<T> valueType) {
        return readMapper.readValue(src, valueType);
    }

    @Override
    public <T> T readValue(
        byte[] src, int offset, int len,
        Class<T> valueType) {
        return readMapper.readValue(src, offset, len, valueType);
    }

    @Override
    public <T> T readValue(byte[] src, TypeReference<T> valueTypeRef) {
        return readMapper.readValue(src, valueTypeRef);
    }

    @Override
    public <T> T readValue(
        byte[] src, int offset, int len,
        TypeReference<T> valueTypeRef) {
        return readMapper.readValue(src, offset, len, valueTypeRef);
    }

    @Override
    public <T> T readValue(byte[] src, JavaType valueType) {
        return readMapper.readValue(src, valueType);
    }

    @Override
    public <T> T readValue(
        byte[] src, int offset, int len,
        JavaType valueType) {
        return readMapper.readValue(src, offset, len, valueType);
    }

    @Override
    public <T> T readValue(InputStream src, JavaType valueType) {
        if (String.class.equals(valueType.getRawClass())) {
            StringWriter stringWriter = new StringWriter();
            try {
                IOUtils.copy(src, stringWriter, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw JacksonIOException.construct(e);
            }
            return (T) stringWriter.toString();
        }
        return readMapper.readValue(src, valueType);
    }

    @Override
    public ObjectWriter writerWithDefaultPrettyPrinter() {
        return writeMapper.writerWithDefaultPrettyPrinter();
    }

    @Override
    public ObjectReader reader() {
        return readMapper.reader();
    }

    @Override
    public ObjectReader readerFor(JavaType type) {
        return readMapper.readerFor(type);
    }

    public synchronized void registerReaderModule(final JacksonModule mapperModule) {
        readMapper = readMapper.rebuild().addModule(mapperModule).build();
    }

    public synchronized void registerWriteModule(final JacksonModule mapperModule) {
        writeMapper = writeMapper.rebuild().addModule(mapperModule).build();
    }

    private static ObjectMapper createReadMapper() {
        SimpleModule module = new SimpleModule("WMDefaultDeSerializer");

        module.addDeserializer(Date.class, new WMDateDeSerializer());
        module.addDeserializer(java.sql.Date.class, new WMSqlDateDeSerializer());
        module.addDeserializer(HttpHeaders.class, new HttpHeadersDeSerializer());

        return JsonMapper.builderWithJackson2Defaults()
            .typeFactory(TypeFactory.createDefaultInstance().withClassLoader(WMObjectMapper.class.getClassLoader()))
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            .addModule(module)
            .accessorNaming(ACCESSOR_NAMING)
            .propertyNamingStrategy(PROPERTY_NAMING_STRATEGY)
            .build();
    }

    private static ObjectMapper createWriteMapper() {
        SimpleModule module = new SimpleModule("WMDefaultSerializer");
        module.addSerializer(byte[].class, new NoOpByteArraySerializer());
        module.addSerializer(HttpHeaders.class, new HttpHeadersSerializer());
//            module.addSerializer(Sort.class, new SortJsonSerializer());

        SimpleModule javaTimeModule = new SimpleModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new WMLocalDateTimeSerializer());

        SimpleModule dateModule = new SimpleModule();
        dateModule.addSerializer(java.sql.Date.class,
            new JavaSqlDateSerializer().withFormat(false, new SimpleDateFormat("yyyy-MM-dd")));

        JsonMapper.Builder builder = JsonMapper.builderWithJackson2Defaults()
            .typeFactory(TypeFactory.createDefaultInstance().withClassLoader(WMObjectMapper.class.getClassLoader()))
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            // we are handling self references using @JsonIgnoreProperties
            .configure(SerializationFeature.FAIL_ON_SELF_REFERENCES, false)
            .enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            .defaultSerializationView(Object.class)
            // mixing to ignore pageable field from page response.
            .addMixIn(Slice.class, SliceMixin.class)
            .addModule(module)
            .addModule(javaTimeModule)
            .addModule(new WMJacksonModule(false))
            .addModule(dateModule)
            .accessorNaming(ACCESSOR_NAMING)
            .propertyNamingStrategy(PROPERTY_NAMING_STRATEGY);

        Iterator<WMObjectMapperModuleProvider> providers = ServiceLoader
            .load(WMObjectMapperModuleProvider.class, WMObjectMapper.class.getClassLoader()).iterator();
        String lastError = null;
        while (true) {
            try {
                if (!providers.hasNext()) {
                    break;
                }
                builder.addModule(providers.next().getModule());
            } catch (ServiceConfigurationError | LinkageError e) {
                if (e.toString().equals(lastError)) {
                    break;
                }
                lastError = e.toString();
                logger.warn("Skipping a Jackson module as its library is not available: {}", lastError);
            }
        }

        return builder.build();
    }

    private static class WMPropertyNamingStrategy extends PropertyNamingStrategy {

        private static final String[] POSSIBLE_GET_METHOD_START_NAMES = {"get", "is"};
        private static final String[] POSSIBLE_SET_METHOD_START_NAMES = {"set"};

        @Override
        public String nameForGetterMethod(MapperConfig<?> config, AnnotatedMethod method, String defaultName) {
            return getPossibleFieldName(method, defaultName, POSSIBLE_GET_METHOD_START_NAMES);
        }

        @Override
        public String nameForSetterMethod(MapperConfig<?> config, AnnotatedMethod method, String defaultName) {
            return getPossibleFieldName(method, defaultName, POSSIBLE_SET_METHOD_START_NAMES);
        }

        private String getPossibleFieldName(
            AnnotatedMethod method, String defaultName, String[] possibleMethodStartNames) {
            String name = method.getName();
            for (String possibleStartName : possibleMethodStartNames) {
                if (name.startsWith(possibleStartName)) {
                    String remPart = name.substring(possibleStartName.length());
                    if (remPart.isEmpty()) {
                        break;
                    }
                    char upper = remPart.charAt(0);
                    char lower = Character.toLowerCase(upper);
                    if (lower == upper) {
                        break;
                    }
                    StringBuilder sb = new StringBuilder(remPart);
                    sb.setCharAt(0, lower);
                    return sb.toString();
                }
            }
            return defaultName;
        }
    }

}
