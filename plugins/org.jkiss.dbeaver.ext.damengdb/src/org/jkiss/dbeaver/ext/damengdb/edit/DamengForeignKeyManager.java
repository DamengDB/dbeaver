/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp and others
 * Copyright (C) 2011-2012 Eugene Fradkin (eugene.fradkin@gmail.com)
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
package org.jkiss.dbeaver.ext.damengdb.edit;

import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.ext.damengdb.model.DamengObjectStatus;
import org.jkiss.dbeaver.ext.damengdb.model.DamengTableBase;
import org.jkiss.dbeaver.ext.damengdb.model.DamengTableForeignKey;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.impl.sql.edit.struct.SQLForeignKeyManager;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.cache.DBSObjectCache;
import org.jkiss.dbeaver.model.struct.rdb.DBSForeignKeyModifyRule;

import java.util.Map;

/**
 * Dameng foreign key manager
 */
public class DamengForeignKeyManager extends SQLForeignKeyManager<DamengTableForeignKey, DamengTableBase> {

    @Nullable
    @Override
    public DBSObjectCache<? extends DBSObject, DamengTableForeignKey> getObjectsCache(DamengTableForeignKey object) {
        return object.getParentObject().getSchema().foreignKeyCache;
    }

    @Override
    protected DamengTableForeignKey createDatabaseObject(DBRProgressMonitor monitor, DBECommandContext context,
                                                         final Object container, Object from, Map<String, Object> options) {
        DamengTableBase table = (DamengTableBase) container;

        return new DamengTableForeignKey(table, "", DamengObjectStatus.ENABLED, null,
            DBSForeignKeyModifyRule.NO_ACTION);
    }

}
