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
package com.wavemaker.runtime.data.datasource;

/**
 * Naming convention of the datasource beans registered for a database service (see the generated spring xml of a
 * database service).
 */
public final class DataSourceBeanNames {

    private static final String DATA_SOURCE_SUFFIX = "DataSource";
    private static final String MANAGED_DATA_SOURCE_SUFFIX = "WMManagedDataSource";

    private DataSourceBeanNames() {
    }

    /**
     * @return name of the {@code DataSource} bean exposed to the application for the given service
     */
    public static String dataSource(final String serviceId) {
        return serviceId + DATA_SOURCE_SUFFIX;
    }

    /**
     * @return name of the pooled/managed {@code DataSource} bean that backs the service's datasource
     */
    public static String managedDataSource(final String serviceId) {
        return serviceId + MANAGED_DATA_SOURCE_SUFFIX;
    }
}
