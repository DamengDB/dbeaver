/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp and others
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
package org.jkiss.dbeaver.ext.damengdb.model;

import org.jkiss.dbeaver.ext.damengdb.model.source.DamengSourceObject;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCSession;

/**
 * Dameng persist action with validation
 */
public class DamengObjectValidateAction extends DamengObjectPersistAction {
	
    @SuppressWarnings("unused")
    private final DamengSourceObject object;

    public DamengObjectValidateAction(DamengSourceObject object, DamengObjectType objectType, String title,
                                      String script) {
        super(objectType, title, script);
        this.object = object;
    }

    @Override
    public void afterExecute(DBCSession session, Throwable error) throws DBCException {
        if (error != null) {
            return;
        }
    }
    
}
