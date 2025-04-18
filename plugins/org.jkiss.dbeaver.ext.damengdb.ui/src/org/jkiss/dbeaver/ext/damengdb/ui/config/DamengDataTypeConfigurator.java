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
package org.jkiss.dbeaver.ext.damengdb.ui.config;

import org.jkiss.dbeaver.ext.damengdb.model.DamengDataType;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEObjectConfigurator;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSEntityType;
import org.jkiss.dbeaver.ui.UITask;
import org.jkiss.dbeaver.ui.editors.object.struct.EntityEditPage;

import java.util.Map;

/**
 * DamengDataTypeConfigurator
 */
public class DamengDataTypeConfigurator implements DBEObjectConfigurator<DamengDataType> {

    @Override
    public DamengDataType configureObject(DBRProgressMonitor monitor, DBECommandContext commandContext, Object parent,
                                          DamengDataType dataType, Map<String, Object> options) {
        return UITask.run(() -> {
            EntityEditPage editPage = new EntityEditPage(dataType.getDataSource(), DBSEntityType.TYPE);
            if (!editPage.edit()) {
                return null;
            }
            dataType.setName(editPage.getEntityName());
            dataType.setObjectDefinitionText("CREATE OR REPLACE CLASS " + dataType.getName() + " AS \n\nEND" //$NON-NLS-1$ //$NON-NLS-2$
            ); // $NON-NLS-1$
            return dataType;
        });
    }
    
}
