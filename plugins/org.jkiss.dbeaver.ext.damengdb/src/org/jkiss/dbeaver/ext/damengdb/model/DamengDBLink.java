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
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.damengdb.internal.DamengMessages;
import org.jkiss.dbeaver.ext.damengdb.model.source.DamengSourceObject;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObjectState;
import org.jkiss.utils.CommonUtils;

/**
 * DB Link
 */
public class DamengDBLink extends DamengSchemaObject implements DamengSourceObject {
	
    // Connect to Dameng by MAL
    public final static int DB_TYPE_DAMENG_ID = 0;

    public final static String DB_TYPE_DAMENG = "DAMENG";

    // Connect to Oracle by OCI
    public final static int DB_TYPE_ORACLE_ID = 1;

    public final static String DB_TYPE_ORACLE = "ORACLE";

    // Connect to remote database by ODBC
    public final static int DB_TYPE_ODBC_ID = 2;

    public final static String DB_TYPE_ODBC = "ODBC";

    // Connect to remote database by GATEWAY
    public final static int DB_TYPE_GATEWAY_ID = 3;

    public final static String DB_TYPE_GATEWAY = "GATEWAY";

    // Connect to Dameng by DPI
    public final static int DB_TYPE_DPI_ID = 4;

    public final static String DB_TYPE_DPI = "DPI";

    protected final static String DBLINK_NAME = "DBLINK_NAME";

    protected final static String SCHID = "SCHID";

    protected final static String SCH_NAME = "SCH_NAME";

    protected final static String LOGIN_USER_NAME = "LOGNAME";

    protected final static String CONNECTION_STRING = "CONSTR";

    protected final static String CREATED_DATE = "CRTDATE";

    protected final static String DB_TYPE_ID = "DBTYPE";

    private static final Log log = Log.getLog(DamengDBLink.class);

    private String dbLinkName;

    private Date createdDate;

    private String dbType;

    private String schemaName;

    private String conStr;

    private String loginUserName;

    private String isPubStr;

    private boolean isPub;

    private String dbLinkdefinationText;

    public DamengDBLink(DBRProgressMonitor progressMonitor, DamengSchema schema, ResultSet dbResult) {
        // Dblink name is show by DmObject
        super(schema, JDBCUtils.safeGetString(dbResult, DBLINK_NAME), true);
        this.dbLinkName = JDBCUtils.safeGetString(dbResult, DBLINK_NAME);
        this.schemaName = JDBCUtils.safeGetString(dbResult, SCH_NAME);
        this.loginUserName = JDBCUtils.safeGetString(dbResult, LOGIN_USER_NAME);
        this.conStr = JDBCUtils.safeGetString(dbResult, CONNECTION_STRING);
        this.createdDate = JDBCUtils.safeGetTimestamp(dbResult, CREATED_DATE);
        this.isPubStr = JDBCUtils.safeGetLong(dbResult, SCHID) == 0 ? DamengMessages.dameng_dblink_is_public
            : DamengMessages.dameng_dblink_is_unpublic;
        this.isPub = JDBCUtils.safeGetLong(dbResult, SCHID) == 0 ? true : false;

        switch (JDBCUtils.safeGetInt(dbResult, DB_TYPE_ID)) {
            case DB_TYPE_DAMENG_ID:
                this.dbType = DB_TYPE_DAMENG;
                break;
            case DB_TYPE_ORACLE_ID:
                this.dbType = DB_TYPE_ORACLE;
                break;
            case DB_TYPE_ODBC_ID:
                this.dbType = DB_TYPE_ODBC;
                break;
            case DB_TYPE_GATEWAY_ID:
                this.dbType = DB_TYPE_GATEWAY;
                break;
            case DB_TYPE_DPI_ID:
                this.dbType = DB_TYPE_DPI;
                break;
        }

    }

    public static Object resolveObject(DBRProgressMonitor monitor, DamengSchema schema, String dbLink)
        throws DBException {
        if (CommonUtils.isEmpty(dbLink)) {
            return null;
        }
        final DamengDBLink object = schema.dbLinkCache.getObject(monitor, schema, dbLink);
        if (object == null) {
            log.warn("DB Link '" + dbLink + "' not found in schema '" + schema.getName() + "'");
            return dbLink;
        }
        return object;
    }

    @Property(viewable = true, editable = false, order = 2)
    public String getSchemaName() {
        return schemaName;
    }

    @Property(viewable = true, editable = false, order = 3)
    public String getLoginUserName() {
        return loginUserName;
    }

    @Property(viewable = true, editable = false, order = 4)
    public String getConStr() {
        return conStr;
    }

    @Property(viewable = true, editable = false, order = 5)
    public String getIsPub() {
        return isPubStr;
    }

    @Property(viewable = true, editable = false, order = 6)
    public String getDbType() {
        return dbType;
    }

    @Property(viewable = true, editable = false, order = 7)
    public Date getCreatedDate() {
        return createdDate;
    }

    @Override
    @Property(hidden = true, editable = false, updatable = false, order = -1)
    public String getObjectDefinitionText(DBRProgressMonitor monitor, Map<String, Object> options) throws DBException {
        if (dbLinkdefinationText == null) {
            StringBuffer definationText = new StringBuffer();

            if (!isPub) {
                definationText.append("--");
                definationText.append("\"" + this.getSchemaName() + "\"");
                definationText.append(".");
                definationText.append("\"" + getName() + "\"");
                definationText.append(DamengConstants.LINE_SEPARATOR + DamengConstants.LINE_SEPARATOR);
            } else {
                definationText.append("-- ");
                definationText.append("\"" + getName() + "\"");
                definationText.append(DamengConstants.LINE_SEPARATOR + DamengConstants.LINE_SEPARATOR);
            }

            definationText.append("CREATE OR REPLACE ");
            if (isPub) {
                definationText.append("PUBLIC ");
            }
            definationText.append("LINK ");
            if (schemaName != null) {
                definationText.append("\"" + schemaName + "\"");
                definationText.append(".");
            }

            definationText.append("\"" + dbLinkName + "\" ");
            definationText.append("CONNECT ");
            definationText.append("'" + dbType + "'");
            definationText.append(" WITH ");
            definationText.append("\"" + loginUserName + "\" ");
            definationText.append("IDENTIFIED BY ");
            definationText.append("\"" + DamengConstants.PASSWORD + "\" ");
            definationText.append("USING ");
            definationText.append("\'" + conStr + "\'");

            dbLinkdefinationText = definationText.toString();

        }

        return dbLinkdefinationText;
    }

    @Override
    public void setObjectDefinitionText(String source) {
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
        return DamengSourceType.DBLINK;
    }

    @Override
    public DBEPersistAction[] getCompileActions(DBRProgressMonitor monitor) throws DBCException {
        return null;
        // return new DBEPersistAction[0];
    }

    public DBEPersistAction[] getRunActions() {
        StringBuffer runScript = new StringBuffer();
        runScript.append(" SELECT 1 FROM DUAL");
        runScript.append(" @ ");
        if (schemaName != null) {
            runScript.append("\"" + schemaName + "\"");
            runScript.append(".");
        }
        runScript.append("\"" + getName() + "\"");
        return new DBEPersistAction[] {
            new DamengObjectPersistAction(DamengObjectType.DBLINK, "Test DBLink", runScript.toString())};
    }
    
}
