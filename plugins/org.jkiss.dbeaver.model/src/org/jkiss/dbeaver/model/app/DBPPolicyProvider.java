/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.model.app;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;

/**
 * Provides policy data
 */
public interface DBPPolicyProvider {
    /**
     * Return boolean value of policy data property
     *
     * @param propertyName - property name
     * @return - boolean value
     */
    boolean isPolicyEnabled(@NotNull String propertyName);

    /**
     * Retrieves policy data value from system environment or Windows registry
     *
     * @param propertyName  policy property name
     * @return policy data value or {@code null} if not found
     */
    @Nullable
    Object getPolicyValue(@NotNull String propertyName);

    /**
     * Retrieves policy data value from system environment or Windows registry
     *
     * @param property  policy data property
     * @return policy data value or {@code null} if not found
     */
    @Nullable
    Object getPolicyProperty(@NotNull String property);
}
