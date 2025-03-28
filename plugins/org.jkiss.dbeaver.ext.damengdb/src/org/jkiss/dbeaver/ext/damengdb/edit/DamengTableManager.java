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

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.damengdb.model.DamengSchema;
import org.jkiss.dbeaver.ext.damengdb.model.DamengTable;
import org.jkiss.dbeaver.ext.damengdb.model.DamengTableColumn;
import org.jkiss.dbeaver.ext.damengdb.model.DamengTablespace;
import org.jkiss.dbeaver.ext.damengdb.model.*;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEObjectRenamer;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistAction;
import org.jkiss.dbeaver.model.impl.sql.edit.struct.SQLTableManager;
import org.jkiss.dbeaver.model.messages.ModelMessages;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.sql.SQLUtils;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.cache.DBSObjectCache;
import org.jkiss.utils.CommonUtils;

import java.util.List;
import java.util.Map;

/**
 * Dameng table manager
 */
public class DamengTableManager extends SQLTableManager<DamengTable, DamengSchema>
    implements DBEObjectRenamer<DamengTable> {

    private static final Class<? extends DBSObject>[] CHILD_TYPES = CommonUtils.array(DamengTableColumn.class,
        DamengTableConstraint.class, DamengTableForeignKey.class, DamengTableIndex.class);

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Nullable
    @Override
    public DBSObjectCache<? extends DBSObject, DamengTable> getObjectsCache(DamengTable object) {
        return (DBSObjectCache) object.getSchema().tableCache;
    }

    @Override
    protected DamengTable createDatabaseObject(DBRProgressMonitor monitor, DBECommandContext context, Object container,
                                               Object copyFrom, Map<String, Object> options) {
        DamengSchema schema = (DamengSchema) container;

        DamengTable table = new DamengTable(schema, "");
        setNewObjectName(monitor, schema, table);
        return table;
    }

    @Override
    protected void addObjectModifyActions(DBRProgressMonitor monitor, DBCExecutionContext executionContext,
                                          List<DBEPersistAction> actionList, ObjectChangeCommand command, Map<String, Object> options) {
        if (command.getProperties().size() > 1 || command.getProperty("comment") == null) //$NON-NLS-1$
        {
            StringBuilder query = new StringBuilder("ALTER TABLE ");
            query.append(command.getObject().getFullyQualifiedName(DBPEvaluationContext.DDL)).append(" "); //$NON-NLS-1$
            appendTableModifiers(monitor, command.getObject(), command, query, true);
            actionList.add(new SQLDatabasePersistAction(query.toString()));
        }
    }

    @Override
    protected void addObjectExtraActions(DBRProgressMonitor monitor, DBCExecutionContext executionContext,
                                         List<DBEPersistAction> actions, NestedObjectCommand<DamengTable, PropertyHandler> command,
                                         Map<String, Object> options) throws DBException {
        DamengTable table = command.getObject();
        if (command.getProperty("comment") != null)
        {
            actions.add(new SQLDatabasePersistAction("Comment table",
                "COMMENT ON TABLE " + table.getFullyQualifiedName(DBPEvaluationContext.DDL) + " IS "
                    + SQLUtils.quoteString(table, table.getComment())));
        }

        if (!table.isPersisted()) {
            // Column comments for the newly created table
            for (DamengTableColumn column : CommonUtils.safeCollection(table.getAttributes(monitor))) {
                if (!CommonUtils.isEmpty(column.getDescription())) {
                    DamengTableColumnManager.addColumnCommentAction(actions, column, column.getTable());
                }
            }
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected void appendTableModifiers(DBRProgressMonitor monitor, DamengTable table, NestedObjectCommand tableProps,
                                        StringBuilder ddl, boolean alter) {
        // ALTER
        if (tableProps.getProperty("tablespace") != null)
        {
            Object tablespace = table.getTablespace();
            if (tablespace instanceof DamengTablespace) {
                if (table.isPersisted()) {
                    ddl.append("\nMOVE TABLESPACE ").append(((DamengTablespace) tablespace).getName());
                } else {
                    ddl.append("\nTABLESPACE ").append(((DamengTablespace) tablespace).getName());
                }
            }
        }
    }

    @Override
    protected void addObjectRenameActions(DBRProgressMonitor monitor, DBCExecutionContext executionContext,
                                          List<DBEPersistAction> actions, ObjectRenameCommand command, Map<String, Object> options) {
        actions.add(new SQLDatabasePersistAction("Rename table", "ALTER TABLE "
            + DBUtils.getQuotedIdentifier(command.getObject().getSchema()) + "."
            + DBUtils.getQuotedIdentifier(command.getObject().getDataSource(), command.getOldName()) + " RENAME TO "
            + DBUtils.getQuotedIdentifier(command.getObject().getDataSource(), command.getNewName())));
    }

    @Override
    protected void addObjectDeleteActions(DBRProgressMonitor monitor, DBCExecutionContext executionContext,
                                          List<DBEPersistAction> actions, ObjectDeleteCommand command, Map<String, Object> options) {
        DamengTable object = command.getObject();
        actions.add(new SQLDatabasePersistAction(ModelMessages.model_jdbc_drop_table,
            "DROP " + (object.isView() ? "VIEW" : "TABLE") + " "
                + object.getFullyQualifiedName(DBPEvaluationContext.DDL)
                + (!object.isView() && CommonUtils.getOption(options, OPTION_DELETE_CASCADE)
                ? " CASCADE CONSTRAINTS"
                : "")));
    }

    @NotNull
    @Override
    public Class<? extends DBSObject>[] getChildTypes() {
        return CHILD_TYPES;
    }

    @Override
    public void renameObject(@NotNull DBECommandContext commandContext, @NotNull DamengTable object,
                             @NotNull Map<String, Object> options, @NotNull String newName) throws DBException {
        processObjectRename(commandContext, object, options, newName);
    }

}
