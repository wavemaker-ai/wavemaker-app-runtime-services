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
package com.wavemaker.runtime.data.props;

import java.util.stream.Stream;

import org.springframework.context.EnvironmentAware;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

import com.wavemaker.commons.util.StringUtils;
import com.wavemaker.runtime.commons.props.PropertyTokenResolver;
import com.wavemaker.runtime.data.util.DataServiceConstants;
import com.wavemaker.runtime.data.util.DataServiceUtils;
import com.wavemaker.runtime.data.util.MySqlCloudProperties;

/**
 * Replaces the WaveMaker cloud MySQL host, username and password tokens.
 */
@Order(2)
public class MySqlCloudTokenResolver implements PropertyTokenResolver, EnvironmentAware {

    private Environment environment;

    @Override
    public boolean canResolve(String value) {
        return Stream.of(
            DataServiceConstants.WM_MY_SQL_CLOUD_HOST_TOKEN,
            DataServiceConstants.WM_MY_SQL_CLOUD_USER_NAME_TOKEN,
            DataServiceConstants.WM_MY_SQL_CLOUD_PASSWORD_TOKEN
        ).anyMatch(value::contains);
    }

    @Override
    public String resolve(String value) {
        if (value.contains(DataServiceConstants.WM_MY_SQL_CLOUD_HOST_TOKEN)) {
            value = DataServiceUtils.replaceMySqlCloudToken(value, MySqlCloudProperties.getHost(environment));
        }
        if (value.contains(DataServiceConstants.WM_MY_SQL_CLOUD_USER_NAME_TOKEN)) {
            value = StringUtils.replacePlainStr(value, DataServiceConstants.WM_MY_SQL_CLOUD_USER_NAME_TOKEN,
                MySqlCloudProperties.getUserName(environment));
        }
        if (value.contains(DataServiceConstants.WM_MY_SQL_CLOUD_PASSWORD_TOKEN)) {
            value = StringUtils.replacePlainStr(value, DataServiceConstants.WM_MY_SQL_CLOUD_PASSWORD_TOKEN,
                MySqlCloudProperties.getPassword(environment));
        }
        return value;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}
