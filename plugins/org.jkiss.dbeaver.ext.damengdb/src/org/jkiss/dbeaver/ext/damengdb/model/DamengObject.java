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
package org.jkiss.dbeaver.ext.damengdb.model;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.DBPSaveableObject;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.utils.CommonUtils;

/**
 * Abstract dameng object
 */
public abstract class DamengObject<PARENT extends DBSObject> implements DBSObject, DBPSaveableObject {
    
	protected final PARENT parent;

    protected String name;

    private boolean persisted;

    private long objectId;

    protected DamengObject(PARENT parent, String name, long objectId, boolean persisted) {
        this.parent = parent;
        this.name = CommonUtils.notEmpty(name);
        this.objectId = objectId;
        this.persisted = persisted;
    }

    protected DamengObject(PARENT parent, String name, boolean persisted) {
        this.parent = parent;
        this.name = name;
        this.persisted = persisted;
    }

    @Nullable
    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public PARENT getParentObject() {
        return parent;
    }

    @NotNull
    @Override
    public DamengDataSource getDataSource() {
        return (DamengDataSource) parent.getDataSource();
    }

    @NotNull
    @Override
    @Property(viewable = true, editable = true, order = 1)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getObjectId() {
        return objectId;
    }

    @Override
    public boolean isPersisted() {
        return persisted;
    }

    @Override
    public void setPersisted(boolean persisted) {
        this.persisted = persisted;
    }
    
}
