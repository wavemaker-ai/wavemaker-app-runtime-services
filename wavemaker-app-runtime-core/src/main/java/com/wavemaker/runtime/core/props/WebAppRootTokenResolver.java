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
package com.wavemaker.runtime.core.props;

import jakarta.servlet.ServletContext;

import org.springframework.core.annotation.Order;
import org.springframework.web.context.ServletContextAware;

import com.wavemaker.commons.util.StringUtils;
import com.wavemaker.runtime.commons.props.PropertyTokenResolver;
import com.wavemaker.runtime.data.util.DataServiceConstants;

/**
 * Replaces the <code>{WebAppRoot}</code> token with the real path of the web application root. Runs before the
 * other resolvers, as the token was always replaced first.
 */
@Order(1)
public class WebAppRootTokenResolver implements PropertyTokenResolver, ServletContextAware {

    private ServletContext servletContext;

    @Override
    public boolean canResolve(String value) {
        return servletContext != null && value.contains(DataServiceConstants.WEB_ROOT_TOKEN);
    }

    @Override
    public String resolve(String value) {
        String path = servletContext.getRealPath("/");
        if (org.apache.commons.lang3.StringUtils.isBlank(path)) {
            return value;
        }
        return StringUtils.replacePlainStr(value, DataServiceConstants.WEB_ROOT_TOKEN, path);
    }

    @Override
    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }
}
