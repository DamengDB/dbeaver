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
import java.sql.SQLException;
import java.util.Map;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.damengdb.model.source.DamengSourceObject;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.DBObjectNameCaseTransformer;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSAlias;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectState;

/**
 * Dameng synonym
 */
public class DamengSynonym extends DamengSchemaObject implements DBSAlias, DamengSourceObject {
    
	private String objectOwner;

    private String objectTypeName;

    private String objectName;

    private String dbLink;

    private String SynonymDeclaration;

    private boolean isPublic;

    public DamengSynonym(DamengSchema schema, ResultSet dbResult) {
        super(schema, JDBCUtils.safeGetString(dbResult, "SYNONYM_NAME"), true);
        this.objectTypeName = JDBCUtils.safeGetString(dbResult, DamengConstants.COLUMN_OBJECT_TYPE);
        this.objectOwner = JDBCUtils.safeGetString(dbResult, "TABLE_OWNER");
        this.objectName = JDBCUtils.safeGetString(dbResult, DamengConstants.COL_TABLE_NAME);
        this.dbLink = JDBCUtils.safeGetString(dbResult, "DB_LINK");
        this.isPublic = JDBCUtils.safeGetString(dbResult, "OWNER").equals(DamengConstants.USER_PUBLIC);
    }

    public DamengObjectType getObjectType() {
        return DamengObjectType.getByType(objectTypeName);
    }

    @NotNull
    @Override
    @Property(viewable = true, editable = true, valueTransformer = DBObjectNameCaseTransformer.class, order = 1)
    public String getName() {
        return super.getName();
    }

    @Property(viewable = true, order = 2)
    public String getObjectTypeName() {
        return objectTypeName;
    }

    @Property(viewable = true, order = 3)
    public Object getObjectOwner() {
        final DamengSchema schema = getDataSource().schemaCache.getCachedObject(objectOwner);
        return schema == null ? objectOwner : schema;
    }

    @Property(viewable = true, linkPossible = true, order = 4)
    public Object getObject(DBRProgressMonitor monitor) throws DBException {
        if (objectTypeName == null) {
            return null;
        }
        return DamengObjectType.resolveObject(monitor, getDataSource(), dbLink, objectTypeName, objectOwner,
            objectName);
    }

    @Property(viewable = true, linkPossible = true, order = 5)
    public Object getDbLink(DBRProgressMonitor monitor) throws DBException {
        return DamengDBLink.resolveObject(monitor, getSchema(), dbLink);
    }

    @Override
    public DBSObject getTargetObject(DBRProgressMonitor monitor) throws DBException {
        Object object = getObject(monitor);
        if (object instanceof DBSObject) {
            return (DBSObject) object;
        }
        return null;
    }

    @Override
    public String getFullyQualifiedName(DBPEvaluationContext context) {
        if (DamengConstants.USER_PUBLIC.equals(getSchema().getName())) {
            return DBUtils.getQuotedIdentifier(this);
        }
        return super.getFullyQualifiedName(context);
    }

    @Override
    public void setObjectDefinitionText(String source) {
    }

    @Override
    @Property(hidden = true, editable = false, updatable = false, order = -1)
    public String getObjectDefinitionText(DBRProgressMonitor monitor, Map<String, Object> options) throws DBException {
        if (SynonymDeclaration == null) {
            StringBuffer definationText = new StringBuffer();
            definationText.append("--");
            if (this.objectOwner != null) {
                definationText.append("\"" + this.objectOwner + "\"");
                definationText.append(".");
            }
            definationText.append("\"" + getName() + "\"");
            definationText.append(DamengConstants.LINE_SEPARATOR + DamengConstants.LINE_SEPARATOR);

            try (JDBCSession session = DBUtils.openUtilSession(monitor, this, "Get DDL of '" + getName() + "'")) {
                /**
                 * According to the manual, SELECT SYNONYMDEF(schema, objectName,type,preflag);
                 * type：Type of the synonym; 0,public 1,user;preflag：Number of object prefix,1
                 * means export schema name;0 means only export object name. eg：SELECT
                 * SYNONYMDEF('SYSDBA', 'SYSOBJECTS',0,1);
                 */
                JDBCPreparedStatement dbStat = session.prepareStatement("SELECT SYNONYMDEF('" + this.objectOwner + "','"
                    + this.getName() + "'," + (isPublic ? 0 : 1) + ",1 )");

                JDBCResultSet dbResult = dbStat.executeQuery();
                while (dbResult.next()) {
                    definationText.append(dbResult.getString(1));

                }
                dbStat.close();
            } catch (SQLException e) {
                // do nothing
            }
            SynonymDeclaration = definationText.toString();
        }
        return SynonymDeclaration;
    }

    @Override
    public @NotNull DBSObjectState getObjectState() {
        return DBSObjectState.NORMAL;
    }

    @Override
    public void refreshObjectState(@NotNull DBRProgressMonitor monitor) throws DBCException {
    }

    @Override
    public DamengSourceType getSourceType() {
        return DamengSourceType.SYNONYM;
    }

    @Override
    public DBEPersistAction[] getCompileActions(DBRProgressMonitor monitor) throws DBCException {
        return null;
    }
    
}
