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
package com.wavemaker.runtime.data;

import java.io.IOException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.config.PropertiesFactoryBean;

/**
 * The Hibernate properties of a data service. The versioned Hibernate dialects like {@code MySQL57Dialect} or
 * {@code PostgreSQL95Dialect} of an existing application are replaced by the dialect of the database, as Hibernate 7 has
 * one dialect for all the versions of a database.
 */
public class HibernatePropertiesFactoryBean extends PropertiesFactoryBean {

    private static final String DIALECT_PROPERTY = "hibernate.dialect";

    private static final Pattern VERSIONED_DIALECT = Pattern.compile("^org\\.hibernate\\.dialect\\.([A-Za-z]+?)(?:[0-9]+[a-z]?)?(?:InnoDB|MyISAM|Spatial)?[0-9]*Dialect$");

    @Override
    protected Properties createProperties() throws IOException {
        Properties properties = super.createProperties();
        String dialect = properties.getProperty(DIALECT_PROPERTY);
        if (dialect != null) {
            properties.setProperty(DIALECT_PROPERTY, unversioned(dialect.trim()));
        }
        return properties;
    }

    static String unversioned(String dialect) {
        Matcher matcher = VERSIONED_DIALECT.matcher(dialect);
        if (!matcher.matches()) {
            return dialect;
        }
        String database = matcher.group(1);
        return switch (database) {
            case "MySQL", "Oracle", "PostgreSQL", "SQLServer", "MariaDB", "DB2", "HSQL", "H2", "Informix", "SQLite" ->
                "org.hibernate.dialect." + database + "Dialect";
            default -> dialect;
        };
    }
}
