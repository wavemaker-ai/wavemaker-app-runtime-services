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
package com.wavemaker.runtime.security.session;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import jakarta.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.boot.jdbc.init.PlatformPlaceholderDatabaseDriverResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.util.ResourceUtils;

import com.wavemaker.commons.WMRuntimeException;

public class JdbcSessionScriptInitializer {

    private static final String SCHEMA_LOCATION_PATTERN = "classpath:org/springframework/session/jdbc/schema-@@platform@@.sql";

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcSessionScriptInitializer.class);

    private final DataSource dataSource;

    public JdbcSessionScriptInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initializeScript() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String tableName = "SPRING_SESSION";
            if ("PostgreSQL".equals(metaData.getDatabaseProductName())) {
                //PostgreSQL is storing tableNames in lower case so getTables check also need to be done on the lowercase
                tableName = StringUtils.lowerCase(tableName);
            }
            try (ResultSet resultSet = metaData.getTables(connection.getCatalog(), null, tableName, new String[]{"TABLE"})) {
                if (resultSet.next()) {
                    LOGGER.debug("Skipping SPRING_SESSION table creation as it already exists");
                    return;
                }
            }
            ClassPathResource classPathResource = getSchemaResource();
            LOGGER.debug("executing sql script from resource: {}", classPathResource.getPath());
            ResourceDatabasePopulator resourceDatabasePopulator = new ResourceDatabasePopulator();
            resourceDatabasePopulator.addScript(classPathResource);
            resourceDatabasePopulator.populate(connection);
        } catch (SQLException throwables) {
            throw new WMRuntimeException(throwables.getCause());
        }
    }

    private ClassPathResource getSchemaResource() {
        // Same platform resolution Spring Boot uses for its own Spring Session JDBC schema initialization
        List<String> locations = new PlatformPlaceholderDatabaseDriverResolver()
            .withDriverPlatform(DatabaseDriver.MARIADB, "mysql")
            .resolveAll(dataSource, SCHEMA_LOCATION_PATTERN);
        String path = StringUtils.removeStart(locations.get(0), ResourceUtils.CLASSPATH_URL_PREFIX);
        ClassPathResource classPathResource = new ClassPathResource(path);
        if (!classPathResource.exists()) {
            throw new WMRuntimeException("No Spring Session JDBC schema script found for the database at " + path);
        }
        return classPathResource;
    }
}
