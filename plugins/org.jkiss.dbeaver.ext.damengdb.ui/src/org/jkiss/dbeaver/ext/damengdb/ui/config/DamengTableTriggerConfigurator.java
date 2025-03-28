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

import org.jkiss.dbeaver.ext.damengdb.model.DamengTableTrigger;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEObjectConfigurator;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSEntityType;
import org.jkiss.dbeaver.ui.UITask;
import org.jkiss.dbeaver.ui.editors.object.struct.EntityEditPage;

import java.util.Map;

/**
 * DamengTableTriggerConfigurator
 */
public class DamengTableTriggerConfigurator implements DBEObjectConfigurator<DamengTableTrigger> {
    
	@Override
    public DamengTableTrigger configureObject(DBRProgressMonitor monitor, DBECommandContext commandContext,
                                              Object container, DamengTableTrigger newTrigger, Map<String, Object> options) {
        return UITask.run(() -> {
            EntityEditPage editPage = new EntityEditPage(newTrigger.getDataSource(), DBSEntityType.TRIGGER);
            if (!editPage.edit()) {
                return null;
            }
            newTrigger.setName(editPage.getEntityName());
            newTrigger.setObjectDefinitionText("TRIGGER " + editPage.getEntityName() + "\n" + //$NON-NLS-1$ //$NON-NLS-2$
                "BEGIN\n" + //$NON-NLS-1$
                "END;"); //$NON-NLS-1$
            return newTrigger;
        });
    }
	
}
