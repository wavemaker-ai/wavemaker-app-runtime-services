/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc.
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
package com.wavemaker.runtime.data.util;

import org.springframework.core.env.Environment;

/**
 * Connection details of the WaveMaker cloud MySQL that the <code>{WM_CLOUD_MYSQL_*}</code> tokens stand for.
 * Values come from the <code>wm.mysqlCloud*</code> properties of the given {@link Environment}, that is system
 * properties or environment variables.
 */
public final class MySqlCloudProperties {

    private static final String HOST_PROPERTY = "wm.mysqlCloudHost";
    private static final String USER_NAME_PROPERTY = "wm.mysqlCloudUsername";
    private static final String PASSWORD_PROPERTY = "wm.mysqlCloudPassword";

    public static String getHost(Environment environment) {
        return environment.getProperty(HOST_PROPERTY, "localhost:3306");
    }

    public static String getUserName(Environment environment) {
        return environment.getProperty(USER_NAME_PROPERTY, "root");
    }

    public static String getPassword(Environment environment) {
        return environment.getProperty(PASSWORD_PROPERTY, "cloudjee123");
    }

    private MySqlCloudProperties() {
    }
}
