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
import org.jkiss.dbeaver.ext.damengdb.model.DamengTableIndex;
import org.jkiss.dbeaver.ext.damengdb.model.DamengTablePhysical;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.impl.sql.edit.struct.SQLIndexManager;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.cache.DBSObjectCache;
import org.jkiss.dbeaver.model.struct.rdb.DBSIndexType;

import java.util.Map;

/**
 * Dameng index manager
 */
public class DamengIndexManager extends SQLIndexManager<DamengTableIndex, DamengTablePhysical> {

    @Nullable
    @Override
    public DBSObjectCache<? extends DBSObject, DamengTableIndex> getObjectsCache(DamengTableIndex object) {
        return object.getParentObject().getSchema().indexCache;
    }

    @Override
    protected void appendIndexModifiers(DamengTableIndex index, StringBuilder decl) {
        if (index.isSpatial()) {
            decl.append(" SPATIAL");
        } else if (index.isBitMap()) {
            decl.append(" BITMAP");
        } else {
            if (index.isClustered()) {
                decl.append(" CLUSTER");
            }

            if (index.isUnique()) {
                decl.append(" UNIQUE");
            }
        }
    }

    @Override
    protected DamengTableIndex createDatabaseObject(DBRProgressMonitor monitor, DBECommandContext context,
                                                    final Object container, Object from, Map<String, Object> options) {
        DamengTablePhysical table = (DamengTablePhysical) container;
        return new DamengTableIndex(table.getSchema(), table, "INDEX", true, DBSIndexType.UNKNOWN);
    }

    @Override
    protected String getDropIndexPattern(DamengTableIndex index) {
        return "DROP INDEX " + PATTERN_ITEM_INDEX;
    }
    
}
