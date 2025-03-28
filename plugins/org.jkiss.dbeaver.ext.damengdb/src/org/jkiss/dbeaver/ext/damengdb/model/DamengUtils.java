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

import org.jkiss.dbeaver.DBDatabaseException;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.damengdb.edit.DamengTableColumnManager;
import org.jkiss.dbeaver.ext.damengdb.internal.DamengMessages;
import org.jkiss.dbeaver.ext.damengdb.model.source.DamengSourceObject;
import org.jkiss.dbeaver.ext.damengdb.model.source.DamengStatefulObject;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.DBObjectNameCaseTransformer;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistAction;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.sql.SQLConstants;
import org.jkiss.dbeaver.model.sql.SQLUtils;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectLazy;
import org.jkiss.dbeaver.model.struct.DBStructUtils;
import org.jkiss.dbeaver.model.struct.cache.DBSObjectCache;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.IOUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DamengUtils
 */
public class DamengUtils {
	
    public static final String LINE_SEPARATOR = System.getProperty("line.separator");
    // USER ROLE PRIVILEGE
    public final static String PRIV_ALL = "ALL";
    public final static String PRIV_SELECT = "SELECT";
    public final static String PRIV_INSERT = "INSERT";
    public final static String PRIV_DELETE = "DELETE";
    public final static String PRIV_UPDATE = "UPDATE";
    public final static String PRIV_REFERENCES = "REFERENCES";
    public final static String PRIV_EXECUTE = "EXECUTE";
    public final static String PRIV_USAGE = "USAGE";
    public final static String PRIV_SELECT_FOR_DUMP = "SELECT FOR DUMP";
    public final static String PRIV_READ = "READ";
    public final static String PRIV_WRITE = "WRITE";
    public final static String PRIV_ALTER = "ALTER";
    public final static String PRIV_INDEX = "INDEX";
    public final static String[] PRIV_TABLE_VIEW = {PRIV_ALL, PRIV_SELECT, PRIV_INSERT, PRIV_DELETE, PRIV_UPDATE,
        PRIV_REFERENCES, PRIV_SELECT_FOR_DUMP, PRIV_ALTER, PRIV_INDEX};
    private static final Log log = Log.getLog(DamengUtils.class);
    private static String ObjFullName = "";
    private static String objId = "";
    // key：User or Role，value：Privilege info
    private static HashMap<String, DamengPrivilege[]> urObjPrivsMap;
    private static HashMap<String, DamengPrivilege[]> orgUrObjPrivsMap;
    private static String colId = "-1";

    public static String getDDL(DBRProgressMonitor monitor, String objectType, DamengTableBase object,
                                DamengDDLFormat ddlFormat, Map<String, Object> options) throws DBException {
        if (monitor.isCanceled()) {
            return "";
        }
        String objectFullName = DBUtils.getObjectFullName(object, DBPEvaluationContext.DDL);

        DamengSchema schema = object.getContainer();

        final DamengDataSource dataSource = object.getDataSource();

        monitor.subTask("Load sources for " + objectType + " '" + objectFullName + "'...");
        try (final JDBCSession session = DBUtils.openMetaSession(monitor, object,
            "Load source code for " + objectType + " '" + objectFullName + "'")) {

            if (CommonUtils.getOption(options, DBPScriptObject.OPTION_DDL_ONLY_FOREIGN_KEYS)) {
                if (!CommonUtils.isEmpty(object.getConstraints(monitor))) {
                    return invokeDBMSMetadataGetDependentDDL(session, schema, object,
                        DBMSMetaDependentObjectType.REF_CONSTRAINT, ddlFormat.isShowStorage());
                } else {
                    return "";
                }
            }

            try {
                // Do not add semicolon in the end
                JDBCUtils.executeProcedure(session, "begin\n"
                    + "DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM,'STORAGE',"
                    + ddlFormat.isShowStorage() + ");\n"
                    + "DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM, 'CONSTRAINTS',true);\n"
                    + "end;");
            } catch (SQLException e) {
                log.error("Can't apply DDL transform parameters", e);
            }

            if (monitor.isCanceled()) {
                return "";
            }

            String ddl;
            // Read main object DDL
            try (JDBCPreparedStatement dbStat = session.prepareStatement(
                "SELECT DBMS_METADATA.GET_DDL(?,?" + (schema == null ? "" : ",?") + ") TXT FROM DUAL")) {
                dbStat.setString(1, objectType);
                dbStat.setString(2, object.getName());
                if (schema != null) {
                    dbStat.setString(3, schema.getName());
                }
                try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                    if (dbResult.next()) {
                        Object ddlValue = dbResult.getObject(1);
                        if (ddlValue instanceof Clob) {
                            StringWriter buf = new StringWriter();
                            try (Reader clobReader = ((Clob) ddlValue).getCharacterStream()) {
                                IOUtils.copyText(clobReader, buf);
                            } catch (IOException e) {
                                e.printStackTrace(new PrintWriter(buf, true));
                            }
                            ddl = buf.toString();

                        } else {
                            ddl = CommonUtils.toString(ddlValue);
                        }
                    } else {
                        log.warn("No DDL for " + objectType + " '" + objectFullName + "'");
                        return "-- EMPTY DDL";
                    }
                }
            }
            ddl = ddl.trim();

            if (monitor.isCanceled()) {
                return ddl;
            }

            if (!CommonUtils.isEmpty(object.getConstraints(monitor))
                && !CommonUtils.getOption(options, DBPScriptObject.OPTION_DDL_SKIP_FOREIGN_KEYS)
                && CommonUtils.getOption(options, DBPScriptObject.OPTION_DDL_SEPARATE_FOREIGN_KEYS_STATEMENTS)) {
                ddl += invokeDBMSMetadataGetDependentDDL(session, schema, object,
                    DBMSMetaDependentObjectType.REF_CONSTRAINT, ddlFormat.isShowStorage());
            }

            if (monitor.isCanceled()) {
                return ddl;
            }

            if (!CommonUtils.isEmpty(object.getTriggers(monitor))) {
                ddl += invokeDBMSMetadataGetDependentDDL(session, schema, object, DBMSMetaDependentObjectType.TRIGGER,
                    ddlFormat.isShowStorage());
            }

            if (monitor.isCanceled()) {
                return ddl;
            }

            if (!CommonUtils.isEmpty(object.getIndexes(monitor))) {
                // Add index info to main DDL. For some reasons, GET_DDL returns
                // columns, constraints, but not indexes
                ddl += invokeDBMSMetadataGetDependentDDL(session, schema, object, DBMSMetaDependentObjectType.INDEX,
                    ddlFormat.isShowStorage());
            }

            if (monitor.isCanceled()) {
                return ddl;
            }

            if (ddlFormat == DamengDDLFormat.FULL) {
                // Add grants info to main DDL
                ObjFullName = object.getFullyQualifiedName(null);
                objId = getId(session, schema, object);

                List<DamengGrantUser> userList = DamengUserDao.getUsers(getCurUserType(session), session);
                boolean remove = false;
                do {
                    remove = false;
                    for (DamengGrantUser user : userList) {
                        if (user.getName().equals(DamengGrantUser.USER_NAME_SYSDBA)
                            || user.getName().equals(DamengGrantUser.USER_NAME_SYSSSO)
                            || user.getName().equals(DamengGrantUser.USER_NAME_SYSAUDITOR)) {
                            userList.remove(user);
                            remove = true;
                            break;
                        }
                        remove = false;
                    }
                } while (remove);

                // role
                List<DamengGrantRole> roleList = DamengRoleDao.getRoles(getCurUserType(session), session);
                do {
                    remove = false;
                    for (DamengGrantRole role : roleList) {
                        if (role.getName().equals(DamengGrantRole.ROLE_NAME_DBA)
                            || role.getName().equals(DamengGrantRole.ROLE_NAME_POLICY)
                            || role.getName().equals(DamengGrantRole.ROLE_NAME_AUDIT)) {
                            roleList.remove(role);
                            remove = true;
                            break;
                        }
                        remove = false;
                    }
                } while (remove);

                UserRole[] urs = new UserRole[userList.size() + roleList.size()];
                int i = 0;
                for (DamengGrantUser user : userList) {
                    urs[i++] = new UserRole(UserRole.TYPE_USER, user, null);
                }
                for (DamengGrantRole role : roleList) {
                    urs[i++] = new UserRole(UserRole.TYPE_ROLE, null, role);
                }

                urObjPrivsMap = new HashMap<String, DamengPrivilege[]>();
                orgUrObjPrivsMap = new HashMap<String, DamengPrivilege[]>();

                for (UserRole ur : urs) {
                    getUrObjPrivliege(ur, session);
                }

                StringBuilder sql = new StringBuilder();
                List<String> sqls = null;

                sqls = DamengRoleDao.builePrivilegeManageDDL(ObjFullName, urObjPrivsMap, orgUrObjPrivsMap, false);

                for (String s : sqls) {
                    sql.append(s + LINE_SEPARATOR + LINE_SEPARATOR);
                }

                ddl += "\n\n" + sql;
            }

            if (monitor.isCanceled()) {
                return ddl;
            }

            if (ddlFormat != DamengDDLFormat.COMPACT) {
                // Add object and objects columns info to main DDL
                ddl = addCommentsToDDL(monitor, object, ddl);
            }
            return ddl;
        } catch (SQLException e) {
            if (object instanceof DamengTablePhysical) {
                log.error("Error generating Dm DDL. Generate default.", e);
                return DBStructUtils.generateTableDDL(monitor, object, options, true);
            } else {
                throw new DBDatabaseException(e, dataSource);
            }
        }
    }

    private static DamengPrivilege[] getUrObjPrivliege(UserRole ur, JDBCSession session) {
        String urName = "";
        switch (ur.getType()) {
            case UserRole.TYPE_USER:
                urName = ur.getUser().getFullName();
                break;
            case UserRole.TYPE_ROLE:
                urName = ur.getRole().getFullName();
                break;
        }
        DamengPrivilege[] privs = urObjPrivsMap.get(urName);
        if (privs == null) {
            // initial Privilege of Object（Table、View、objType...） on other Role
            // or User（distinguish through urName）
            privs = new DamengPrivilege[PRIV_TABLE_VIEW.length];
            for (int i = 0; i < privs.length; ++i) {
                privs[i] = new DamengPrivilege();
                privs[i].setPrivName(PRIV_TABLE_VIEW[i]);
            }
            // reset the Privilege of the Object on the ur
            resetObjPrivs(privs, ur, session);
            urObjPrivsMap.put(urName, privs);
        }
        return privs;
    }

    private static void resetObjPrivs(DamengPrivilege[] privs, UserRole ur, JDBCSession session) {
        // SessionContext.setCurrentSession(session);
        String urName = ur.getFullName();
        List<DamengPrivilege> orgObjPrivsList;
        if (ur.getType() == UserRole.TYPE_USER) {
            orgObjPrivsList = DamengRoleDao.getObjPrivileges(null, ur.getUser(), objId, colId, session);
        } else {
            orgObjPrivsList = DamengRoleDao.getObjPrivileges(ur.getRole(), null, objId, colId, session);
        }
        // change privs according to the Privilege, ObjPrivsMap shows all
        // Privilege
        for (int i = 0; i < privs.length; ++i) {
            for (DamengPrivilege priv : orgObjPrivsList) {
                if (privs[i].getPrivName().equals(priv.getPrivName())) {
                    privs[i].setBeGrant(true);
                    privs[i].setCanGrant(priv.isCanGrant());
                    break;
                }
            }
        }
        // orgUrObjPrivsMap is only assigned at here
        orgObjPrivsList.removeAll(orgObjPrivsList);
        orgUrObjPrivsMap.put(urName, convert(orgObjPrivsList.toArray()));
    }

    protected static DamengPrivilege[] convert(Object[] objs) {
        if (objs == null || objs.length == 0) {
            return new DamengPrivilege[0];
        }

        DamengPrivilege[] privs = new DamengPrivilege[objs.length];
        for (int i = 0; i < privs.length; ++i) {
            privs[i] = (DamengPrivilege) objs[i];
        }

        return privs;
    }

    private static String getId(JDBCSession session, DamengSchema schema, DamengTableBase object) {
        String id = "";
        try (JDBCPreparedStatement dbStat = session.prepareStatement(
            "select id from sysobjects where name = ? and schid = (select ID from sysobjects where name = ? and TYPE$ = 'SCH')")) {
            dbStat.setString(1, object.getName());
            dbStat.setString(2, schema.getName());
            try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                if (dbResult.next()) {
                    id = dbResult.getString(1).trim();
                }
            }
        } catch (Exception e) {
        }
        return id;
    }

    private static int getCurUserType(JDBCSession session) {
        int userType = -1;
        try (JDBCPreparedStatement dbStat = session.prepareStatement(
            "select INFO1 from sysobjects where TYPE$='UR' and SUBTYPE$='USER' and name = (select User)")) {
            try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                if (dbResult.next()) {
                    userType = dbResult.getInt(1);
                }
            }
        } catch (Exception e) {
        }
        return userType;
    }

    private static String invokeDBMSMetadataGetDependentDDL(JDBCSession session, DamengSchema schema,
                                                            DamengTableBase object, DBMSMetaDependentObjectType dependentObjectType,
                                                            boolean isShowStorage) {
        String ddl = "";

        if (dependentObjectType.equals(DBMSMetaDependentObjectType.INDEX)) {
            try (JDBCPreparedStatement dbStat = session.prepareStatement("SELECT distinct  INDEXDEF(IND_OBJ.ID,1)\r\n"
                +
                "from (select * from SYSINDEXES where ROOTFILE != -1 or (XTYPE & 0x1000) = 0x1000 or (XTYPE & 0x2000) = 0x2000 or (XTYPE & 0x08) = 0x08 or (FLAG & 0x08) = 0x08 or (XTYPE & 0x8000) = 0x8000 or (XTYPE & 0x40) = 0x40) INDS, SYSCOLUMNS COLS, ALL_INDEXES i, \r\n"
                +
                "(select distinct IND_OBJ_INNER.ID, IND_OBJ_INNER.NAME, IND_OBJ_INNER.CRTDATE, IND_OBJ_INNER.PID, IND_OBJ_INNER.VALID, IND_OBJ_INNER.INFO7 from SYSOBJECTS IND_OBJ_INNER where IND_OBJ_INNER.SUBTYPE$ = 'INDEX') IND_OBJ, (select ID, NAME, SCHID from SYSOBJECTS where TYPE$='SCHOBJ' and SUBTYPE$ like '_TAB'  \r\n"
                +
                " and  NAME = ? and SCHID = ?) TAB_OBJ, (select ID, NAME from SYSOBJECTS where TYPE$='SCH' and  ID = ?) SCH_OBJ, ALL_IND_COLUMNS ic where INDS.ID=IND_OBJ.ID and IND_OBJ.PID=TAB_OBJ.ID and TAB_OBJ.SCHID=SCH_OBJ.ID  and i.owner = SCH_OBJ.name and i.index_name = IND_OBJ.NAME and ic.index_owner = SCH_OBJ.NAME AND ic.index_name = IND_OBJ.name \r\n"
                +
                "and COLS.ID = IND_OBJ.PID and (SF_COL_IS_IDX_KEY(INDS.KEYNUM, INDS.KEYINFO, COLS.COLID)=1 or (INDS.XTYPE & 0x1000) = 0x1000 or (INDS.XTYPE & 0x2000) = 0x2000 or (XTYPE & 0x08) = 0x08)\r\n")) {
                dbStat.setString(1, object.getName());
                dbStat.setLong(2, schema.getId());
                dbStat.setLong(3, schema.getId());
                try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                    while (dbResult.next()) {
                        String tempDDL = dbResult.getString(1);
                        if (!tempDDL.equalsIgnoreCase(DamengMessages.dameng_system_index_ddl_error)) {
                            if (!isShowStorage) {
                                int index = tempDDL.indexOf("STORAGE");
                                if (index != -1) {
                                    tempDDL = tempDDL.substring(0, index) + ";";
                                }
                            }

                            ddl += "\n\n" + tempDDL;
                        }
                    }
                    return ddl;
                }
            } catch (Exception e) {
                // No dependent index DDL or something went wrong
                log.debug("Error reading dependent DDL '" + dependentObjectType + "' for '"
                    + object.getFullyQualifiedName(DBPEvaluationContext.DDL) + "': " + e.getMessage());
            }
        }

        try (JDBCPreparedStatement dbStat = session.prepareStatement("SELECT DBMS_METADATA.GET_DEPENDENT_DDL('"
            + dependentObjectType.name() + "',?" + (schema == null ? "" : ",?") + ") TXT FROM DUAL")) {
            dbStat.setString(1, object.getName());
            if (schema != null) {
                dbStat.setString(2, schema.getName());
            }
            try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                if (dbResult.next()) {
                    ddl = "\n\n" + dbResult.getString(1).trim();
                }
            }
        } catch (Exception e) {
            // No dependent index DDL or something went wrong
            log.debug("Error reading dependent DDL '" + dependentObjectType + "' for '"
                + object.getFullyQualifiedName(DBPEvaluationContext.DDL) + "': " + e.getMessage());
        }
        return ddl;
    }

    private static String addCommentsToDDL(DBRProgressMonitor monitor, DamengTableBase object, String ddl) {
        StringBuilder ddlBuilder = new StringBuilder(ddl);
        String objectFullName = object.getFullyQualifiedName(DBPEvaluationContext.DDL);

        String objectComment = object.getComment(monitor);
        if (!CommonUtils.isEmpty(objectComment)) {
            String objectTypeName = "TABLE";
            if (object instanceof DamengMaterializedView) {
                objectTypeName = "MATERIALIZED VIEW";
            }
            ddlBuilder.append("\n\n").append("COMMENT ON ").append(objectTypeName).append(" ").append(objectFullName)
                .append(" IS ").append(SQLUtils.quoteString(object.getDataSource(), objectComment))
                .append(SQLConstants.DEFAULT_STATEMENT_DELIMITER);
        }

        try {
            List<DamengTableColumn> attributes = object.getAttributes(monitor);
            if (!CommonUtils.isEmpty(attributes)) {
                List<DBEPersistAction> actions = new ArrayList<>();
                if (CommonUtils.isEmpty(objectComment)) {
                    ddlBuilder.append("\n");
                }
                for (DamengTableColumn column : CommonUtils.safeCollection(attributes)) {
                    String columnComment = column.getComment(monitor);
                    if (!CommonUtils.isEmpty(columnComment)) {
                        DamengTableColumnManager.addColumnCommentAction(actions, column, column.getTable());
                    }
                }
                if (!CommonUtils.isEmpty(actions)) {
                    for (DBEPersistAction action : actions) {
                        ddlBuilder.append("\n").append(action.getScript())
                            .append(SQLConstants.DEFAULT_STATEMENT_DELIMITER);
                    }
                }
            }
        } catch (DBException e) {
            log.debug("Error reading object columns", e);
        }

        return ddlBuilder.toString();
    }

    public static void setCurrentSchema(JDBCSession session, String schema) throws SQLException {
        JDBCUtils.executeSQL(session,
            "ALTER SESSION SET CURRENT_SCHEMA=" + DBUtils.getQuotedIdentifier(session.getDataSource(), schema));
    }

    public static String getCurrentSchema(JDBCSession session) throws SQLException {
        return JDBCUtils.queryString(session, "SELECT SYS_CONTEXT( 'USERENV', 'CURRENT_SCHEMA' ) FROM DUAL");
    }

    public static String normalizeSourceName(DamengSourceObject object, boolean body) {
        try {
            String source = body ? ((DBPScriptObjectExt) object).getExtendedDefinitionText(null)
                : object.getObjectDefinitionText(null, DBPScriptObject.EMPTY_OPTIONS);
            if (source == null) {
                return null;
            }
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b" + object.getSourceType() + "\\b"
                    + (body ? "\\s+BODY" : "") + "\\s(\\s*)([\\w$\\.\\\"]+)[\\s\\(]+",
                java.util.regex.Pattern.CASE_INSENSITIVE);
            final Matcher matcher = pattern.matcher(source);
            if (matcher.find()) {
                String objectName = matcher.group(2);
                if (objectName.indexOf('.') == -1) {
                    if (!objectName.equalsIgnoreCase(object.getName())) {
                        object.setName(DBObjectNameCaseTransformer.transformObjectName(object, objectName));
                        object.getDataSource().getContainer()
                            .fireEvent(new DBPEvent(DBPEvent.Action.OBJECT_UPDATE, object));
                    }
                    return source;
                }
            }
            return source.trim();
        } catch (DBException e) {
            log.error(e);
            return null;
        }
    }

    public static void addSchemaChangeActions(DBCExecutionContext executionContext, List<DBEPersistAction> actions,
                                              DamengSourceObject object) {
        DamengSchema schema = object.getSchema();
        if (schema == null) {
            return;
        }
        actions.add(0, new SQLDatabasePersistAction("Set target schema",
            "ALTER SESSION SET CURRENT_SCHEMA=" + schema.getName(), DBEPersistAction.ActionType.INITIALIZER));
        DamengSchema defaultSchema = ((DamengExecutionContext) executionContext).getDefaultSchema();
        if (schema != defaultSchema && defaultSchema != null) {
            actions.add(new SQLDatabasePersistAction("Set current schema",
                "ALTER SESSION SET CURRENT_SCHEMA=" + defaultSchema.getName(),
                DBEPersistAction.ActionType.FINALIZER));
        }
    }

    public static String getSysSchemaPrefix(DamengDataSource dataSource) {
        boolean useSysView = CommonUtils.toBoolean(dataSource.getContainer().getConnectionConfiguration()
            .getProviderProperty(DamengConstants.PROP_METADATA_USE_SYS_SCHEMA));
        if (useSysView) {
            return DamengConstants.SCHEMA_SYS + ".";
        } else {
            return "";
        }
    }

    public static String getSource(DBRProgressMonitor monitor, DamengSourceObject sourceObject, boolean body,
                                   boolean insertCreateReplace) throws DBCException {
        if (sourceObject.getSourceType().isCustom()) {
            log.warn("Can't read source for custom source objects");
            return "-- ???? CUSTOM SOURCE";
        }
        final String sourceType = sourceObject.getSourceType().name().replace("_", " ");
        final DamengSchema sourceOwner = sourceObject.getSchema();
        if (sourceOwner == null) {
            log.warn("No source owner for object '" + sourceObject.getName() + "'");
            return null;
        }
        monitor.beginTask("Load sources for '" + sourceObject.getName() + "'...", 1);
        String sysViewName = DamengConstants.VIEW_DBA_SOURCE;
        if (!sourceObject.getDataSource().isViewAvailable(monitor, DamengConstants.SCHEMA_SYS, sysViewName)) {
            sysViewName = DamengConstants.VIEW_ALL_SOURCE;
        }
        try (final JDBCSession session = DBUtils.openMetaSession(monitor, sourceOwner,
            "Load source code for " + sourceType + " '" + sourceObject.getName() + "'")) {
            try (JDBCPreparedStatement dbStat = session
                .prepareStatement("SELECT TEXT FROM " + getSysSchemaPrefix(sourceObject.getDataSource())
                    + sysViewName + " " + "WHERE TYPE=? AND OWNER=? AND NAME=? " + "ORDER BY LINE")) {
                String sourceName;
                sourceName = sourceObject.getName();
                String type = "";
                if (sourceType.equalsIgnoreCase("TRIGGER"))
                {
                    type = "TRIG";
                } else if (sourceType.equalsIgnoreCase("PROCEDURE") || sourceType.equalsIgnoreCase("FUNCTION")
                    || sourceObject.getSourceType().equals(DamengSourceType.EXTERNAL_FUNCTION)) {
                    type = "PROC";
                } else {
                    type = sourceObject.getSourceType().name();
                }

                dbStat.setString(1, body ? sourceType + " BODY" : type);
                dbStat.setString(2, sourceOwner.getName());
                dbStat.setString(3, sourceName);
                dbStat.setFetchSize(DBConstants.METADATA_FETCH_SIZE);
                try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                    StringBuilder source = null;
                    int lineCount = 0;
                    while (dbResult.next()) {
                        if (monitor.isCanceled()) {
                            break;
                        }
                        String line = dbResult.getString(1);
                        if (source == null) {
                            source = new StringBuilder(200);
                        }
                        if (line == null) {
                            line = "";
                        }
                        source.append(line);
                        if (!line.endsWith("\n")) {
                            // Java source
                            source.append("\n");
                        }
                        lineCount++;
                        monitor.subTask("Line " + lineCount);
                    }
                    if (source == null) {
                        return null;
                    }
                    if (insertCreateReplace) {
                        return insertCreateReplace(sourceObject, body, source.toString());
                    } else {
                        return source.toString();
                    }
                }
            } catch (SQLException e) {
                throw new DBCException(e, session.getExecutionContext());
            }
        } finally {
            monitor.done();
        }
    }

    public static String getSysUserViewName(DBRProgressMonitor monitor, DamengDataSource dataSource, String viewName) {
        String dbaView = "DBA_" + viewName;
        if (dataSource.isViewAvailable(monitor, DamengConstants.SCHEMA_SYS, dbaView)) {
            return DamengUtils.getSysSchemaPrefix(dataSource) + dbaView;
        } else {
            return DamengUtils.getSysSchemaPrefix(dataSource) + "USER_" + viewName;
        }
    }

    public static String getAdminAllViewPrefix(DBRProgressMonitor monitor, DamengDataSource dataSource,
                                               String viewName) {
        boolean useDBAView = CommonUtils.toBoolean(dataSource.getContainer().getConnectionConfiguration()
            .getProviderProperty(DamengConstants.PROP_ALWAYS_USE_DBA_VIEWS));
        if (useDBAView) {
            String dbaView = "DBA_" + viewName;
            if (dataSource.isViewAvailable(monitor, DamengConstants.SCHEMA_SYS, dbaView)) {
                return DamengUtils.getSysSchemaPrefix(dataSource) + dbaView;
            }
        }
        return DamengUtils.getSysSchemaPrefix(dataSource) + "ALL_" + viewName;
    }

    public static String getSysCatalogHint(DamengDataSource dataSource) {
        return dataSource.isUseRuleHint() ? "/*+RULE*/" : "";
    }

    static <PARENT extends DBSObject> Object resolveLazyReference(DBRProgressMonitor monitor, PARENT parent,
                                                                  DBSObjectCache<PARENT, ?> cache, DBSObjectLazy<?> referrer,
                                                                  Object propertyId) throws DBException {
        final Object reference = referrer.getLazyReference(propertyId);
        if (reference instanceof String) {
            Object object;
            if (monitor != null) {
                object = cache.getObject(monitor, parent, (String) reference);
            } else {
                object = cache.getCachedObject((String) reference);
            }
            if (object != null) {
                return object;
            } else {
                // log.warn("Object '" + reference + "' not found");
                return reference;
            }
        } else {
            return reference;
        }
    }

    public static boolean getObjectStatus(DBRProgressMonitor monitor, DamengStatefulObject object,
                                          DamengObjectType objectType) throws DBCException {
        try (JDBCSession session = DBUtils.openMetaSession(monitor, object,
            "Refresh state of " + objectType.getTypeName() + " '" + object.getName() + "'")) {
            try (JDBCPreparedStatement dbStat = session.prepareStatement("SELECT STATUS FROM "
                + DamengUtils.getAdminAllViewPrefix(monitor, object.getDataSource(), "OBJECTS")
                + " WHERE OBJECT_TYPE=? AND OWNER=? AND OBJECT_NAME=?")) {
                dbStat.setString(1, objectType.getTypeName());
                dbStat.setString(2, object.getSchema().getName());
                dbStat.setString(3, DBObjectNameCaseTransformer.transformObjectName(object, object.getName()));
                try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                    if (dbResult.next()) {
                        return DamengConstants.RESULT_STATUS_VALID
                            .equals(dbResult.getString(DamengConstants.COLUMN_STATUS));
                    } else {
                        log.warn(objectType.getTypeName() + " '" + object.getName()
                            + "' not found in system dictionary");
                        return false;
                    }
                }
            } catch (SQLException e) {
                throw new DBCException(e, session.getExecutionContext());
            }
        }
    }

    public static String insertCreateReplace(DamengSourceObject object, boolean body, String source) {
        String sourceType = object.getSourceType().name();
        if (body) {
            sourceType += " BODY";
        }
        Pattern srcPattern = Pattern.compile("^(" + sourceType + ")\\s+(\"{0,1}\\w+\"{0,1})", Pattern.CASE_INSENSITIVE);
        Matcher matcher = srcPattern.matcher(source);
        if (matcher.find()) {
            return "CREATE OR REPLACE " + matcher.group(1) + " " + DBUtils.getQuotedIdentifier(object.getSchema()) + "."
                + matcher.group(2) + source.substring(matcher.end());
        }
        return source;
    }

    public static String formatWord(String word) {
        if (word == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(word.length());
        sb.append(Character.toUpperCase(word.charAt(0)));
        for (int i = 1; i < word.length(); i++) {
            char c = word.charAt(i);
            if ((c == 'i' || c == 'I') && sb.charAt(i - 1) == 'I') {
                sb.append('I');
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    public static String formatSentence(String sent) {
        if (sent == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        StringTokenizer st = new StringTokenizer(sent, " \t\n\r-,.\\/", true);
        while (st.hasMoreTokens()) {
            String word = st.nextToken();
            if (word.length() > 0) {
                result.append(formatWord(word));
            }
        }

        return result.toString();
    }

    /**
     * 根据SQLColumn中包含的数据类型信息来产生完整的数据类型定义.
     */
    public static String getRealTypeName(String typeName, String size, String scale) {
        StringBuilder typeNameBuffer = new StringBuilder();

        if (typeName.equalsIgnoreCase("CHAR") || typeName.equalsIgnoreCase("CHARACTER")
            || typeName.equalsIgnoreCase("VARCHAR") || typeName.equalsIgnoreCase("VARCHAR2")
            || typeName.equalsIgnoreCase("NVARCHAR") || typeName.equalsIgnoreCase("NCHAR")) {
            typeNameBuffer.append(typeName);
            typeNameBuffer.append('(');
            typeNameBuffer.append(size);
            if (scale.equalsIgnoreCase("7"))
            {
                typeNameBuffer.append(" CHAR");
            }
            typeNameBuffer.append(')');
        } else if (typeName.equalsIgnoreCase("FLOAT") || typeName.equalsIgnoreCase("DOUBLE")
            || typeName.equalsIgnoreCase("BINARY") || typeName.equalsIgnoreCase("VARBINARY")
            || typeName.equalsIgnoreCase("INTERVAL YEAR") || typeName.equalsIgnoreCase("INTERVAL MONTH")
            || typeName.equalsIgnoreCase("INTERVAL DAY") || typeName.equalsIgnoreCase("INTERVAL HOUR")
            || typeName.equalsIgnoreCase("INTERVAL MINUTE")) {
            typeNameBuffer.append(typeName);
            typeNameBuffer.append('(');
            typeNameBuffer.append(size);
            typeNameBuffer.append(')');
        } else if (typeName.equalsIgnoreCase("BLOB") || typeName.equalsIgnoreCase("CLOB")) {
            typeNameBuffer.append(typeName);
        } else if (typeName.equalsIgnoreCase("NUMERIC") || typeName.equalsIgnoreCase("DECIMAL")
            || typeName.equalsIgnoreCase("NUMBER") || typeName.equalsIgnoreCase("DEC")
            || typeName.equalsIgnoreCase("INTERVAL SECOND")) {
            if (size.equals("0") && scale.equals("0")) {
                typeNameBuffer.append(typeName);
            } else {
                typeNameBuffer.append(typeName);
                typeNameBuffer.append('(');
                typeNameBuffer.append(size);
                typeNameBuffer.append(", ");
                typeNameBuffer.append(scale);
                typeNameBuffer.append(')');
            }
        } else if (typeName.equalsIgnoreCase("TIME") || typeName.equalsIgnoreCase("TIMESTAMP")
            || typeName.equalsIgnoreCase("DATETIME")) {
            typeNameBuffer.append(typeName);
            typeNameBuffer.append('(');
            typeNameBuffer.append(scale);
            typeNameBuffer.append(')');
        } else if (typeName.equalsIgnoreCase("TIME WITH TIME ZONE")) {
            typeName = "TIME(" + scale + ") WITH TIME ZONE";
            return typeName;
        } else if (typeName.equalsIgnoreCase("TIMESTAMP WITH TIME ZONE")) {
            typeName = "TIMESTAMP(" + scale + ") WITH TIME ZONE";
            return typeName;
        } else if (typeName.equalsIgnoreCase("TIMESTAMP WITH LOCAL TIME ZONE")) {
            typeName = "TIMESTAMP(" + scale + ") WITH LOCAL TIME ZONE";
            return typeName;
        } else if (typeName.equalsIgnoreCase("DATETIME WITH TIME ZONE")) {
            typeName = "DATETIME(" + scale + ") WITH TIME ZONE";
            return typeName;
        } else if (typeName.equalsIgnoreCase("INTERVAL YEAR TO MONTH")) {
            typeName = "INTERVAL YEAR(" + size + ") TO MONTH";
            return typeName;
        } else if (typeName.equalsIgnoreCase("INTERVAL DAY TO HOUR")) {
            typeName = "INTERVAL DAY(" + size + ") TO HOUR";
            return typeName;
        } else if (typeName.equalsIgnoreCase("INTERVAL DAY TO MINUTE")) {
            typeName = "INTERVAL DAY(" + size + ") TO MINUTE";
            return typeName;
        } else if (typeName.equalsIgnoreCase("INTERVAL HOUR TO MINUTE")) {
            typeName = "INTERVAL HOUR(" + size + ") TO MINUTE";
            return typeName;
        } else if (typeName.equalsIgnoreCase("INTERVAL DAY TO SECOND")) {
            typeName = "INTERVAL DAY(" + size + ") TO SECOND(" + scale + ")";
            return typeName;
        } else if (typeName.equalsIgnoreCase("INTERVAL HOUR TO SECOND")) {
            typeName = "INTERVAL HOUR(" + size + ") TO SECOND(" + scale + ")";
            return typeName;
        } else if (typeName.equalsIgnoreCase("INTERVAL MINUTE TO SECOND")) {
            typeName = "INTERVAL MINUTE(" + size + ") TO SECOND(" + scale + ")";
            return typeName;
        } else {
            return typeName;
        }
        return typeNameBuffer.toString();
    }

    private enum DBMSMetaDependentObjectType {
        INDEX, CONSTRAINT, REF_CONSTRAINT, TRIGGER, OBJECT_GRANT
    }

}
