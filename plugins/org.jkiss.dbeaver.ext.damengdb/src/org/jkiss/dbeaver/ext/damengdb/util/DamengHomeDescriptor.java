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
package org.jkiss.dbeaver.ext.damengdb.util;

import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.damengdb.DamengDataSourceProvider;
import org.jkiss.dbeaver.model.connection.LocalNativeClientLocation;
import org.jkiss.utils.CommonUtils;

public class DamengHomeDescriptor extends LocalNativeClientLocation {
	
    private static final Log log = Log.getLog(DamengHomeDescriptor.class);

    private Integer dmVersion; // short version (7, 8...)

    private String displayName;

    public DamengHomeDescriptor(String dmHome) {
        super(CommonUtils.removeTrailingSlash(dmHome), dmHome);
        this.dmVersion = DamengDataSourceProvider.getDmVersion(this);
        if (dmVersion == null) {
            log.debug("Unrecognized dm client version at " + dmHome);
        }
        this.displayName = DamengEnvUtils.readWinRegistry(dmHome, DamengEnvUtils.WIN_REG_DM_HOME_NAME);
    }

    @Override
    public String getDisplayName() {
        if (displayName != null) {
            return displayName;
        } else {
            return getName();
        }
    }
    
}
