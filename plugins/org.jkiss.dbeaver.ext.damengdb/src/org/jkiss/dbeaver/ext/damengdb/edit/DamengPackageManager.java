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
package org.jkiss.dbeaver.ext.damengdb.edit;

import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.damengdb.model.DamengSchema;
import org.jkiss.dbeaver.ext.damengdb.model.DamengUtils;
import org.jkiss.dbeaver.ext.damengdb.model.*;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBPScriptObject;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistAction;
import org.jkiss.dbeaver.model.impl.sql.edit.SQLObjectEditor;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.cache.DBSObjectCache;
import org.jkiss.utils.CommonUtils;

import java.util.List;
import java.util.Map;

/**
 * DamengPackageManager
 */
public class DamengPackageManager extends SQLObjectEditor<DamengPackage, DamengSchema> {

    @Nullable
    @Override
    public DBSObjectCache<? extends DBSObject, DamengPackage> getObjectsCache(DamengPackage object) {
        return object.getSchema().packageCache;
    }

    @Override
    protected DamengPackage createDatabaseObject(DBRProgressMonitor monitor, DBECommandContext context,
                                                 final Object container, Object copyFrom, Map<String, Object> options) {
        return new DamengPackage((DamengSchema) container, "NEW_PACKAGE");
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
        final DamengPackage object = objectDeleteCommand.getObject();
        actions.add(new SQLDatabasePersistAction("Drop package",
            "DROP PACKAGE " + object.getFullyQualifiedName(DBPEvaluationContext.DDL)) //$NON-NLS-1$
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
                                               DamengPackage pack) {
        try {
            String header = pack.getObjectDefinitionText(new VoidProgressMonitor(), DBPScriptObject.EMPTY_OPTIONS)
                .trim();
            if (!header.endsWith(";")) {
                header += ";";
            }
            if (!CommonUtils.isEmpty(header)) {
                actionList.add(new DamengObjectValidateAction(pack, DamengObjectType.PACKAGE, "Create package header",
                    header));
            }
            String body = pack.getExtendedDefinitionText(new VoidProgressMonitor());
            if (!CommonUtils.isEmpty(body)) {
                body = body.trim();
                if (!body.endsWith(";")) {
                    body += ";";
                }
                actionList.add(new DamengObjectValidateAction(pack, DamengObjectType.PACKAGE_BODY,
                    "Create package body", body));
            } else {
                actionList.add(new SQLDatabasePersistAction("Drop package header",
                    "DROP PACKAGE BODY " + pack.getFullyQualifiedName(DBPEvaluationContext.DDL),
                    DBEPersistAction.ActionType.OPTIONAL)
                );
            }
        } catch (DBException e) {
            log.warn(e);
        }
        DamengUtils.addSchemaChangeActions(executionContext, actionList, pack);
    }

}
