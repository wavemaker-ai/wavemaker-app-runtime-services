/*******************************************************************************
 * Copyright (C) 2022-2026 WaveMaker, Inc.
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
package com.wavemaker.runtime.data.dao;

import java.util.List;
import java.util.Map;

/**
 * Compile-time firewall between {@code wavemaker-app-runtime-core} and {@code wavemaker-app-runtime-db}.
 * <p>
 * Callers that need to run a Hibernate-backed query (e.g. the security provider database support classes)
 * depend only on this interface and JDK types instead of on {@code runtime-db}'s Hibernate-based
 * implementation, so {@code runtime-core} no longer needs {@code runtime-db} (or Hibernate itself) on its
 * compile classpath. Each row is returned as an ordered map keyed by column position ("0", "1", ...), matching
 * the positional {@code Object[]} shape a plain HQL/native query returns.
 * <p>
 * An executor is already bound to a specific database service (see {@link DatabaseQueryExecutorFactory}) -
 * {@code execute} only takes the query itself.
 */
public interface DatabaseQueryExecutor {

    List<Map<String, Object>> execute(String query, Map<String, Object> params);
}
