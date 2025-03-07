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

package org.jkiss.dbeaver.registry.driver;

import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.connection.DBPDriverLibrary;
import org.jkiss.dbeaver.model.connection.DBPDriverLoader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DriverLoaderDescriptor
 */
public abstract class DriverLoaderDescriptor implements DBPDriverLoader {
    private final Map<DBPDriverLibrary, List<DriverDescriptor.DriverFileInfo>> resolvedFiles = new HashMap<>();

    private Class<?> driverClass;
    private boolean isLoaded;
    private DriverClassLoader classLoader;

    private transient boolean isFailed = false;

    @Nullable
    @Override
    public DriverClassLoader getClassLoader() {
        return classLoader;
    }


    Map<DBPDriverLibrary, List<DriverDescriptor.DriverFileInfo>> getResolvedFiles() {
        return resolvedFiles;
    }


}
