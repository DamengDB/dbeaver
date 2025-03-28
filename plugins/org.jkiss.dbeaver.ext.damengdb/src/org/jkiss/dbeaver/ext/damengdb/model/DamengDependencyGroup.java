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
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.damengdb.internal.DamengMessages;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class DamengDependencyGroup implements DBSObject {
	
    private final DBSObject owner;

    private final boolean dependents;

    public DamengDependencyGroup(DBSObject owner, boolean dependents) {
        this.owner = owner;
        this.dependents = dependents;
    }

    @NotNull
    public static Collection<DamengDependencyGroup> of(@NotNull DBSObject owner) {
        return Collections.unmodifiableCollection(
            Arrays.asList(new DamengDependencyGroup(owner, false), new DamengDependencyGroup(owner, true)));
    }

    @Association
    public Collection<DamengDependency> getEntries(DBRProgressMonitor monitor) throws DBException {
        return DamengDependency.readDependencies(monitor, owner, dependents);
    }

    @NotNull
    @Override
    @Property(viewable = true, order = 1)
    public String getName() {
        return dependents ? DamengMessages.edit_dameng_dependencies_dependent_name
            : DamengMessages.edit_dameng_dependencies_dependency_name;
    }

    @Nullable
    @Override
    public String getDescription() {
        return dependents ? DamengMessages.edit_dameng_dependencies_dependent_description
            : DamengMessages.edit_dameng_dependencies_dependency_description;
    }

    @Override
    public boolean isPersisted() {
        return owner.isPersisted();
    }

    @Nullable
    @Override
    public DBSObject getParentObject() {
        return owner;
    }

    @NotNull
    @Override
    public DBPDataSource getDataSource() {
        return owner.getDataSource();
    }
    
}
