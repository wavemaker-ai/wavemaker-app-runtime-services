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
package com.wavemaker.runtime.data.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

import static org.junit.jupiter.api.Assertions.*;

import com.wavemaker.runtime.data.exception.DataServiceRuntimeException;

/**
 * Created by anitha on 2/11/15.
 */
public class DataServiceUtilsTest {

    @Test
    public void umwrapTest() {
        Exception e = new DataServiceRuntimeException("test message");
        RuntimeException e1 = new RuntimeException("abcd", e);
        RuntimeException runtimeException = new RuntimeException("some cause");
        RuntimeException e2 = new RuntimeException("efgh", e1);
        Exception e3 = new NullPointerException("Null Values");
        InvalidDataAccessResourceUsageException invalid = new InvalidDataAccessResourceUsageException("Invalid data");
        RuntimeException rx = new IndexOutOfBoundsException("Check Array");
        Assertions.assertEquals(e, DataServiceUtils.unwrap(e));
        Assertions.assertEquals(e, DataServiceUtils.unwrap(e2));
        Assertions.assertEquals(rx, DataServiceUtils.unwrap(rx));
        Assertions.assertEquals(invalid, DataServiceUtils.unwrap(invalid));
        Assertions.assertEquals(e, DataServiceUtils.unwrap(e1));
        Assertions.assertEquals(runtimeException, DataServiceUtils.unwrap(runtimeException));
        Assertions.assertEquals(e3, DataServiceUtils.unwrap(e3));

    }

    @Test
    public void isDMLTest() {
        String query1 = "insert into detail values(1,'user','pwd')";
        String query2 = " update detail set password = 'abc123' where user= 'abc'";
        String query3 = "select * from detail";
        String query4 = "DELETE from detail where id=4";
        String query5 = "alter table detail add employeeid int";
        Assertions.assertTrue(DataServiceUtils.isDML(query1));
        Assertions.assertTrue(DataServiceUtils.isDML(query2));
        Assertions.assertFalse(DataServiceUtils.isDML(query3));
        Assertions.assertTrue(DataServiceUtils.isDML(query4));
        Assertions.assertTrue(DataServiceUtils.isDML(query5));
    }

    @Test
    public void testReplaceMysqlHostPattern() throws Exception {
        String testUrl = "jdbc:mysql://{WM_CLOUD_MYSQL_HOST}/a1?useUnicode=yes&characterEncoding=UTF-8" +
            "&zeroDateTimeBehavior=convertToNull&createDatabaseIfNotExist=true";

        String expectedUrl = "jdbc:mysql://localhost:3306/a1?useUnicode=yes&characterEncoding=UTF-8" +
            "&zeroDateTimeBehavior=convertToNull&createDatabaseIfNotExist=true";

        final String result = DataServiceUtils.replaceMySqlCloudToken(testUrl, "localhost:3306");
        Assertions.assertEquals(expectedUrl, result);
    }

    @Test
    public void testReplaceMysqlHostPatternWithPort() throws Exception {
        String testUrl = "jdbc:mysql://{WM_CLOUD_MYSQL_HOST}:320/a1?useUnicode=yes&characterEncoding=UTF-8" +
            "&zeroDateTimeBehavior=convertToNull&createDatabaseIfNotExist=true";

        String expectedUrl = "jdbc:mysql://localhost:3306/a1?useUnicode=yes&characterEncoding=UTF-8" +
            "&zeroDateTimeBehavior=convertToNull&createDatabaseIfNotExist=true";

        final String result = DataServiceUtils.replaceMySqlCloudToken(testUrl, "localhost:3306");
        Assertions.assertEquals(expectedUrl, result);
    }

    @Test
    public void testReplaceMysqlHostPatternNegative() throws Exception {
        String testUrl = "jdbc:mysql://randomhost:3306/a1?useUnicode=yes&characterEncoding=UTF-8" +
            "&zeroDateTimeBehavior=convertToNull&createDatabaseIfNotExist=true";

        String expectedUrl = "jdbc:mysql://randomhost:3306/a1?useUnicode=yes&characterEncoding=UTF-8" +
            "&zeroDateTimeBehavior=convertToNull&createDatabaseIfNotExist=true";

        final String result = DataServiceUtils.replaceMySqlCloudToken(testUrl, "localhost:3306");
        Assertions.assertEquals(expectedUrl, result);
    }

    @Test
    public void testReplaceMysqlHostPatternNegative2() throws Exception {
        String testUrl = "jdbc:mysql://randomhost/a1?useUnicode=yes&characterEncoding=UTF-8" +
            "&zeroDateTimeBehavior=convertToNull&createDatabaseIfNotExist=true";

        String expectedUrl = "jdbc:mysql://randomhost/a1?useUnicode=yes&characterEncoding=UTF-8" +
            "&zeroDateTimeBehavior=convertToNull&createDatabaseIfNotExist=true";

        final String result = DataServiceUtils.replaceMySqlCloudToken(testUrl, "localhost:3306");
        Assertions.assertEquals(expectedUrl, result);
    }
}
