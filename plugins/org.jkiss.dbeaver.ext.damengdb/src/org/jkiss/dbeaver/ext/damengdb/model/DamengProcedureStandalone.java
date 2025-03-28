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
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.damengdb.internal.DamengMessages;
import org.jkiss.dbeaver.ext.damengdb.model.source.DamengSourceObject;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBPRefreshableObject;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectState;
import org.jkiss.dbeaver.model.struct.rdb.DBSProcedureType;
import org.jkiss.dbeaver.utils.GeneralUtils;

import java.sql.ResultSet;
import java.util.Map;

/**
 * GenericProcedure
 */
public class DamengProcedureStandalone extends DamengProcedureBase<DamengSchema>
    implements DamengSourceObject, DBPRefreshableObject {

    protected String sourceDeclaration;
    protected int externalFunctionType;
    // Type of the object
    protected String objectTypeStr;
    private boolean valid;
    private boolean isExternalFunction;

    public DamengProcedureStandalone(DamengSchema schema, ResultSet dbResult) {
        super(schema, JDBCUtils.safeGetString(dbResult, DamengConstants.COLUMN_OBJECT_NAME),
            JDBCUtils.safeGetLong(dbResult, "OBJECT_ID"),
            DBSProcedureType.valueOf(JDBCUtils.safeGetString(dbResult, DamengConstants.COLUMN_OBJECT_TYPE)));
        this.valid = DamengConstants.RESULT_STATUS_VALID
            .equals(JDBCUtils.safeGetString(dbResult, DamengConstants.COLUMN_STATUS));
        // init object type
        initObjectType(dbResult);
    }

    public DamengProcedureStandalone(DamengSchema dmSchema, String name, DBSProcedureType procedureType) {
        super(dmSchema, name, 0l, procedureType);
        sourceDeclaration = procedureType.name() + " " + name + GeneralUtils.getDefaultLineSeparator() + "IS"
            + GeneralUtils.getDefaultLineSeparator() + "BEGIN" + GeneralUtils.getDefaultLineSeparator() + "END "
            + name + ";" + GeneralUtils.getDefaultLineSeparator();
    }

    /**
     * Determine the type
     */
    private void initObjectType(ResultSet dbResult) {
        int info1 = JDBCUtils.safeGetInt(dbResult, "INFO1");
        int info2 = JDBCUtils.safeGetInt(dbResult, "INFO2");

        // Procedure
        if ((info1 & 0x01) == 1) {
            objectTypeStr = DamengMessages.dameng_procedures_type_is_procedure;
            isExternalFunction = false;
            externalFunctionType = 0;
        } else {
            // Function
            if ((info1 & 0x80) != 1 && info2 != 'C' && info2 != 'J' && info2 != 'T' && info2 != 'H') {
                objectTypeStr = DamengMessages.dameng_procedures_type_is_function;
                isExternalFunction = false;
                externalFunctionType = 0;
            }
            // External Function
            else {
                objectTypeStr = DamengMessages.dameng_procedures_type_is_external_function;
                isExternalFunction = true;
                externalFunctionType = info2;

            }
        }

    }

    public boolean isExternalFunction() {
        return isExternalFunction;
    }

    public int getExternalFunctionType() {
        return externalFunctionType;
    }

    @Property(viewable = true, order = 4)
    public String getObjectTypeStr() {
        return objectTypeStr;
    }

    @Property(viewable = true, order = 3)
    public boolean isValid() {
        return valid;
    }

    @NotNull
    @Override
    public DamengSchema getSchema() {
        return getParentObject();
    }

    @Override
    public DamengSourceType getSourceType() {
        if (isExternalFunction) {
            return DamengSourceType.EXTERNAL_FUNCTION;
        } else {
            return getProcedureType() == DBSProcedureType.PROCEDURE ? DamengSourceType.PROCEDURE
                : DamengSourceType.FUNCTION;
        }
    }

    @Override
    public Integer getOverloadNumber() {
        return null;
    }

    @NotNull
    @Override
    public String getFullyQualifiedName(DBPEvaluationContext context) {
        return DBUtils.getFullQualifiedName(getDataSource(), getSchema(), this);
    }

    @Override
    @Property(hidden = true, editable = true, updatable = true, order = -1)
    public String getObjectDefinitionText(DBRProgressMonitor monitor, Map<String, Object> options) throws DBCException {
        if (sourceDeclaration == null && monitor != null) {
            sourceDeclaration = DamengUtils.getSource(monitor, this, false, true);
        }
        return sourceDeclaration;
    }

    public void setObjectDefinitionText(String sourceDeclaration) {
        this.sourceDeclaration = sourceDeclaration;
    }

    @Override
    public DBEPersistAction[] getCompileActions(DBRProgressMonitor monitor) {
        return new DBEPersistAction[] {new DamengObjectPersistAction(
            getProcedureType() == DBSProcedureType.PROCEDURE ? DamengObjectType.PROCEDURE
                : DamengObjectType.FUNCTION,
            "Compile procedure", "ALTER " + getSourceType().name() + " "
            + getFullyQualifiedName(DBPEvaluationContext.DDL) + " COMPILE")};
    }

    @NotNull
    @Override
    public DBSObjectState getObjectState() {
        return valid ? DBSObjectState.NORMAL : DBSObjectState.INVALID;
    }

    @Override
    public void refreshObjectState(@NotNull DBRProgressMonitor monitor) throws DBCException {
        this.valid = DamengUtils.getObjectStatus(monitor, this,
            getProcedureType() == DBSProcedureType.PROCEDURE ? DamengObjectType.PROCEDURE
                : DamengObjectType.FUNCTION);
    }

    @Override
    public DBSObject refreshObject(@NotNull DBRProgressMonitor monitor) throws DBException {
        return getSchema().proceduresCache.refreshObject(monitor, getSchema(), this);
    }
    
}
