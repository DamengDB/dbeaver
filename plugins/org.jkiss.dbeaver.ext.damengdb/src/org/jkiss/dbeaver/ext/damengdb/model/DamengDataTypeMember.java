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

import java.sql.ResultSet;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.impl.DBObjectNameCaseTransformer;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSEntityElement;

/**
 * Dameng data type member
 */
public abstract class DamengDataTypeMember implements DBSEntityElement {
	
    protected String name;
    protected int number;
    private DamengDataType ownerType;
    private DamengPackage packageMember;
    private boolean inherited;

    private boolean persisted;

    protected DamengDataTypeMember(DamengDataType ownerType) {
        this.ownerType = ownerType;
        this.persisted = true;
    }

    protected DamengDataTypeMember(DamengPackage packageMember) {
        this.packageMember = packageMember;
        this.persisted = true;
    }

    protected DamengDataTypeMember(DamengDataType ownerType, ResultSet dbResult) {
        this.ownerType = ownerType;
        this.inherited = JDBCUtils.safeGetBoolean(dbResult, "INHERITED", DamengConstants.YES);
        this.persisted = true;
    }

    @NotNull
    public DamengDataType getOwnerType() {
        return ownerType;
    }

    @Nullable
    @Override
    public String getDescription() {
        return null;
    }

    @NotNull
    @Override
    public DamengDataType getParentObject() {
        return ownerType;
    }

    public DamengPackage getParentPackage() {
        return packageMember;
    }

    @NotNull
    @Override
    public DamengDataSource getDataSource() {
        if (packageMember != null) {
            return packageMember.getDataSource();
        }
        return ownerType.getDataSource();
    }

    @Override
    public boolean isPersisted() {
        return persisted;
    }

    @NotNull
    @Override
    @Property(viewable = true, editable = true, valueTransformer = DBObjectNameCaseTransformer.class, order = 1)
    public String getName() {
        return name;
    }

    public int getNumber() {
        return number;
    }

    public boolean isInherited() {
        return inherited;
    }
    
}
