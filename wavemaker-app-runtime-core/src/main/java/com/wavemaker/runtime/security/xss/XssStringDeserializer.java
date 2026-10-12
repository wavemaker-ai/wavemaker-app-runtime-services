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

package com.wavemaker.runtime.security.xss;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

import com.wavemaker.runtime.security.xss.handler.XSSSecurityHandler;

public class XssStringDeserializer extends StdDeserializer<String> {
    public XssStringDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser jsonParser, DeserializationContext ctxt) {
        XSSSecurityHandler xssSecurityHandler = XSSSecurityHandler.getInstance();
        String value = jsonParser.getText();
        if (xssSecurityHandler.isInputSanitizationEnabled() && XssContext.isXssEnabled()) {
            return xssSecurityHandler.sanitizeIncomingData(value);
        } else {
            return value;
        }
    }
}
