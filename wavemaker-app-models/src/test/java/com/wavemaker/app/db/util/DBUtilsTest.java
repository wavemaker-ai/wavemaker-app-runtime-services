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
package com.wavemaker.app.db.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DBUtilsTest {

    @Test
    public void testReplaceMysqlHostPattern() throws Exception {
        String testUrl = "jdbc:mysql://{WM_CLOUD_MYSQL_HOST}/a1?useUnicode=yes&characterEncoding=UTF-8" +
            "&zeroDateTimeBehavior=convertToNull&createDatabaseIfNotExist=true";

        String expectedUrl = "jdbc:mysql://localhost:3306/a1?useUnicode=yes&characterEncoding=UTF-8" +
            "&zeroDateTimeBehavior=convertToNull&createDatabaseIfNotExist=true";

        final String result = DBUtils.replaceMySqlCloudToken(testUrl, "localhost:3306");
        Assertions.assertEquals(expectedUrl, result);
    }

    @Test
    public void testReplaceMysqlHostPatternWithPort() throws Exception {
        String testUrl = "jdbc:mysql://{WM_CLOUD_MYSQL_HOST}:320/a1?useUnicode=yes&characterEncoding=UTF-8" +
            "&zeroDateTimeBehavior=convertToNull&createDatabaseIfNotExist=true";

        String expectedUrl = "jdbc:mysql://localhost:3306/a1?useUnicode=yes&characterEncoding=UTF-8" +
            "&zeroDateTimeBehavior=convertToNull&createDatabaseIfNotExist=true";

        final String result = DBUtils.replaceMySqlCloudToken(testUrl, "localhost:3306");
        Assertions.assertEquals(expectedUrl, result);
    }

    @Test
    public void testReplaceMysqlHostPatternNegative() throws Exception {
        String testUrl = "jdbc:mysql://randomhost:3306/a1?useUnicode=yes&characterEncoding=UTF-8" +
            "&zeroDateTimeBehavior=convertToNull&createDatabaseIfNotExist=true";

        String expectedUrl = "jdbc:mysql://randomhost:3306/a1?useUnicode=yes&characterEncoding=UTF-8" +
            "&zeroDateTimeBehavior=convertToNull&createDatabaseIfNotExist=true";

        final String result = DBUtils.replaceMySqlCloudToken(testUrl, "localhost:3306");
        Assertions.assertEquals(expectedUrl, result);
    }

    @Test
    public void testReplaceMysqlHostPatternNegative2() throws Exception {
        String testUrl = "jdbc:mysql://randomhost/a1?useUnicode=yes&characterEncoding=UTF-8" +
            "&zeroDateTimeBehavior=convertToNull&createDatabaseIfNotExist=true";

        String expectedUrl = "jdbc:mysql://randomhost/a1?useUnicode=yes&characterEncoding=UTF-8" +
            "&zeroDateTimeBehavior=convertToNull&createDatabaseIfNotExist=true";

        final String result = DBUtils.replaceMySqlCloudToken(testUrl, "localhost:3306");
        Assertions.assertEquals(expectedUrl, result);
    }
}
