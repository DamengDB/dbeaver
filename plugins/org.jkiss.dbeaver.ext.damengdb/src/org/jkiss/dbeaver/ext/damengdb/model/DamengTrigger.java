/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp and others
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
import java.util.Date;
import java.util.Map;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.damengdb.model.source.DamengSourceObject;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBPNamedObject;
import org.jkiss.dbeaver.model.DBPQualifiedObject;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectState;
import org.jkiss.dbeaver.model.struct.rdb.DBSTrigger;
import org.jkiss.utils.CommonUtils;

/**
 * DamengTrigger
 */
public abstract class DamengTrigger<PARENT extends DBSObject> extends DamengObject<PARENT>
    implements DBSTrigger, DBPQualifiedObject, DamengSourceObject {
    private String schemaName;
    private BaseObjectType objectType;
    private String triggerType;
    private String triggeringEvent;
    private String columnName;
    private DamengObjectStatus status;
    private String sourceDeclaration;
    private Date created;

    public DamengTrigger(PARENT parent, String name) {
        super(parent, name, false);
    }

    public DamengTrigger(PARENT parent, ResultSet dbResult) {
        super(parent, JDBCUtils.safeGetString(dbResult, "TRIGGER_NAME"), true);
        this.schemaName = JDBCUtils.safeGetString(dbResult, "OWNER");
        this.objectType = CommonUtils.valueOf(BaseObjectType.class,
            JDBCUtils.safeGetStringTrimmed(dbResult, "BASE_OBJECT_TYPE"));
        this.triggerType = JDBCUtils.safeGetString(dbResult, "TRIGGERING_TYPE");
        this.triggeringEvent = JDBCUtils.safeGetString(dbResult, "TRIGGERING_EVENT");
        this.columnName = JDBCUtils.safeGetString(dbResult, "COLUMN_NAME");
        this.status = CommonUtils.valueOf(DamengObjectStatus.class,
            JDBCUtils.safeGetStringTrimmed(dbResult, DamengConstants.COLUMN_STATUS).equals("Y") ? "ENABLED"
                : "DISABLED");
        this.created = JDBCUtils.safeGetTimestamp(dbResult, DamengConstants.CREATED);
    }

    @NotNull
    @Override
    @Property(viewable = true, editable = true, order = 1)
    public String getName() {
        return super.getName();
    }

    @Property(viewable = true, order = 2)
    public String getSchemaName() {
        return schemaName;
    }

    @Property(viewable = true, order = 5)
    public BaseObjectType getObjectType() {
        return objectType;
    }

    @Property(viewable = true, order = 5)
    public String getTriggerType() {
        return triggerType;
    }

    @Property(viewable = true, order = 6)
    public String getTriggeringEvent() {
        return triggeringEvent;
    }

    @Property(viewable = true, order = 7)
    public String getColumnName() {
        return columnName;
    }

    @Property(viewable = true, order = 10)
    public DamengObjectStatus getStatus() {
        return status;
    }

    @Property(viewable = true, order = 12)
    public Date getCreated() {
        return created;
    }

    @Override
    public DamengSourceType getSourceType() {
        return DamengSourceType.TRIGGER;
    }

    @Override
    @Property(hidden = true, editable = true, updatable = true, order = -1)
    public String getObjectDefinitionText(DBRProgressMonitor monitor, Map<String, Object> options) throws DBException {
        if (sourceDeclaration == null && monitor != null) {
            sourceDeclaration = DamengUtils.getSource(monitor, this, false, true);
        }
        return sourceDeclaration;
    }

    public void setObjectDefinitionText(String source) {
        this.sourceDeclaration = source;
    }

    @NotNull
    @Override
    public DBSObjectState getObjectState() {
        return status == DamengObjectStatus.ENABLED ? DBSObjectState.NORMAL : DBSObjectState.INVALID;
    }

    @Override
    public void refreshObjectState(@NotNull DBRProgressMonitor monitor) throws DBCException {
        this.status = (DamengUtils.getObjectStatus(monitor, this, DamengObjectType.TRIGGER) ? DamengObjectStatus.ENABLED
            : DamengObjectStatus.ERROR);
    }

    @Override
    public DBEPersistAction[] getCompileActions(DBRProgressMonitor monitor) {
        return new DBEPersistAction[] {new DamengObjectPersistAction(DamengObjectType.TRIGGER, "Compile trigger",
            "ALTER TRIGGER " + getFullyQualifiedName(DBPEvaluationContext.DDL) + " COMPILE")};
    }

    @Override
    public String getFullyQualifiedName(DBPEvaluationContext context) {
        return DBUtils.getFullQualifiedName(getDataSource(), getSchema(), this);
    }

    @Override
    public String toString() {
        return getFullyQualifiedName(DBPEvaluationContext.DDL);
    }

    public enum BaseObjectType {
        TABLE, VIEW, SCHEMA, DATABASE
    }

    public enum ActionType implements DBPNamedObject {
        PLSQL("PL/SQL"), CALL("CALL");

        private final String title;

        ActionType(String title) {
            this.title = title;
        }

        @NotNull
        @Override
        public String getName() {
            return title;
        }
    }
}
