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

import java.util.ArrayList;
import java.util.List;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.model.impl.DBObjectNameCaseTransformer;
import org.jkiss.dbeaver.model.impl.jdbc.struct.JDBCTableConstraint;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSEntityConstraintType;

/**
 * DamengTableConstraint
 */
public abstract class DamengTableConstraintBase
    extends JDBCTableConstraint<DamengTableBase, DamengTableConstraintColumn> {

    private DamengObjectStatus status;

    private List<DamengTableConstraintColumn> columns;

    public DamengTableConstraintBase(DamengTableBase dmTable, String name, DBSEntityConstraintType constraintType,
                                     DamengObjectStatus status, boolean persisted) {
        super(dmTable, name, null, constraintType, persisted);
        this.status = status;
    }

    protected DamengTableConstraintBase(DamengTableBase dmTableBase, String name, String description,
                                        DBSEntityConstraintType constraintType, boolean persisted) {
        super(dmTableBase, name, description, constraintType, persisted);
    }

    @NotNull
    @Override
    public DamengDataSource getDataSource() {
        return getTable().getDataSource();
    }

    @NotNull
    @Property(viewable = true, editable = false, valueTransformer = DBObjectNameCaseTransformer.class, order = 3)
    @Override
    public DBSEntityConstraintType getConstraintType() {
        return constraintType;
    }

    @Property(viewable = true, editable = false, order = 9)
    public DamengObjectStatus getStatus() {
        return status;
    }

    @Override
    public List<DamengTableConstraintColumn> getAttributeReferences(DBRProgressMonitor monitor) {
        return columns;
    }

    public void addColumn(DamengTableConstraintColumn column) {
        if (columns == null) {
            columns = new ArrayList<>();
        }
        this.columns.add(column);
    }

    void setColumns(List<DamengTableConstraintColumn> columns) {
        this.columns = columns;
    }

    public void setAttributeReferences(List<DamengTableConstraintColumn> columns) {
        this.columns.clear();
        this.columns.addAll(columns);
    }

}
