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
import org.jkiss.dbeaver.ext.damengdb.model.DamengSchema;
import org.jkiss.dbeaver.ext.damengdb.model.DamengDataType;
import org.jkiss.dbeaver.ext.damengdb.model.DamengUtils;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistAction;
import org.jkiss.dbeaver.model.impl.sql.edit.SQLObjectEditor;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.cache.DBSObjectCache;
import org.jkiss.utils.CommonUtils;

import java.util.List;
import java.util.Map;

/**
 * DamengDataTypeManager
 */
public class DamengDataTypeManager extends SQLObjectEditor<DamengDataType, DamengSchema> {

    @Nullable
    @Override
    public DBSObjectCache<? extends DBSObject, DamengDataType> getObjectsCache(DamengDataType object) {
        return object.getSchema().dataTypeCache;
    }

    @Override
    public boolean canCreateObject(Object container) {
        return container instanceof DamengSchema;
    }

    @Override
    protected DamengDataType createDatabaseObject(DBRProgressMonitor monitor, DBECommandContext context,
                                                  final Object container, Object copyFrom, Map<String, Object> options) {
        DamengSchema schema = (DamengSchema) container;
        DamengDataType dataType = new DamengDataType(schema, "DataType", false);
        dataType.setObjectDefinitionText("TYPE " + dataType.getName() + " AS OBJECT\n" + //$NON-NLS-1$ //$NON-NLS-2$
            "(\n" +
            ")");
        return dataType;
    }

    @Override
    protected void addObjectCreateActions(DBRProgressMonitor monitor, DBCExecutionContext executionContext,
                                          List<DBEPersistAction> actions, ObjectCreateCommand objectCreateCommand,
                                          Map<String, Object> options) {
        createOrReplaceProcedureQuery(executionContext, actions, objectCreateCommand.getObject());
    }

    @Override
    protected void addObjectDeleteActions(DBRProgressMonitor monitor, DBCExecutionContext executionContext,
                                          List<DBEPersistAction> actions, ObjectDeleteCommand objectDeleteCommand,
                                          Map<String, Object> options) {
        final DamengDataType object = objectDeleteCommand.getObject();
        actions.add(new SQLDatabasePersistAction("Drop type",
            "DROP TYPE " + object.getFullyQualifiedName(DBPEvaluationContext.DDL)) //$NON-NLS-1$
        );
    }

    @Override
    protected void addObjectModifyActions(DBRProgressMonitor monitor, DBCExecutionContext executionContext,
                                          List<DBEPersistAction> actionList, ObjectChangeCommand objectChangeCommand,
                                          Map<String, Object> options) {
        createOrReplaceProcedureQuery(executionContext, actionList, objectChangeCommand.getObject());
    }

    @Override
    public long getMakerOptions(DBPDataSource dataSource) {
        return FEATURE_EDITOR_ON_CREATE;
    }

    private void createOrReplaceProcedureQuery(DBCExecutionContext executionContext, List<DBEPersistAction> actionList,
                                               DamengDataType dataType) {
        String header = DamengUtils.normalizeSourceName(dataType, false);
        if (!CommonUtils.isEmpty(header)) {
            actionList.add(new SQLDatabasePersistAction("Create type header", header)); // $NON-NLS-2$
        }
        String body = DamengUtils.normalizeSourceName(dataType, true);
        if (!CommonUtils.isEmpty(body)) {
            actionList.add(new SQLDatabasePersistAction("Create type body", body)); // $NON-NLS-2$
        }
        DamengUtils.addSchemaChangeActions(executionContext, actionList, dataType);
    }

}
