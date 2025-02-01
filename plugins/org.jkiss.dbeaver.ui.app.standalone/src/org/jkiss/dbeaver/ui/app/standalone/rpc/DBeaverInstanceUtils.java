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

package org.jkiss.dbeaver.ui.app.standalone.rpc;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.app.DBPWorkspace;
import org.jkiss.dbeaver.utils.GeneralUtils;

import java.nio.file.Path;

/**
 * DBeaver instance base controller.
 */
public class DBeaverInstanceUtils {

    @NotNull
    protected static Path getConfigPath() {
        return getConfigPath(null);
    }

    @NotNull
    protected static Path getConfigPath(@Nullable String workspacePath) {
        if (workspacePath != null) {
            return Path.of(workspacePath).resolve(DBPWorkspace.METADATA_FOLDER).resolve(IInstanceController.CONFIG_PROP_FILE);
        } else {
            return GeneralUtils.getMetadataFolder().resolve(IInstanceController.CONFIG_PROP_FILE);
        }
    }

}