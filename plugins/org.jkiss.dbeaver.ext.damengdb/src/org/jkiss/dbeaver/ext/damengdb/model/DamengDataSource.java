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

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.osgi.util.NLS;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBDatabaseException;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ModelPreferences;
import org.jkiss.dbeaver.ext.damengdb.data.DamengValueHandlerProvider;
import org.jkiss.dbeaver.ext.damengdb.internal.DamengMessages;
import org.jkiss.dbeaver.ext.damengdb.model.plan.DamengQueryPlanner;
import org.jkiss.dbeaver.ext.damengdb.model.session.DamengServerSessionManager;
import org.jkiss.dbeaver.model.DBConstants;
import org.jkiss.dbeaver.model.DBPDataKind;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBPDataSourceInfo;
import org.jkiss.dbeaver.model.DBPErrorAssistant;
import org.jkiss.dbeaver.model.DBPObjectStatisticsCollector;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.access.DBAPasswordChangeInfo;
import org.jkiss.dbeaver.model.access.DBAUserPasswordManager;
import org.jkiss.dbeaver.model.admin.sessions.DBAServerSessionManager;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.connection.DBPDriver;
import org.jkiss.dbeaver.model.data.DBDAttributeContentTypeProvider;
import org.jkiss.dbeaver.model.data.DBDValueHandlerProvider;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCExecutionPurpose;
import org.jkiss.dbeaver.model.exec.DBCExecutionResult;
import org.jkiss.dbeaver.model.exec.DBCQueryTransformType;
import org.jkiss.dbeaver.model.exec.DBCQueryTransformer;
import org.jkiss.dbeaver.model.exec.DBCSession;
import org.jkiss.dbeaver.model.exec.DBCStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCDatabaseMetaData;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCFactory;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCStatement;
import org.jkiss.dbeaver.model.exec.output.DBCOutputWriter;
import org.jkiss.dbeaver.model.exec.output.DBCServerOutputReader;
import org.jkiss.dbeaver.model.exec.plan.DBCQueryPlanner;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCDataSource;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCExecutionContext;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCRemoteInstance;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCObjectCache;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCStructCache;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.ForTest;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.sql.SQLConstants;
import org.jkiss.dbeaver.model.sql.SQLState;
import org.jkiss.dbeaver.model.struct.DBSDataType;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectFilter;
import org.jkiss.dbeaver.model.struct.DBSStructureAssistant;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.utils.BeanUtils;
import org.jkiss.utils.CommonUtils;

public class DamengDataSource extends JDBCDataSource implements DBPObjectStatisticsCollector {
    
	private static final Log log = Log.getLog(DamengDataSource.class);

    final public SchemaCache schemaCache = new SchemaCache();

    final DataTypeCache dataTypeCache = new DataTypeCache();

    final TablespaceCache tablespaceCache = new TablespaceCache();

    final UserCache userCache = new UserCache();

    final UserDbaCache userDbaCache = new UserDbaCache();

    final UserAuditCache userAuditCache = new UserAuditCache();

    final UserPolicyCache userPolicyCache = new UserPolicyCache();

    final UserDboCache userDboCache = new UserDboCache();

    final UserSysCache userSysCache = new UserSysCache();

    final ProfileCache profileCache = new ProfileCache();

    final RoleCache roleCache = new RoleCache();
    private final Map<String, Boolean> availableViews = new HashMap<>();
    // 0:Three-Power Separation 1:Four-Power Separation
    private boolean privFlag = false;
    // Type of the login user
    private int userType = 0;
    private DmOutputReader outputReader;
    private DamengSchema publicSchema;
    private boolean isAdmin;
    private boolean isAdminVisible;
    private String planTableName;
    private boolean useRuleHint;
    private boolean hasStatistics;
    private boolean isPasswordExpireWarningShown;
    private Pattern ERROR_POSITION_PATTERN = Pattern.compile(".+\\s+line ([0-9]+), column ([0-9]+)");
    private Pattern ERROR_POSITION_PATTERN_2 = Pattern.compile(".+\\s+at line ([0-9]+)");
    private Pattern ERROR_POSITION_PATTERN_3 = Pattern.compile(".+\\s+at position\\: ([0-9]+)");

    public DamengDataSource(DBRProgressMonitor monitor, DBPDataSourceContainer container) throws DBException {
        super(monitor, container, new DamengSQLDialect());
        this.outputReader = new DmOutputReader();
    }

    @ForTest
    public DamengDataSource(DBPDataSourceContainer container) {
        super(container, new DamengSQLDialect());
        this.outputReader = new DmOutputReader();

        this.hasStatistics = false;

        DamengSchema defSchema = new DamengSchema(this, -1, "TEST_SCHEMA");
        schemaCache.setCache(Collections.singletonList(defSchema));
    }

    @Override
    public Object getDataSourceFeature(String featureId) {
        switch (featureId) {
            case DBPDataSource.FEATURE_MAX_STRING_LENGTH:
                return 4000;
        }

        return super.getDataSourceFeature(featureId);
    }

    public Integer getUsertype() {
        return userType;
    }

    public boolean isViewAvailable(@NotNull DBRProgressMonitor monitor, @Nullable String schemaName,
                                   @NotNull String viewName) {
        viewName = viewName.toUpperCase();
        Boolean available;
        synchronized (availableViews) {
            available = availableViews.get(viewName);
        }
        if (available == null) {
            try {
                try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Check view existence")) {
                    String viewNameQuoted = DBUtils.getQuotedIdentifier(this, viewName);
                    try (final JDBCPreparedStatement dbStat = session.prepareStatement("SELECT 1 FROM "
                        + (schemaName == null ? viewNameQuoted
                        : DBUtils.getQuotedIdentifier(this, schemaName) + "." + viewNameQuoted)
                        + " WHERE 1<>1")) {
                        dbStat.setFetchSize(1);
                        dbStat.execute();
                        available = true;
                    }
                }
            } catch (Exception e) {
                available = false;
            }
            synchronized (availableViews) {
                availableViews.put(viewName, available);
            }
        }
        return available;
    }

    @Override
    protected Connection openConnection(@NotNull DBRProgressMonitor monitor, @Nullable JDBCExecutionContext context,
                                        @NotNull String purpose) throws DBCException {
        try {
            Connection connection = super.openConnection(monitor, context, purpose);
            try {
                for (SQLWarning warninig = connection.getWarnings(); warninig != null
                    && !isPasswordExpireWarningShown; warninig = warninig.getNextWarning()) {
                    if (checkForPasswordWillExpireWarning(warninig)) {
                        isPasswordExpireWarningShown = true;
                    }
                }
            } catch (SQLException e) {
                log.debug("Can't get connection warnings", e);
            }
            return connection;
        } catch (DBCException e) {
            if (SQLState.getCodeFromException(e) == DamengConstants.EC_PASSWORD_EXPIRED) {
                // Here we could try to ask for expired password change
                // This is supported for thin driver since Dm 12.2
                if (changeExpiredPassword(monitor, context, purpose)) {
                    // Retry
                    return openConnection(monitor, context, purpose);
                }
            }
            throw e;
        }
    }

    @Override
    protected JDBCFactory createJdbcFactory() {
        return new DamengJDBCFactory();
    }

    private boolean checkForPasswordWillExpireWarning(@NotNull SQLWarning warning) {
        if (warning != null && warning.getErrorCode() == DamengConstants.EC_PASSWORD_WILL_EXPIRE) {
            DBWorkbench.getPlatformUI().showWarningMessageBox(DamengMessages.dameng_password_will_expire_warn_name,
                NLS.bind(DamengMessages.dameng_password_will_expire_warn_description, warning.getMessage()));
            return true;
        }
        return false;
    }

    private boolean changeExpiredPassword(DBRProgressMonitor monitor, JDBCExecutionContext context, String purpose) {
        // Ref:
        // https://stackoverflow.com/questions/21733300/dm-password-expiry-and-grace-period-handling-using-java-dm-jdbc

        DBPConnectionConfiguration connectionInfo = getContainer().getActualConnectionConfiguration();
        DBAPasswordChangeInfo passwordInfo = DBWorkbench.getPlatformUI().promptUserPasswordChange(
            "Password has expired. Set new password.", connectionInfo.getUserName(),
            connectionInfo.getUserPassword(), true, true);
        if (passwordInfo == null) {
            return false;
        }

        // Obtain connection
        try {
            if (passwordInfo.getNewPassword() == null) {
                throw new DBException("You can't set empty password");
            }
            Properties connectProps = getAllConnectionProperties(monitor, context, purpose, connectionInfo);
            connectProps.setProperty(DBConstants.PROP_USER, passwordInfo.getUserName());
            connectProps.setProperty(DBConstants.PROP_PASSWORD, passwordInfo.getOldPassword());
            connectProps.setProperty("dm.jdbc.newPassword", passwordInfo.getNewPassword());

            final String url = getConnectionURL(connectionInfo);
            monitor.subTask("Connecting for expired password change");
            Driver driverInstance = getDriverInstance(monitor);
            try (Connection connection = driverInstance.connect(url, connectProps)) {
                if (connection == null) {
                    throw new DBCException("Null connection returned");
                }
            }

            connectionInfo.setUserPassword(passwordInfo.getNewPassword());
            getContainer().getConnectionConfiguration().setUserPassword(passwordInfo.getNewPassword());
            getContainer().persistConfiguration();
            return true;
        } catch (Exception e) {
            DBWorkbench.getPlatformUI().showError("Error changing password", "Error changing expired password", e);
            return false;
        }
    }

    @Override
    protected JDBCExecutionContext createExecutionContext(JDBCRemoteInstance instance, String type) {
        return new DamengExecutionContext(instance, type);
    }

    protected void initializeContextState(@NotNull DBRProgressMonitor monitor, @NotNull JDBCExecutionContext context,
                                          JDBCExecutionContext initFrom) throws DBException {
        if (outputReader == null) {
            outputReader = new DmOutputReader();
        }
        // Enable DBMS output
        outputReader.enableServerOutput(monitor, context, outputReader.isServerOutputEnabled());
        if (initFrom != null) {
            ((DamengExecutionContext) context).setCurrentSchema(monitor,
                ((DamengExecutionContext) initFrom).getDefaultSchema());
        } else {
            ((DamengExecutionContext) context).refreshDefaults(monitor, true);
        }

        {
            DBPConnectionConfiguration connectionInfo = getContainer().getConnectionConfiguration();

            try (JDBCSession session = context.openSession(monitor, DBCExecutionPurpose.META,
                "Set connection parameters")) {
                try {
                    readDatabaseServerVersion(session.getMetaData());
                } catch (SQLException e) {
                    log.debug("Error reading metadata", e);
                }

                // Set session settings
                String sessionLanguage = connectionInfo.getProviderProperty(DamengConstants.PROP_SESSION_LANGUAGE);
                if (sessionLanguage != null) {
                    try {
                        JDBCUtils.executeSQL(session, "ALTER SESSION SET NLS_LANGUAGE='" + sessionLanguage + "'");
                    } catch (Throwable e) {
                        log.warn("Can't set session language", e);
                    }
                }
                String sessionTerritory = connectionInfo.getProviderProperty(DamengConstants.PROP_SESSION_TERRITORY);
                if (sessionTerritory != null) {
                    try {
                        JDBCUtils.executeSQL(session, "ALTER SESSION SET NLS_TERRITORY='" + sessionTerritory + "'");
                    } catch (Throwable e) {
                        log.warn("Can't set session territory", e);
                    }
                }
                setNLSParameter(session, connectionInfo, "NLS_DATE_FORMAT",
                    DamengConstants.PROP_SESSION_NLS_DATE_FORMAT);
                setNLSParameter(session, connectionInfo, "NLS_TIMESTAMP_FORMAT",
                    DamengConstants.PROP_SESSION_NLS_TIMESTAMP_FORMAT);
                setNLSParameter(session, connectionInfo, "NLS_LENGTH_SEMANTICS",
                    DamengConstants.PROP_SESSION_NLS_LENGTH_FORMAT);
                setNLSParameter(session, connectionInfo, "NLS_CURRENCY",
                    DamengConstants.PROP_SESSION_NLS_CURRENCY_FORMAT);
            }
        }
    }

    private void setNLSParameter(JDBCSession session, DBPConnectionConfiguration connectionInfo, String oraNlsName,
                                 String paramName) {
        String paramValue = connectionInfo.getProviderProperty(paramName);
        if (!CommonUtils.isEmpty(paramValue)) {
            try {
                JDBCUtils.executeSQL(session, "ALTER SESSION SET " + oraNlsName + "='" + paramValue + "'");
            } catch (Throwable e) {
                log.warn("Can not set session NLS parameter " + oraNlsName, e);
            }
        }
    }

    public DamengSchema getDefaultSchema() {
        return (DamengSchema) DBUtils.getDefaultContext(this, true).getContextDefaults().getDefaultSchema();
    }

    @Override
    protected DBPDataSourceInfo createDataSourceInfo(DBRProgressMonitor monitor,
                                                     @NotNull JDBCDatabaseMetaData metaData) {
        return new DamengDataSourceInfo(this, metaData);
    }

    @Override
    public ErrorType discoverErrorType(@NotNull Throwable error) {
        Throwable rootCause = CommonUtils.getRootCause(error);
        if (rootCause instanceof SQLException) {
            switch (((SQLException) rootCause).getErrorCode()) {
                case DamengConstants.EC_NO_RESULTSET_AVAILABLE:
                    return ErrorType.RESULT_SET_MISSING;
                case DamengConstants.EC_FEATURE_NOT_SUPPORTED:
                    return ErrorType.FEATURE_UNSUPPORTED;
            }
        }
        return super.discoverErrorType(error);
    }

    @Override
    protected Map<String, String> getInternalConnectionProperties(DBRProgressMonitor monitor, DBPDriver driver,
                                                                  JDBCExecutionContext context, String purpose,
                                                                  DBPConnectionConfiguration connectionInfo)
        throws DBCException {
        Map<String, String> connectionsProps = new HashMap<>();
        if (!getContainer().getPreferenceStore().getBoolean(ModelPreferences.META_CLIENT_NAME_DISABLE)) {
            String appName = DBUtils.getClientApplicationName(getContainer(), context, purpose);
            connectionsProps.put("appName", CommonUtils.truncateString(appName, 48));
        }

        return connectionsProps;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public boolean isAdminVisible() {
        return isAdmin || isAdminVisible;
    }

    public boolean isUseRuleHint() {
        return useRuleHint;
    }

    @Association
    public Collection<DamengSchema> getSchemas(DBRProgressMonitor monitor) throws DBException {
        return schemaCache.getAllObjects(monitor, this);
    }

    public DamengSchema getSchema(DBRProgressMonitor monitor, String name) throws DBException {
        if (publicSchema != null && publicSchema.getName().equals(name)) {
            return publicSchema;
        }
        // Schema cache may be null during DataSource initialization
        return schemaCache == null ? null : schemaCache.getObject(monitor, this, name);
    }

    @Association
    public Collection<DamengTablespace> getTablespaces(DBRProgressMonitor monitor) throws DBException {
        return tablespaceCache.getAllObjects(monitor, this);
    }

    public TablespaceCache getTablespaceCache() {
        return tablespaceCache;
    }

    @Association
    public Collection<DamengUser> getUsers(DBRProgressMonitor monitor) throws DBException {
        return userCache.getAllObjects(monitor, this);
    }

    @Association
    public DamengUser getUser(DBRProgressMonitor monitor, String name) throws DBException {
        return userCache.getObject(monitor, this, name);
    }

    @Association
    public Collection<DamengUser> getDbaUsers(DBRProgressMonitor monitor) throws DBException {
        return userDbaCache.getAllObjects(monitor, this);
    }

    @Association
    public DamengUser getDbaUser(DBRProgressMonitor monitor, String name) throws DBException {
        return userDbaCache.getObject(monitor, this, name);
    }

    @Association
    public Collection<DamengUser> getAuditUsers(DBRProgressMonitor monitor) throws DBException {
        return userAuditCache.getAllObjects(monitor, this);
    }

    @Association
    public DamengUser getAuditUser(DBRProgressMonitor monitor, String name) throws DBException {
        return userAuditCache.getObject(monitor, this, name);
    }

    @Association
    public Collection<DamengUser> getPolicyUsers(DBRProgressMonitor monitor) throws DBException {
        return userPolicyCache.getAllObjects(monitor, this);
    }

    @Association
    public DamengUser gePolicyUser(DBRProgressMonitor monitor, String name) throws DBException {
        return userPolicyCache.getObject(monitor, this, name);
    }

    @Association
    public Collection<DamengUser> getDboUsers(DBRProgressMonitor monitor) throws DBException {
        return userDboCache.getAllObjects(monitor, this);
    }

    @Association
    public DamengUser geDboUser(DBRProgressMonitor monitor, String name) throws DBException {
        return userDboCache.getObject(monitor, this, name);
    }

    @Association
    public Collection<DamengUser> getSysUsers(DBRProgressMonitor monitor) throws DBException {
        return userSysCache.getAllObjects(monitor, this);
    }

    @Association
    public DamengUser getSysUser(DBRProgressMonitor monitor, String name) throws DBException {
        return userSysCache.getObject(monitor, this, name);
    }

    @Association
    public Collection<DamengUserProfile> getProfiles(DBRProgressMonitor monitor) throws DBException {
        return profileCache.getAllObjects(monitor, this);
    }

    @Association
    public Collection<DamengRole> getRoles(DBRProgressMonitor monitor) throws DBException {
        return roleCache.getAllObjects(monitor, this);
    }

    public DamengGrantee getGrantee(DBRProgressMonitor monitor, String name) throws DBException {
        DamengUser user = userCache.getObject(monitor, this, name);
        if (user != null) {
            return user;
        }
        return roleCache.getObject(monitor, this, name);
    }

    @Association
    public Collection<DamengSynonym> getPublicSynonyms(DBRProgressMonitor monitor) throws DBException {
        return publicSchema.getSynonyms(monitor);
    }

    @Association
    public Collection<DamengDBLink> getPublicDatabaseLinks(DBRProgressMonitor monitor) throws DBException {
        return publicSchema.getDatabaseLinks(monitor);
    }

    public boolean isAtLeastV8() {
        return getInfo().getDatabaseVersion().getMajor() >= 8;
    }

    public boolean isAtLeastV9() {
        return isAtLeastV8();
    }

    public boolean isAtLeastV10() {
        return isAtLeastV8();
    }

    public boolean isAtLeastV11() {
        return isAtLeastV8();
    }

    public boolean isAtLeastV12() {
        return isAtLeastV8();
    }

    @Override
    public void initialize(@NotNull DBRProgressMonitor monitor) throws DBException {
        super.initialize(monitor);

        DBPConnectionConfiguration connectionInfo = getContainer().getConnectionConfiguration();

        {
            String useRuleHintProp = connectionInfo.getProviderProperty(DamengConstants.PROP_USE_RULE_HINT);
            if (useRuleHintProp != null) {
                useRuleHint = CommonUtils.getBoolean(useRuleHintProp, false);
            }
        }

        this.publicSchema = new DamengSchema(this, 1, DamengConstants.USER_PUBLIC);
        {
            try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Load data source meta info")) {
                // Check DBA role
                this.isAdmin = "YES".equals(
                    JDBCUtils.queryString(session, "SELECT 'YES' FROM USER_ROLE_PRIVS WHERE GRANTED_ROLE='DBA'"));
                this.isAdminVisible = isAdmin;
                if (!isAdminVisible) {
                    String showAdmin = connectionInfo.getProviderProperty(DamengConstants.PROP_ALWAYS_SHOW_DBA);
                    if (showAdmin != null) {
                        isAdminVisible = CommonUtils.getBoolean(showAdmin, false);
                    }
                }
                // check the priv_flag
                this.privFlag = "1".equals(JDBCUtils.queryString(session, "select sf_get_priv_flag()"));
                // check the user type of the login user
                this.userType = Integer
                    .parseInt(JDBCUtils.queryString(session, "select info1 from sysobjects where id = uid();"));
            } catch (SQLException e) {
                // throw new DBException(e);
                log.warn(e);
            }
        }
        // Cache data types
        dataTypeCache.setCaseSensitive(false);
        {
            List<DamengDataType> dtList = new ArrayList<>();
            for (Map.Entry<String, DamengDataType.TypeDesc> predefinedType : DamengDataType.PREDEFINED_TYPES
                .entrySet()) {
                DamengDataType dataType = new DamengDataType(this, predefinedType.getKey(), true);
                dtList.add(dataType);
            }
            this.dataTypeCache.setCache(dtList);
        }
    }

    @Override
    public DBSObject refreshObject(@NotNull DBRProgressMonitor monitor) throws DBException {
        super.refreshObject(monitor);

        this.schemaCache.clearCache();
        // this.dataTypeCache.clearCache();
        this.tablespaceCache.clearCache();
        this.userCache.clearCache();
        this.userDbaCache.clearCache();
        this.userAuditCache.clearCache();
        this.userPolicyCache.clearCache();
        this.userDboCache.clearCache();
        this.userSysCache.clearCache();
        this.profileCache.clearCache();
        this.roleCache.clearCache();
        hasStatistics = false;

        this.initialize(monitor);

        return this;
    }

    @Override
    public Collection<DamengSchema> getChildren(@NotNull DBRProgressMonitor monitor) throws DBException {
        return getSchemas(monitor);
    }

    @Override
    public DamengSchema getChild(@NotNull DBRProgressMonitor monitor, @NotNull String childName) throws DBException {
        return getSchema(monitor, childName);
    }

    @NotNull
    @Override
    public Class<? extends DamengSchema> getPrimaryChildType(@Nullable DBRProgressMonitor monitor) throws DBException {
        return DamengSchema.class;
    }

    @Override
    public void cacheStructure(@NotNull DBRProgressMonitor monitor, int scope) throws DBException {

    }

    @Nullable
    @Override
    public <T> T getAdapter(Class<T> adapter) {
        if (adapter == DBSStructureAssistant.class) {
            return adapter.cast(new DamengStructureAssistant(this));
        } else if (adapter == DBCServerOutputReader.class) {
            return adapter.cast(outputReader);
        } else if (adapter == DBAServerSessionManager.class) {
            return adapter.cast(new DamengServerSessionManager(this));
        } else if (adapter == DBCQueryPlanner.class) {
            return adapter.cast(new DamengQueryPlanner(this));
        } else if (adapter == DBAUserPasswordManager.class) {
            return adapter.cast(new DamengChangeUserPasswordManager(this));
        } else if (adapter == DBDAttributeContentTypeProvider.class) {
            return adapter.cast(DamengAttributeContentTypeProvider.INSTANCE);
        } else if (adapter == DBDValueHandlerProvider.class) {
            return adapter.cast(new DamengValueHandlerProvider());
        }
        return super.getAdapter(adapter);
    }

    @Override
    public void cancelStatementExecute(DBRProgressMonitor monitor, JDBCStatement statement) throws DBException {
        if (driverSupportsQueryCancel()) {
            super.cancelStatementExecute(monitor, statement);
        }
    }

    private boolean driverSupportsQueryCancel() {
        return true;
    }

    @NotNull
    @Override
    public DBPDataKind resolveDataKind(@NotNull String typeName, int valueType) {
        if ((typeName.equals(DamengConstants.TYPE_NAME_XML) || typeName.equals(DamengConstants.TYPE_FQ_XML))) {
            return DBPDataKind.CONTENT;
        }
        if (typeName.equals(DamengConstants.TYPE_DMRASTER)) {
            return DBPDataKind.OBJECT;
        }
        DBPDataKind dataKind = DamengDataType.getDataKind(typeName);
        if (dataKind != null) {
            return dataKind;
        }
        return super.resolveDataKind(typeName, valueType);
    }

    @Override
    public Collection<? extends DBSDataType> getLocalDataTypes() {
        return dataTypeCache.getCachedObjects();
    }

    @Override
    public DamengDataType getLocalDataType(String typeName) {
        return dataTypeCache.getCachedObject(typeName);
    }

    public DataTypeCache getDataTypeCache() {
        return dataTypeCache;
    }

    public boolean hasPrivFlag() {
        return this.privFlag;
    }

    @Nullable
    @Override
    public DamengDataType resolveDataType(@NotNull DBRProgressMonitor monitor, @NotNull String typeFullName)
        throws DBException {
        int divPos = typeFullName.indexOf(SQLConstants.STRUCT_SEPARATOR);
        if (divPos == -1) {
            // Simple type name
            return getLocalDataType(typeFullName);
        } else {
            String schemaName = typeFullName.substring(0, divPos);
            String typeName = typeFullName.substring(divPos + 1);
            DamengSchema schema = getSchema(monitor, schemaName);
            if (schema == null) {
                return null;
            }
            return schema.getDataType(monitor, typeName);
        }
    }

    @Nullable
    public String getPlanTableName(JDBCSession session) throws DBException {
        if (planTableName == null) {
            String[] candidateNames;
            String tableName = getContainer().getPreferenceStore().getString(DamengConstants.PREF_EXPLAIN_TABLE_NAME);
            if (!CommonUtils.isEmpty(tableName)) {
                candidateNames = new String[] {tableName};
            } else {
                candidateNames = new String[] {"PLAN_TABLE", "TOAD_PLAN_TABLE"};
            }
            for (String candidate : candidateNames) {
                try {
                    JDBCUtils.executeSQL(session, "SELECT 1 FROM " + candidate);
                } catch (SQLException e) {
                    // No such table
                    continue;
                }
                planTableName = candidate;
                break;
            }
            if (planTableName == null) {
                final String newPlanTableName = candidateNames[0];
                // Plan table not found - try to create new one
                if (!DBWorkbench.getPlatformUI().confirmAction("Dm PLAN_TABLE missing",
                    "PLAN_TABLE not found in current user's session. "
                        + "Do you want DBeaver to create new PLAN_TABLE (" + newPlanTableName + ")?")) {
                    return null;
                }
                planTableName = createPlanTable(session, newPlanTableName);
            }
        }
        return planTableName;
    }

    private String createPlanTable(JDBCSession session, String tableName) throws DBException {
        try {
            JDBCUtils.executeSQL(session, DamengConstants.PLAN_TABLE_DEFINITION.replace("${TABLE_NAME}", tableName));
        } catch (SQLException e) {
            throw new DBDatabaseException("Error creating PLAN table", e, this);
        }
        return tableName;
    }

    @Nullable
    @Override
    public DBCQueryTransformer createQueryTransformer(@NotNull DBCQueryTransformType type) {
        if (type == DBCQueryTransformType.RESULT_SET_LIMIT) {
            // return new QueryTransformerRowNum();
        }
        return super.createQueryTransformer(type);
    }

    @Nullable
    @Override
    public ErrorPosition[] getErrorPosition(@NotNull DBRProgressMonitor monitor, @NotNull DBCExecutionContext context,
                                            @NotNull String query, @NotNull Throwable error) {
        while (error instanceof DBException) {
            if (error.getCause() == null) {
                break;
            }
            error = error.getCause();
        }
        String message = error.getMessage();
        if (!CommonUtils.isEmpty(message)) {
            List<ErrorPosition> positions = new ArrayList<>();
            Matcher matcher = ERROR_POSITION_PATTERN.matcher(message);
            while (matcher.find()) {
                DBPErrorAssistant.ErrorPosition pos = new DBPErrorAssistant.ErrorPosition();
                pos.info = matcher.group(1);
                pos.line = Integer.parseInt(matcher.group(1)) - 1;
                pos.position = Integer.parseInt(matcher.group(2)) - 1;
                positions.add(pos);
            }
            if (positions.isEmpty()) {
                matcher = ERROR_POSITION_PATTERN_2.matcher(message);
                while (matcher.find()) {
                    DBPErrorAssistant.ErrorPosition pos = new DBPErrorAssistant.ErrorPosition();
                    pos.info = matcher.group(1);
                    pos.line = Integer.parseInt(matcher.group(1)) - 1;
                    positions.add(pos);
                }
            }
            if (positions.isEmpty()) {
                matcher = ERROR_POSITION_PATTERN_3.matcher(message);
                while (matcher.find()) {
                    DBPErrorAssistant.ErrorPosition pos = new DBPErrorAssistant.ErrorPosition();
                    pos.info = matcher.group(1);
                    pos.position = Integer.parseInt(matcher.group(1)) - 1;
                    positions.add(pos);
                }
            }

            if (!positions.isEmpty()) {
                return positions.toArray(new ErrorPosition[positions.size()]);
            }
        }
        if (error.getCause() != null) {
            // Maybe DmDatabaseException
            try {
                Object errorPosition = BeanUtils.readObjectProperty(error.getCause(), "errorPosition");
                if (errorPosition instanceof Number) {
                    DBPErrorAssistant.ErrorPosition pos = new DBPErrorAssistant.ErrorPosition();
                    pos.position = ((Number) errorPosition).intValue();
                    return new ErrorPosition[] {pos};
                }
            } catch (Exception e) {
                // Nope, its not it
            }

        }

        return null;
    }

    @Override
    public boolean isStatisticsCollected() {
        return hasStatistics;
    }

    void resetStatistics() {
        hasStatistics = false;
    }

    @Override
    public void collectObjectStatistics(DBRProgressMonitor monitor, boolean totalSizeOnly, boolean forceRefresh)
        throws DBException {
        if (hasStatistics && !forceRefresh) {
            return;
        }
        try (final JDBCSession session = DBUtils.openMetaSession(monitor, this,
            "Load tablespace '" + getName() + "' statistics")) {
            // Tablespace stats
            try (JDBCStatement dbStat = session.createStatement()) {
                try (JDBCResultSet dbResult = dbStat.executeQuery("SELECT\n"
                    + "\tTS.TABLESPACE_NAME, F.AVAILABLE_SPACE, S.USED_SPACE\n" + "FROM\n"
                    + "\tSYS.DBA_TABLESPACES TS,\n"
                    + "\t(SELECT TABLESPACE_NAME, SUM(BYTES) AVAILABLE_SPACE FROM DBA_DATA_FILES GROUP BY TABLESPACE_NAME) F,\n"
                    + "\t(SELECT TABLESPACE_NAME, SUM(BYTES) USED_SPACE FROM DBA_SEGMENTS GROUP BY TABLESPACE_NAME) S\n"
                    + "WHERE\n"
                    + "\tF.TABLESPACE_NAME(+) = TS.TABLESPACE_NAME AND S.TABLESPACE_NAME(+) = TS.TABLESPACE_NAME")) {
                    while (dbResult.next()) {
                        String tsName = dbResult.getString(1);
                        DamengTablespace tablespace = tablespaceCache.getObject(monitor, DamengDataSource.this, tsName);
                        if (tablespace != null) {
                            tablespace.fetchSizes(dbResult);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new DBDatabaseException("Can't read tablespace statistics", e, getDataSource());
        } finally {
            hasStatistics = true;
        }
    }

    @NotNull
    @Override
    protected String getStandardSQLDataTypeName(@NotNull DBPDataKind dataKind) {
        switch (dataKind) {
            case BOOLEAN:
                return SQLConstants.DATA_TYPE_BOOLEAN;
            case NUMERIC:
                return DamengConstants.TYPE_NAME_NUMERIC;
            case DATETIME:
                return DamengConstants.TYPE_NAME_TIMESTAMP;
            case BINARY:
            case CONTENT:
                return DamengConstants.TYPE_NAME_BLOB;
            case ROWID:
                return DamengConstants.TYPE_NAME_ROWID;
            default:
                return DamengConstants.TYPE_NAME_VARCHAR2;
        }
    }

    static class SchemaCache extends JDBCObjectCache<DamengDataSource, DamengSchema> {
        SchemaCache() {
            setListOrderComparator(DBUtils.<DamengSchema>nameComparator());
        }

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengDataSource owner)
            throws SQLException {
            StringBuilder schemasQuery = new StringBuilder();
            DBPConnectionConfiguration configuration = owner.getContainer().getConnectionConfiguration();
            boolean showOnlyOneSchema = CommonUtils
                .toBoolean(configuration.getProviderProperty(DamengConstants.PROP_SHOW_ONLY_ONE_SCHEMA));
            // PROP_CHECK_SCHEMA_CONTENT set to true when option "Hide empty
            // schemas" is set
            boolean showAllSchemas = !showOnlyOneSchema && !CommonUtils
                .toBoolean(configuration.getProviderProperty(DamengConstants.PROP_CHECK_SCHEMA_CONTENT));
            schemasQuery.append("SELECT OBJECT_NAME AS USERNAME, OBJECT_ID AS USER_ID, CREATED FROM ")
                .append(DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(), owner, "OBJECTS \n"));

            schemasQuery.append("WHERE ( OBJECT_TYPE = 'SCH' AND ");
            if (showOnlyOneSchema) {
                schemasQuery.append("(OBJECT_NAME) = ?");
            } else if (showAllSchemas) {
                schemasQuery.append("OBJECT_NAME IS NOT NULL");
            } else {
                schemasQuery.append("OBJECT_NAME IN (SELECT DISTINCT OWNER FROM ")
                    .append(DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(), owner, "OBJECTS"))
                    .append(")");
            }
//                }

            DBSObjectFilter schemaFilters = owner.getContainer().getObjectFilter(DamengSchema.class, null, false);
            if (!showOnlyOneSchema && schemaFilters != null) {
                JDBCUtils.appendFilterClause(schemasQuery, schemaFilters, "OBJECT_NAME", false, owner);
            }
            schemasQuery.append(")");

            JDBCPreparedStatement dbStat = session.prepareStatement(schemasQuery.toString());

            if (showOnlyOneSchema) {
                dbStat.setString(1,
                    DBUtils.getUnQuotedIdentifier(owner, configuration.getUserName().toUpperCase(Locale.ENGLISH)));
            } else if (schemaFilters != null) {
                JDBCUtils.setFilterParameters(dbStat, 1, schemaFilters);
            }
            return dbStat;
        }

        @Override
        protected DamengSchema fetchObject(@NotNull JDBCSession session, @NotNull DamengDataSource owner,
                                           @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengSchema(owner, resultSet);
        }

        @Override
        protected void invalidateObjects(DBRProgressMonitor monitor, DamengDataSource owner,
                                         Iterator<DamengSchema> objectIter) {
            setListOrderComparator(DBUtils.<DamengSchema>nameComparator());
        }
    }

    static class DataTypeCache extends JDBCObjectCache<DamengDataSource, DamengDataType> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengDataSource owner)
            throws SQLException {
            return session.prepareStatement("SELECT " + DamengUtils.getSysCatalogHint(owner) + " * FROM "
                + DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(), owner, "TYPES")
                + " WHERE OWNER IS NULL ORDER BY TYPE_NAME");
        }

        @Override
        protected DamengDataType fetchObject(@NotNull JDBCSession session, @NotNull DamengDataSource owner,
                                             @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengDataType(owner, resultSet);
        }
    }

    static class TablespaceCache extends JDBCObjectCache<DamengDataSource, DamengTablespace> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengDataSource owner)
            throws SQLException {
            return session.prepareStatement("SELECT * FROM "
                + DamengUtils.getSysUserViewName(session.getProgressMonitor(), owner, "TABLESPACES")
                + " where BLOCK_SIZE is not null ORDER BY TABLESPACE_NAME");
        }

        @Override
        protected DamengTablespace fetchObject(@NotNull JDBCSession session, @NotNull DamengDataSource owner,
                                               @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengTablespace(owner, resultSet);
        }
    }

    static class UserCache extends JDBCObjectCache<DamengDataSource, DamengUser> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengDataSource owner)
            throws SQLException {
            return session.prepareStatement(
                "select distinct USR_OBJ.ID, USR_OBJ.NAME, PARA_VALUE, PWD_POLICY, (select top 1 BIN_VALUE from SYSOBJINFOS where ID = USR_OBJ.ID) ENCRYPT_KEY,(select NAME from SYS.V$TABLESPACE where ID in (select INFO3 & 0x000000000000FFFF from SYSOBJECTS where ID = USR_OBJ.ID) and ID != 0  and true) TABLE_SPACE,\r\n"
                    +
                    " USR_OBJ.INFO1, USR_OBJ.CRTDATE, USR.LOCKED_STATUS from (select ID, NAME, INFO1, CRTDATE from SYSOBJECTS where TYPE$='UR' and SUBTYPE$='USER') USR_OBJ, (select ID, LOCKED_STATUS from SYS.SYSUSERS) USR, SYS.SYSUSERS, SYSRESOURCES AS RSCS, V$DM_INI\r\n"
                    +
                    " where USR_OBJ.ID = USR.ID AND SYS.SYSUSERS.ID = RSCS.ID and SYS.SYSUSERS.ID = USR_OBJ.ID and PARA_NAME = 'PWD_MIN_LEN' ORDER BY INFO1,NAME ");
        }

        @Override
        protected DamengUser fetchObject(@NotNull JDBCSession session, @NotNull DamengDataSource owner,
                                         @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengUser(owner, resultSet);
        }
    }

    static class UserDbaCache extends JDBCObjectCache<DamengDataSource, DamengUser> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengDataSource owner)
            throws SQLException {
            return session.prepareStatement(
                "select distinct USR_OBJ.ID, USR_OBJ.NAME, PARA_VALUE, PWD_POLICY, (select top 1 BIN_VALUE from SYSOBJINFOS where ID = USR_OBJ.ID) ENCRYPT_KEY,(select NAME from SYS.V$TABLESPACE where ID in (select INFO3 & 0x000000000000FFFF from SYSOBJECTS where ID = USR_OBJ.ID) and ID != 0  and true) TABLE_SPACE,\r\n"
                    +
                    " USR_OBJ.INFO1, USR_OBJ.CRTDATE, USR.LOCKED_STATUS from (select ID, NAME, INFO1, CRTDATE from SYSOBJECTS where TYPE$='UR' and SUBTYPE$='USER'  and INFO1=0) USR_OBJ, (select ID, LOCKED_STATUS from SYS.SYSUSERS) USR, SYS.SYSUSERS, SYSRESOURCES AS RSCS, V$DM_INI\r\n"
                    +
                    " where USR_OBJ.ID = USR.ID AND SYS.SYSUSERS.ID = RSCS.ID and SYS.SYSUSERS.ID = USR_OBJ.ID and PARA_NAME = 'PWD_MIN_LEN' ORDER BY INFO1,NAME ");
        }

        @Override
        protected DamengUser fetchObject(@NotNull JDBCSession session, @NotNull DamengDataSource owner,
                                         @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengUser(owner, resultSet);
        }

    }

    static class UserAuditCache extends JDBCObjectCache<DamengDataSource, DamengUser> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengDataSource owner)
            throws SQLException {
            return session.prepareStatement(
                "select distinct USR_OBJ.ID, USR_OBJ.NAME, PARA_VALUE, PWD_POLICY, (select top 1 BIN_VALUE from SYSOBJINFOS where ID = USR_OBJ.ID) ENCRYPT_KEY,(select NAME from SYS.V$TABLESPACE where ID in (select INFO3 & 0x000000000000FFFF from SYSOBJECTS where ID = USR_OBJ.ID) and ID != 0  and true) TABLE_SPACE,\r\n"
                    +
                    " USR_OBJ.INFO1, USR_OBJ.CRTDATE, USR.LOCKED_STATUS from (select ID, NAME, INFO1, CRTDATE from SYSOBJECTS where TYPE$='UR' and SUBTYPE$='USER'  and INFO1=1) USR_OBJ, (select ID, LOCKED_STATUS from SYS.SYSUSERS) USR, SYS.SYSUSERS, SYSRESOURCES AS RSCS, V$DM_INI\r\n"
                    +
                    " where USR_OBJ.ID = USR.ID AND SYS.SYSUSERS.ID = RSCS.ID and SYS.SYSUSERS.ID = USR_OBJ.ID and PARA_NAME = 'PWD_MIN_LEN' ORDER BY INFO1,NAME ");
        }

        @Override
        protected DamengUser fetchObject(@NotNull JDBCSession session, @NotNull DamengDataSource owner,
                                         @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengUser(owner, resultSet);
        }
    }

    static class UserPolicyCache extends JDBCObjectCache<DamengDataSource, DamengUser> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengDataSource owner)
            throws SQLException {
            return session.prepareStatement(
                "select distinct USR_OBJ.ID, USR_OBJ.NAME, PARA_VALUE, PWD_POLICY, (select top 1 BIN_VALUE from SYSOBJINFOS where ID = USR_OBJ.ID) ENCRYPT_KEY,(select NAME from SYS.V$TABLESPACE where ID in (select INFO3 & 0x000000000000FFFF from SYSOBJECTS where ID = USR_OBJ.ID) and ID != 0  and true) TABLE_SPACE,\r\n"
                    +
                    " USR_OBJ.INFO1, USR_OBJ.CRTDATE, USR.LOCKED_STATUS from (select ID, NAME, INFO1, CRTDATE from SYSOBJECTS where TYPE$='UR' and SUBTYPE$='USER'  and INFO1=2) USR_OBJ, (select ID, LOCKED_STATUS from SYS.SYSUSERS) USR, SYS.SYSUSERS, SYSRESOURCES AS RSCS, V$DM_INI\r\n"
                    +
                    " where USR_OBJ.ID = USR.ID AND SYS.SYSUSERS.ID = RSCS.ID and SYS.SYSUSERS.ID = USR_OBJ.ID and PARA_NAME = 'PWD_MIN_LEN' ORDER BY INFO1,NAME ");
        }

        @Override
        protected DamengUser fetchObject(@NotNull JDBCSession session, @NotNull DamengDataSource owner,
                                         @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengUser(owner, resultSet);
        }
    }

    static class UserDboCache extends JDBCObjectCache<DamengDataSource, DamengUser> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengDataSource owner)
            throws SQLException {
            return session.prepareStatement(
                "select distinct USR_OBJ.ID, USR_OBJ.NAME, PARA_VALUE, PWD_POLICY, (select top 1 BIN_VALUE from SYSOBJINFOS where ID = USR_OBJ.ID) ENCRYPT_KEY,(select NAME from SYS.V$TABLESPACE where ID in (select INFO3 & 0x000000000000FFFF from SYSOBJECTS where ID = USR_OBJ.ID) and ID != 0  and true) TABLE_SPACE,\r\n"
                    +
                    " USR_OBJ.INFO1, USR_OBJ.CRTDATE, USR.LOCKED_STATUS from (select ID, NAME, INFO1, CRTDATE from SYSOBJECTS where TYPE$='UR' and SUBTYPE$='USER'  and INFO1=3) USR_OBJ, (select ID, LOCKED_STATUS from SYS.SYSUSERS) USR, SYS.SYSUSERS, SYSRESOURCES AS RSCS, V$DM_INI\r\n"
                    +
                    " where USR_OBJ.ID = USR.ID AND SYS.SYSUSERS.ID = RSCS.ID and SYS.SYSUSERS.ID = USR_OBJ.ID and PARA_NAME = 'PWD_MIN_LEN' ORDER BY INFO1,NAME ");
        }

        @Override
        protected DamengUser fetchObject(@NotNull JDBCSession session, @NotNull DamengDataSource owner,
                                         @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengUser(owner, resultSet);
        }
    }

    static class UserSysCache extends JDBCObjectCache<DamengDataSource, DamengUser> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengDataSource owner)
            throws SQLException {
            return session.prepareStatement(
                "select distinct USR_OBJ.ID, USR_OBJ.NAME, PARA_VALUE, PWD_POLICY, (select top 1 BIN_VALUE from SYSOBJINFOS where ID = USR_OBJ.ID) ENCRYPT_KEY,(select NAME from SYS.V$TABLESPACE where ID in (select INFO3 & 0x000000000000FFFF from SYSOBJECTS where ID = USR_OBJ.ID) and ID != 0  and true) TABLE_SPACE,\r\n"
                    +
                    " USR_OBJ.INFO1, USR_OBJ.CRTDATE, USR.LOCKED_STATUS from (select ID, NAME, INFO1, CRTDATE from SYSOBJECTS where TYPE$='UR' and SUBTYPE$='USER'  and INFO1=4) USR_OBJ, (select ID, LOCKED_STATUS from SYS.SYSUSERS) USR, SYS.SYSUSERS, SYSRESOURCES AS RSCS, V$DM_INI\r\n"
                    +
                    " where USR_OBJ.ID = USR.ID AND SYS.SYSUSERS.ID = RSCS.ID and SYS.SYSUSERS.ID = USR_OBJ.ID and PARA_NAME = 'PWD_MIN_LEN' ORDER BY INFO1,NAME ");
        }

        @Override
        protected DamengUser fetchObject(@NotNull JDBCSession session, @NotNull DamengDataSource owner,
                                         @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengUser(owner, resultSet);
        }
    }

    static class RoleCache extends JDBCObjectCache<DamengDataSource, DamengRole> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengDataSource owner)
            throws SQLException {
            return session.prepareStatement(
                "select id, name, info1, valid, crtdate from sysobjects where type$='UR' and subtype$='ROLE' and (info2 is null or info2!=1) and info1 = "
                    + owner.getUsertype() + " ORDER BY NAME");
        }

        @Override
        protected DamengRole fetchObject(@NotNull JDBCSession session, @NotNull DamengDataSource owner,
                                         @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengRole(owner, resultSet);
        }
    }

    static class ProfileCache
        extends JDBCStructCache<DamengDataSource, DamengUserProfile, DamengUserProfile.ProfileResource> {
        protected ProfileCache() {
            super("PROFILE");
        }

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengDataSource owner)
            throws SQLException {
            return session.prepareStatement("SELECT DISTINCT PROFILE FROM DBA_PROFILES ORDER BY PROFILE");
        }

        @Override
        protected DamengUserProfile fetchObject(@NotNull JDBCSession session, @NotNull DamengDataSource owner,
                                                @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengUserProfile(owner, resultSet);
        }

        @Override
        protected JDBCStatement prepareChildrenStatement(@NotNull JDBCSession session,
                                                         @NotNull DamengDataSource dataSource, @Nullable DamengUserProfile forObject)
            throws SQLException {
            final JDBCPreparedStatement dbStat = session
                .prepareStatement("SELECT RESOURCE_NAME,RESOURCE_TYPE,LIMIT FROM DBA_PROFILES "
                    + (forObject == null ? "" : "WHERE PROFILE=? ") + "ORDER BY RESOURCE_NAME");
            if (forObject != null) {
                dbStat.setString(1, forObject.getName());
            }
            return dbStat;
        }

        @Override
        protected DamengUserProfile.ProfileResource fetchChild(@NotNull JDBCSession session,
                                                               @NotNull DamengDataSource dataSource, @NotNull DamengUserProfile parent,
                                                               @NotNull JDBCResultSet dbResult) throws SQLException, DBException {
            return new DamengUserProfile.ProfileResource(parent, dbResult);
        }
    }

    private class DmOutputReader implements DBCServerOutputReader {
        @Override
        public boolean isServerOutputEnabled() {
            return getContainer().getPreferenceStore().getBoolean(DamengConstants.PREF_DBMS_OUTPUT);
        }

        @Override
        public boolean isAsyncOutputReadSupported() {
            return false;
        }

        public void enableServerOutput(DBRProgressMonitor monitor, DBCExecutionContext context, boolean enable)
            throws DBCException {
            String sql = enable ? "BEGIN DBMS_OUTPUT.ENABLE(" + DamengConstants.MAXIMUM_DBMS_OUTPUT_SIZE + "); END;"
                : "BEGIN DBMS_OUTPUT.DISABLE; END;";
            try (DBCSession session = context.openSession(monitor, DBCExecutionPurpose.UTIL,
                (enable ? "Enable" : "Disable ") + "DBMS output")) {
                JDBCUtils.executeSQL((JDBCSession) session, sql);
            } catch (SQLException e) {
                throw new DBCException(e, context);
            }
        }

        @Override
        public void readServerOutput(@NotNull DBRProgressMonitor monitor, @NotNull DBCExecutionContext context,
                                     @Nullable DBCExecutionResult executionResult, @Nullable DBCStatement statement,
                                     @NotNull DBCOutputWriter output) throws DBCException {
            try (JDBCSession session = (JDBCSession) context.openSession(monitor, DBCExecutionPurpose.UTIL,
                "Read DBMS output")) {
                try (CallableStatement getLineProc = session.getOriginal()
                    .prepareCall("{CALL DBMS_OUTPUT.GET_LINE(?, ?)}")) {
                    getLineProc.registerOutParameter(1, java.sql.Types.VARCHAR);
                    getLineProc.registerOutParameter(2, java.sql.Types.INTEGER);
                    int status = 0;
                    while (status == 0) {
                        getLineProc.execute();
                        status = getLineProc.getInt(2);
                        if (status == 0) {
                            output.println(null, getLineProc.getString(1));
                        }
                    }
                } catch (SQLException e) {
                    throw new DBCException(e, context);
                }
            }
        }
    }
    
}
