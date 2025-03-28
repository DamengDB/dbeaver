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
import org.jkiss.dbeaver.ext.damengdb.model.DamengTableColumn;
import org.jkiss.dbeaver.ext.damengdb.model.*;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBPScriptObject;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.edit.prop.DBECommandComposite;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistAction;
import org.jkiss.dbeaver.model.impl.sql.edit.struct.SQLTableManager;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.sql.SQLScriptElement;
import org.jkiss.dbeaver.model.sql.parser.SQLScriptParser;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.cache.DBSObjectCache;
import org.jkiss.utils.CommonUtils;

import java.util.List;
import java.util.Map;

/**
 * DamengViewManager
 */
public class DamengViewManager extends SQLTableManager<DamengView, DamengSchema> {

    private static final Class<? extends DBSObject>[] CHILD_TYPES = CommonUtils.array(DamengTableConstraint.class,
        DamengTableForeignKey.class);

    @NotNull
    @Override
    public Class<? extends DBSObject>[] getChildTypes() {
        return CHILD_TYPES;
    }

    @Override
    public long getMakerOptions(DBPDataSource dataSource) {
        return FEATURE_EDITOR_ON_CREATE;
    }

    @Override
    protected void validateObjectProperties(DBRProgressMonitor monitor, ObjectChangeCommand command,
                                            Map<String, Object> options) throws DBException {
        if (CommonUtils.isEmpty(command.getObject().getName())) {
            throw new DBException("View name cannot be empty");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Nullable
    @Override
    public DBSObjectCache<? extends DBSObject, DamengView> getObjectsCache(DamengView object) {
        return (DBSObjectCache) object.getSchema().tableCache;
    }

    @Override
    protected String getBaseObjectName() {
        return SQLTableManager.BASE_VIEW_NAME;
    }

    @Override
    protected DamengView createDatabaseObject(DBRProgressMonitor monitor, DBECommandContext context, Object container,
                                              Object copyFrom, Map<String, Object> options) {
        DamengSchema schema = (DamengSchema) container;
        DamengView newView = new DamengView(schema, "NEW_VIEW");
        setNewObjectName(monitor, schema, newView);
        newView.setViewText("CREATE OR REPLACE VIEW " + newView.getFullyQualifiedName(DBPEvaluationContext.DDL)
            + " AS\nSELECT 1 AS A FROM DUAL");
        return newView;
    }

    @Override
    protected void addStructObjectCreateActions(DBRProgressMonitor monitor, DBCExecutionContext executionContext,
                                                List<DBEPersistAction> actions, StructCreateCommand command, Map<String, Object> options)
        throws DBException {
        createOrReplaceViewQuery(monitor, actions, command, options);
    }

    @Override
    protected void addObjectModifyActions(DBRProgressMonitor monitor, DBCExecutionContext executionContext,
                                          List<DBEPersistAction> actionList, ObjectChangeCommand command, Map<String, Object> options)
        throws DBException {
        createOrReplaceViewQuery(monitor, actionList, command, options);
    }

    @Override
    protected void addObjectDeleteActions(DBRProgressMonitor monitor, DBCExecutionContext executionContext,
                                          List<DBEPersistAction> actions, ObjectDeleteCommand command, Map<String, Object> options) {
        actions.add(new SQLDatabasePersistAction("Drop view",
            "DROP VIEW " + command.getObject().getFullyQualifiedName(DBPEvaluationContext.DDL)) //$NON-NLS-1$
        );
    }

    private void createOrReplaceViewQuery(DBRProgressMonitor monitor, @NotNull List<DBEPersistAction> actions,
                                          DBECommandComposite<DamengView, PropertyHandler> command, Map<String, Object> options)
        throws DBException {
        final DamengView view = command.getObject();
        boolean hasComment = command.hasProperty("comment");
        if (!hasComment || command.getProperties().size() > 1) {
            String viewText = view.getViewText().trim();
            List<SQLScriptElement> sqlScriptElements = SQLScriptParser.parseScript(view.getDataSource(), viewText);
            if (sqlScriptElements.size() > 1) {
                // In this case we already have and view definition, and
                // view/columns comments
                for (SQLScriptElement scriptElement : sqlScriptElements) {
                    actions.add(new SQLDatabasePersistAction("Create view part", scriptElement.getText()));
                }
                return;
            }
            while (viewText.endsWith(";")) {
                viewText = viewText.substring(0, viewText.length() - 1);
            }
            actions.add(new SQLDatabasePersistAction("Create view", viewText));
        }

        if (hasComment || (CommonUtils.getOption(options, DBPScriptObject.OPTION_OBJECT_SAVE)
            && CommonUtils.isNotEmpty(view.getComment()))) {
            actions.add(new SQLDatabasePersistAction("Comment table",
                "COMMENT ON TABLE " + view.getFullyQualifiedName(DBPEvaluationContext.DDL) + " IS '"
                    + CommonUtils.notEmpty(view.getComment()) + "'"));
        }

        if (!(hasComment && command.getProperties().size() == 1)
            && CommonUtils.getOption(options, DBPScriptObject.OPTION_OBJECT_SAVE)) {
            for (DamengTableColumn column : CommonUtils.safeCollection(view.getAttributes(monitor))) {
                if (!CommonUtils.isEmpty(column.getComment(monitor))) {
                    DamengTableColumnManager.addColumnCommentAction(actions, column, view);
                }
            }
        }
    }

}
