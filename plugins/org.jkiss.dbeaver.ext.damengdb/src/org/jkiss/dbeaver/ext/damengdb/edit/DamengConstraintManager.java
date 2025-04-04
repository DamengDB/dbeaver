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
import org.jkiss.dbeaver.ext.damengdb.model.DamengTableConstraint;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.impl.edit.DBECommandAbstract;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistAction;
import org.jkiss.dbeaver.model.impl.sql.edit.struct.SQLConstraintManager;
import org.jkiss.dbeaver.model.messages.ModelMessages;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSEntityConstraintType;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.cache.DBSObjectCache;

import java.util.List;
import java.util.Map;

/**
 * Dameng constraint manager
 */
public class DamengConstraintManager extends SQLConstraintManager<DamengTableConstraint, DamengTableBase> {

    @Nullable
    @Override
    public DBSObjectCache<? extends DBSObject, DamengTableConstraint> getObjectsCache(DamengTableConstraint object) {
        return object.getParentObject().getSchema().constraintCache;
    }

    @Override
    protected DamengTableConstraint createDatabaseObject(DBRProgressMonitor monitor, DBECommandContext context,
                                                         final Object container, Object from, Map<String, Object> options) {
        DamengTableBase table = (DamengTableBase) container;

        return new DamengTableConstraint(table, "", DBSEntityConstraintType.UNIQUE_KEY, null,
            DamengObjectStatus.ENABLED);
    }

    @Override
    protected String getDropConstraintPattern(DamengTableConstraint constraint) {
        String clause = "CONSTRAINT";

        String tableType = constraint.getTable().isView() ? "VIEW" : "TABLE";

        return "ALTER " + tableType + " " + PATTERN_ITEM_TABLE + " DROP " + clause + " " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            + PATTERN_ITEM_CONSTRAINT;
    }

    @Override
    protected void addObjectCreateActions(DBRProgressMonitor monitor, DBCExecutionContext executionContext,
                                          List<DBEPersistAction> actions, ObjectCreateCommand command, Map<String, Object> options) {
        DamengTableConstraint constraint = command.getObject();
        boolean isView = constraint.getTable().isView();
        String tableType = isView ? "VIEW" : "TABLE";
        DamengTableBase table = constraint.getTable();
        actions.add(new SQLDatabasePersistAction(ModelMessages.model_jdbc_create_new_constraint,
            "ALTER " + tableType + " " + table.getFullyQualifiedName(DBPEvaluationContext.DDL) + "\nADD "
                + getNestedDeclaration(monitor, table, command, options) + "\n"
                + (!isView && constraint.getStatus() == DamengObjectStatus.ENABLED ? "ENABLE" : "DISABLE")
                + (isView ? " NOVALIDATE" : "")));
    }

    @Override
    protected void appendConstraintDefinition(StringBuilder decl, DBECommandAbstract<DamengTableConstraint> command) {
        if (command.getObject().getConstraintType() == DBSEntityConstraintType.CHECK) {
            decl.append(" (").append((command.getObject()).getSearchCondition()).append(")");
        } else {
            super.appendConstraintDefinition(decl, command);
        }
    }
    
}
