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
import org.jkiss.dbeaver.ext.damengdb.model.DamengUtils;
import org.jkiss.dbeaver.ext.damengdb.model.*;
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
import org.jkiss.dbeaver.model.struct.rdb.DBSProcedureType;

import java.util.List;
import java.util.Map;

/**
 * DamengProcedureManager
 */
public class DamengProcedureManager extends SQLObjectEditor<DamengProcedureStandalone, DamengSchema> {

    @Nullable
    @Override
    public DBSObjectCache<? extends DBSObject, DamengProcedureStandalone> getObjectsCache(
        DamengProcedureStandalone object) {
        return object.getSchema().proceduresCache;
    }

    @Override
    protected DamengProcedureStandalone createDatabaseObject(DBRProgressMonitor monitor, DBECommandContext context,
                                                             final Object container, Object copyFrom, Map<String, Object> options) {
        return new DamengProcedureStandalone((DamengSchema) container, "NEW_PROCEDURE", DBSProcedureType.PROCEDURE);
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
        final DamengProcedureStandalone object = objectDeleteCommand.getObject();
        actions.add(new SQLDatabasePersistAction("Drop procedure", "DROP " + object.getProcedureType().name() //$NON-NLS-2$
            + " " + object.getFullyQualifiedName(DBPEvaluationContext.DDL))
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
                                               DamengProcedureStandalone procedure) {
        String source = DamengUtils.normalizeSourceName(procedure, false);
        if (source == null) {
            return;
        }
        actionList
            .add(new DamengObjectValidateAction(procedure, DamengObjectType.PROCEDURE, "Create procedure", source)); // $NON-NLS-2$
        DamengUtils.addSchemaChangeActions(executionContext, actionList, procedure);
    }

}
