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
package org.jkiss.dbeaver.model.exec;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBDatabaseException;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ModelPreferences;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.connection.DBPConnectionType;
import org.jkiss.dbeaver.model.data.*;
import org.jkiss.dbeaver.model.edit.DBECommand;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.impl.DBObjectNameCaseTransformer;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistAction;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistActionComment;
import org.jkiss.dbeaver.model.messages.ModelMessages;
import org.jkiss.dbeaver.model.net.DBWForwarder;
import org.jkiss.dbeaver.model.net.DBWHandlerConfiguration;
import org.jkiss.dbeaver.model.net.DBWHandlerType;
import org.jkiss.dbeaver.model.net.DBWNetworkHandler;
import org.jkiss.dbeaver.model.preferences.DBPPreferenceStore;
import org.jkiss.dbeaver.model.qm.QMUtils;
import org.jkiss.dbeaver.model.qm.meta.QMMConnectionInfo;
import org.jkiss.dbeaver.model.qm.meta.QMMStatementExecuteInfo;
import org.jkiss.dbeaver.model.runtime.*;
import org.jkiss.dbeaver.model.sql.SQLDialect;
import org.jkiss.dbeaver.model.sql.SQLQuery;
import org.jkiss.dbeaver.model.sql.SQLSelectItem;
import org.jkiss.dbeaver.model.sql.SQLUtils;
import org.jkiss.dbeaver.model.struct.*;
import org.jkiss.dbeaver.model.struct.rdb.DBSCatalog;
import org.jkiss.dbeaver.model.struct.rdb.DBSSchema;
import org.jkiss.dbeaver.model.struct.rdb.DBSTable;
import org.jkiss.dbeaver.model.struct.rdb.DBSTableIndex;
import org.jkiss.dbeaver.model.virtual.DBVEntity;
import org.jkiss.dbeaver.model.virtual.DBVEntityConstraint;
import org.jkiss.dbeaver.model.virtual.DBVUtils;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.runtime.jobs.DefaultInvalidationFeedbackHandler;
import org.jkiss.dbeaver.runtime.jobs.InvalidateJob;
import org.jkiss.dbeaver.runtime.net.GlobalProxyAuthenticator;
import org.jkiss.utils.CommonUtils;

import java.lang.reflect.InvocationTargetException;
import java.net.Authenticator;
import java.util.*;

/**
 * Execution utils
 */
public class DBExecUtils {

    public static final int DEFAULT_READ_FETCH_SIZE = 10000;

    private static final Log log = Log.getLog(DBExecUtils.class);

    /**
     * Current execution context. Used by global authenticators and network handlers
     */
    private static final ThreadLocal<DBPDataSourceContainer> ACTIVE_CONTEXT = new ThreadLocal<>();
    private static final List<DBPDataSourceContainer> ACTIVE_CONTEXTS = new ArrayList<>();
    public static final boolean BROWSE_LAZY_ASSOCIATIONS = false;
    private static final ThreadLocal<RecoveryState> recoveryStack = new ThreadLocal<>();

    private static class RecoveryState {
        int recoveryDepth;
        boolean recoveryFailed;
    }

    public static DBPDataSourceContainer getCurrentThreadContext() {
        return ACTIVE_CONTEXT.get();
    }

    public static List<DBPDataSourceContainer> getActiveContexts() {
        synchronized (ACTIVE_CONTEXTS) {
            return new ArrayList<>(ACTIVE_CONTEXTS);
        }
    }

    public static void startContextInitiation(DBPDataSourceContainer context) {
        ACTIVE_CONTEXT.set(context);
        synchronized (ACTIVE_CONTEXTS) {
            ACTIVE_CONTEXTS.add(context);
        }
        // Set proxy auth (if required)
        // Note: authenticator may be changed by Eclipse framework on startup or later.
        // That's why we set new default authenticator on connection initiation
        boolean hasProxy = false;
        for (DBWHandlerConfiguration handler : context.getConnectionConfiguration().getHandlers()) {
            if (handler.isEnabled() && handler.getType() == DBWHandlerType.PROXY) {
                hasProxy = true;
                break;
            }
        }
        if (hasProxy) {
            Authenticator.setDefault(new GlobalProxyAuthenticator());
        }
    }

    public static void finishContextInitiation(DBPDataSourceContainer context) {
        ACTIVE_CONTEXT.remove();
        synchronized (ACTIVE_CONTEXTS) {
            ACTIVE_CONTEXTS.remove(context);
        }
    }

    public static DBPDataSourceContainer findConnectionContext(String host, int port, String path) {
        DBPDataSourceContainer curContext = getCurrentThreadContext();
        if (curContext != null) {
            return contextMatches(host, port, curContext) ? curContext : null;
        }
        synchronized (ACTIVE_CONTEXTS) {
            for (DBPDataSourceContainer ctx : ACTIVE_CONTEXTS) {
                if (contextMatches(host, port, ctx)) {
                    return ctx;
                }
            }
        }
        return null;
    }

    private static boolean contextMatches(String host, int port, DBPDataSourceContainer ctx) {
        DBPConnectionConfiguration cfg = ctx.getConnectionConfiguration();
        if (CommonUtils.equalObjects(cfg.getHostName(), host) && String.valueOf(port).equals(cfg.getHostPort())) {
            return true;
        }
        for (DBWNetworkHandler networkHandler : ctx.getActiveNetworkHandlers()) {
            if (networkHandler instanceof DBWForwarder && ((DBWForwarder) networkHandler).matchesParameters(host, port)) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    public static DBPErrorAssistant.ErrorType discoverErrorType(@NotNull DBPDataSource dataSource, @NotNull Throwable error) {
        DBPErrorAssistant errorAssistant = DBUtils.getAdapter(DBPErrorAssistant.class, dataSource);
        if (errorAssistant != null) {
            return ((DBPErrorAssistant) dataSource).discoverErrorType(error);
        }

        return DBPErrorAssistant.ErrorType.NORMAL;
    }

    /**
     * @param param DBRProgressProgress monitor or DBCSession
     *
     */
    public static <T> boolean tryExecuteRecover(@NotNull T param, @NotNull DBPDataSource dataSource, @NotNull DBRRunnableParametrized<T> runnable) throws DBException {
        RecoveryState recoveryState = DBExecUtils.recoveryStack.get();
        if (recoveryState == null) {
            recoveryState = new RecoveryState();
            DBExecUtils.recoveryStack.set(recoveryState);
        }

        try {
            recoveryState.recoveryDepth++;

            int tryCount = 1;
            boolean recoverEnabled = !recoveryState.recoveryFailed &&
                dataSource.getContainer().getPreferenceStore().getBoolean(ModelPreferences.EXECUTE_RECOVER_ENABLED);
            if (recoverEnabled) {
                tryCount += dataSource.getContainer().getPreferenceStore().getInt(ModelPreferences.EXECUTE_RECOVER_RETRY_COUNT);
            }
            Throwable lastError = null;
            for (int i = 0; i < tryCount; i++) {
                try {
                    runnable.run(param);
                    lastError = null;
                    break;
                } catch (InvocationTargetException e) {
                    lastError = e.getTargetException();
                    if (!recoverEnabled || recoveryState.recoveryFailed) {
                        // Can't recover
                        break;
                    }
                    DBPErrorAssistant.ErrorType errorType = discoverErrorType(dataSource, lastError);
                    if (errorType != DBPErrorAssistant.ErrorType.TRANSACTION_ABORTED && errorType != DBPErrorAssistant.ErrorType.CONNECTION_LOST) {
                        // Some other error
                        break;
                    }
                    DBRProgressMonitor monitor;
                    if (param instanceof DBRProgressMonitor) {
                        monitor = (DBRProgressMonitor) param;
                    } else if (param instanceof DBCSession) {
                        monitor = ((DBCSession) param).getProgressMonitor();
                    } else {
                        monitor = new VoidProgressMonitor();
                    }
                    if (!monitor.isCanceled()) {

                        if (errorType == DBPErrorAssistant.ErrorType.TRANSACTION_ABORTED) {
                            // Transaction aborted
                            DBCExecutionContext executionContext = null;
                            if (lastError instanceof DBCException) {
                                executionContext = ((DBCException) lastError).getExecutionContext();
                            }
                            if (executionContext != null) {
                                log.debug("Invalidate context [" + executionContext.getDataSource().getContainer().getName() + "/" + executionContext.getContextName() + "] transactions");
                            } else {
                                log.debug("Invalidate datasource [" + dataSource.getContainer().getName() + "] transactions");
                            }
                            InvalidateJob.invalidateTransaction(monitor, dataSource, executionContext);
                        } else {
                            // Do not recover if connection was canceled
                            log.debug("Invalidate datasource '" + dataSource.getContainer().getName() + "' connections...");
                            InvalidateJob.invalidateDataSource(
                                monitor,
                                dataSource,
                                false,
                                true,
                                new DefaultInvalidationFeedbackHandler()
                            );
                            if (i < tryCount - 1) {
                                log.error("Operation failed. Retry count remains = " + (tryCount - i - 1), lastError);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    log.error("Operation interrupted");
                    return false;
                }
            }
            if (lastError != null) {
                recoveryState.recoveryFailed = true;
                if (lastError instanceof DBException dbe) {
                    throw dbe;
                } else {
                    throw new DBDatabaseException(lastError, dataSource);
                }
            }
            return true;
        } finally {
            recoveryState.recoveryDepth--;
            if (recoveryState.recoveryDepth == 0) {
                recoveryStack.set(null);
            }
        }
    }

    public static void setStatementFetchSize(DBCStatement dbStat, long firstRow, long maxRows, int fetchSize) {
        boolean useFetchSize = fetchSize > 0 || dbStat.getSession().getDataSource().getContainer().getPreferenceStore().getBoolean(ModelPreferences.RESULT_SET_USE_FETCH_SIZE);
        if (useFetchSize) {
            if (fetchSize <= 0) {
                fetchSize = DEFAULT_READ_FETCH_SIZE;
            }
            try {
                dbStat.setResultsFetchSize(
                    firstRow < 0 || maxRows <= 0 ? fetchSize : (int) (firstRow + maxRows));
            } catch (Exception e) {
                log.warn(e);
            }
        }
    }

    public static void executeScript(DBRProgressMonitor monitor, DBCExecutionContext executionContext, String jobName, List<DBEPersistAction> persistActions) {
        try (DBCSession session = executionContext.openSession(monitor, DBCExecutionPurpose.UTIL, jobName)) {
            executeScript(session, persistActions.toArray(new DBEPersistAction[0]));
        }
    }

    public static void executeScript(DBCSession session, DBEPersistAction[] persistActions) {
        DBRProgressMonitor monitor = session.getProgressMonitor();
        boolean ignoreErrors = false;
        monitor.beginTask(session.getTaskTitle(), persistActions.length);
        try {
            for (DBEPersistAction action : persistActions) {
                if (monitor.isCanceled()) {
                    break;
                }
                if (!CommonUtils.isEmpty(action.getTitle())) {
                    monitor.subTask(action.getTitle());
                }
                try {
                    executePersistAction(session, action);
                } catch (Exception e) {
                    log.debug("Error executing query", e);
                    if (ignoreErrors) {
                        continue;
                    }
                    boolean keepRunning = true;
                    switch (DBWorkbench.getPlatformUI().showErrorStopRetryIgnore(session.getTaskTitle(), e, true)) {
                        case STOP:
                            keepRunning = false;
                            break;
                        case RETRY:
                            // just make it again
                            continue;
                        case IGNORE:
                            // Just do nothing
                            break;
                        case IGNORE_ALL:
                            ignoreErrors = true;
                            break;
                    }
                    if (!keepRunning) {
                        break;
                    }
                } finally {
                    monitor.worked(1);
                }
            }
        } finally {
            monitor.done();
        }
    }

    public static void executePersistActions(DBCSession session, DBEPersistAction[] persistActions) throws DBCException {
        DBRProgressMonitor monitor = session.getProgressMonitor();
        monitor.beginTask(session.getTaskTitle(), persistActions.length);
        try {
            for (DBEPersistAction action : persistActions) {
                if (monitor.isCanceled()) {
                    break;
                }
                if (!CommonUtils.isEmpty(action.getTitle())) {
                    monitor.subTask(action.getTitle());
                }
                executePersistAction(session, action);
            }
        } finally {
            monitor.done();
        }
    }

    public static void executePersistAction(DBCSession session, DBEPersistAction action) throws DBCException {
        if (action instanceof SQLDatabasePersistActionComment) {
            return;
        }
        String script = action.getScript();
        if (script == null) {
            action.afterExecute(session, null);
        } else {
            DBCStatement dbStat = DBUtils.createStatement(session, script, false);
            try {
                action.beforeExecute(session);
                dbStat.executeStatement();
                if (action instanceof SQLDatabasePersistAction) {
                    ((SQLDatabasePersistAction) action).afterExecute(session, dbStat, null);
                } else {
                    action.afterExecute(session, null);
                }
            } catch (DBCException e) {
                action.afterExecute(session, e);
                throw e;
            } finally {
                dbStat.close();
            }
        }
    }

    public static boolean checkSmartAutoCommit(DBCSession session, String queryText) {
        DBCTransactionManager txnManager = DBUtils.getTransactionManager(session.getExecutionContext());
        if (txnManager != null) {
            try {
                if (!txnManager.isAutoCommit()) {
                    return false;
                }

                SQLDialect sqlDialect = SQLUtils.getDialectFromDataSource(session.getDataSource());
                if (!sqlDialect.isTransactionModifyingQuery(queryText)) {
                    return false;
                }

                if (txnManager.isAutoCommit()) {
                    txnManager.setAutoCommit(session.getProgressMonitor(), false);
                    return true;
                }
            } catch (DBCException e) {
                log.warn(e);
            }
        }
        return false;
    }

    public static void setExecutionContextDefaults(DBRProgressMonitor monitor, DBPDataSource dataSource, DBCExecutionContext executionContext, @Nullable String newInstanceName, @Nullable String curInstanceName, @Nullable String newObjectName) throws DBException {
        DBSObjectContainer rootContainer = DBUtils.getAdapter(DBSObjectContainer.class, dataSource);
        if (rootContainer == null) {
            return;
        }

        DBCExecutionContextDefaults contextDefaults = null;
        if (executionContext != null) {
            contextDefaults = executionContext.getContextDefaults();
        }
        if (contextDefaults != null && (contextDefaults.supportsSchemaChange() || contextDefaults.supportsCatalogChange())) {
            changeDefaultObject(monitor, rootContainer, contextDefaults, newInstanceName, curInstanceName, newObjectName);
        }
    }

    @SuppressWarnings("unchecked")
    public static void changeDefaultObject(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DBSObjectContainer rootContainer,
        @NotNull DBCExecutionContextDefaults contextDefaults,
        @Nullable String newCatalogName,
        @Nullable String curCatalogName,
        @Nullable String newObjectName) throws DBException
    {
        DBSCatalog newCatalog = null;
        DBSSchema newSchema = null;

        if (newCatalogName != null) {
            DBSObject newInstance = rootContainer.getChild(monitor, newCatalogName);
            if (newInstance instanceof DBSCatalog) {
                newCatalog = (DBSCatalog) newInstance;
            }
        }
        DBSObject newObject;
        if (newObjectName != null) {
            if (newCatalog == null) {
                newObject = rootContainer.getChild(monitor, newObjectName);
            } else {
                newObject = newCatalog.getChild(monitor, newObjectName);
            }
            if (newObject instanceof DBSSchema) {
                newSchema = (DBSSchema) newObject;
            } else if (newObject instanceof DBSCatalog) {
                newCatalog = (DBSCatalog) newObject;
            }
        }

        boolean changeCatalog = (curCatalogName != null ? !CommonUtils.equalObjects(curCatalogName, newCatalogName) : newCatalog != null);

        if (newCatalog != null && newSchema != null && changeCatalog) {
            contextDefaults.setDefaultCatalog(monitor, newCatalog, contextDefaults.supportsSchemaChange() ? newSchema : null);
        } else if (newSchema != null && contextDefaults.supportsSchemaChange()) {
            contextDefaults.setDefaultSchema(monitor, newSchema);
        } else if (newCatalog != null && changeCatalog) {
            contextDefaults.setDefaultCatalog(monitor, newCatalog, null);
        }
    }

    public static void recoverSmartCommit(DBCExecutionContext executionContext) {
        DBPDataSourceContainer container = executionContext.getDataSource().getContainer();
        DBPPreferenceStore preferenceStore = container.getPreferenceStore();
        DBPConnectionType connectionType = container.getConnectionConfiguration().getConnectionType();
        // First check specific datasource settings
        // Or use settings from the connection type
        boolean isSmartCommitEnable = preferenceStore.contains(ModelPreferences.TRANSACTIONS_SMART_COMMIT) ?
            preferenceStore.getBoolean(ModelPreferences.TRANSACTIONS_SMART_COMMIT) : connectionType.isSmartCommit();
        if (!isSmartCommitEnable) {
            return;
        }
        boolean isRecoverSmartCommitEnable = preferenceStore.contains(ModelPreferences.TRANSACTIONS_SMART_COMMIT_RECOVER) ?
            preferenceStore.getBoolean(ModelPreferences.TRANSACTIONS_SMART_COMMIT_RECOVER)
            : connectionType.isSmartCommitRecover();
        if (isRecoverSmartCommitEnable) {
            DBCTransactionManager transactionManager = DBUtils.getTransactionManager(executionContext);
            if (transactionManager != null) {
                new AbstractJob("Recover smart commit mode") {
                    @Override
                    protected IStatus run(DBRProgressMonitor monitor) {
                        if (!executionContext.isConnected()) {
                            return Status.OK_STATUS;
                        }
                        try {
                            monitor.beginTask("Switch to auto-commit mode", 1);
                            if (!transactionManager.isAutoCommit()) {
                                transactionManager.setAutoCommit(monitor,true);
                            }
                        } catch (DBCException e) {
                            log.debug("Error recovering smart commit mode: " + e.getMessage());
                        }
                        finally {
                            monitor.done();
                        }
                        return Status.OK_STATUS;
                    }
                }.schedule();
            }
        }
    }

    public static DBSEntityConstraint getBestIdentifier(@Nullable DBRProgressMonitor monitor, @NotNull DBSEntity table, DBDAttributeBinding[] bindings)
        throws DBException
    {
        if (table instanceof DBSDocumentContainer) {
            return new DBSDocumentConstraint((DBSDocumentContainer) table);
        }
        List<DBSEntityConstraint> identifiers = new ArrayList<>(2);
        //List<DBSEntityConstraint> nonIdentifyingConstraints = null;

        {
            if (table instanceof DBSTable && ((DBSTable) table).isView()) {
                // Skip physical identifiers for views. There are nothing anyway

            } else {
                // Check indexes first.
                if (table instanceof DBSTable) {
                    try {
                        Collection<? extends DBSTableIndex> indexes = ((DBSTable) table).getIndexes(monitor);
                        if (!CommonUtils.isEmpty(indexes)) {
                            // First search for primary index
                            for (DBSTableIndex index : indexes) {
                                if (index.isPrimary() && DBUtils.isIdentifierIndex(monitor, index)) {
                                    identifiers.add(index);
                                    break;
                                }
                            }
                            // Then search for unique index
                            for (DBSTableIndex index : indexes) {
                                if (DBUtils.isIdentifierIndex(monitor, index) && !identifiers.contains(index)) {
                                    identifiers.add(index);
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Indexes are not supported or not available
                        // Just skip them
                        log.debug("Error reading table indexes: " + e.getMessage());
                    }
                }
                try {
                    // Check constraints
                    Collection<? extends DBSEntityConstraint> constraints = table.getConstraints(monitor);
                    if (constraints != null) {
                        for (DBSEntityConstraint constraint : constraints) {
                            if (DBUtils.isIdentifierConstraint(monitor, constraint)) {
                                identifiers.add(constraint);
                            }/* else {
                                if (nonIdentifyingConstraints == null) nonIdentifyingConstraints = new ArrayList<>();
                                nonIdentifyingConstraints.add(constraint);
                            }*/
                        }
                    }
                } catch (Exception e) {
                    log.debug("Error reading table constraints: " + e.getMessage());
                }

            }
        }

        if (!CommonUtils.isEmpty(identifiers)) {
            // Find PK or unique key
            DBSEntityConstraint uniqueId = null;
            for (DBSEntityConstraint constraint : identifiers) {
                if (constraint instanceof DBSEntityReferrer) {
                    DBSEntityReferrer referrer = (DBSEntityReferrer) constraint;
                    if (isGoodReferrer(monitor, bindings, referrer)) {
                        if (referrer.getConstraintType() == DBSEntityConstraintType.PRIMARY_KEY) {
                            return referrer;
                        } else if (uniqueId == null &&
                            (referrer.getConstraintType().isUnique() ||
                                (referrer instanceof DBSTableIndex && ((DBSTableIndex) referrer).isUnique()))) {
                            uniqueId = referrer;
                        }
                    }
                } else {
                    uniqueId = constraint;
                }
            }
            if (uniqueId != null) {
                return uniqueId;
            }
        }

        {
            // Check for pseudo attrs (ROWID)
            // Do this after natural identifiers search (see #3829)
            for (DBDAttributeBinding column : bindings) {
                DBDPseudoAttribute pseudoAttribute = column instanceof DBDAttributeBindingMeta ? ((DBDAttributeBindingMeta) column).getPseudoAttribute() : null;
                if (pseudoAttribute != null && pseudoAttribute.getType() == DBDPseudoAttributeType.ROWID) {
                    return new DBDPseudoReferrer(table, column);
                }
            }
        }

        // No physical identifiers or row ids
        // Make new or use existing virtual identifier
        DBVEntity virtualEntity = DBVUtils.getVirtualEntity(table, true);
        return virtualEntity.getBestIdentifier();
    }

    private static boolean isGoodReferrer(DBRProgressMonitor monitor, DBDAttributeBinding[] bindings, DBSEntityReferrer referrer) throws DBException
    {
        if (referrer instanceof DBDPseudoReferrer) {
            return true;
        }
        Collection<? extends DBSEntityAttributeRef> references = referrer.getAttributeReferences(monitor);
        if (references == null || references.isEmpty()) {
            return referrer instanceof DBVEntityConstraint;
        }
        for (DBSEntityAttributeRef ref : references) {
            boolean refMatches = false;
            for (DBDAttributeBinding binding : bindings) {
                if (binding.matches(ref.getAttribute(), false)) {
                    refMatches = true;
                    break;
                }
            }
            if (!refMatches) {
                return false;
            }
        }
        return true;
    }

    public static DBSEntityAssociation getAssociationByAttribute(DBDAttributeBinding attr) throws DBException {
        List<DBSEntityReferrer> referrers = attr.getReferrers();
        if (referrers != null) {
            for (final DBSEntityReferrer referrer : referrers) {
                if (referrer instanceof DBSEntityAssociation) {
                    return (DBSEntityAssociation) referrer;
                }
            }
        }
        throw new DBException("Association not found in attribute [" + attr.getName() + "]");
    }

    public static boolean equalAttributes(DBCAttributeMetaData attr1, DBCAttributeMetaData attr2) {
        return
            attr1 != null && attr2 != null &&
            SQLUtils.compareAliases(attr1.getLabel(), attr2.getLabel()) &&
            SQLUtils.compareAliases(attr1.getName(), attr2.getName()) &&
            CommonUtils.equalObjects(attr1.getEntityMetaData(), attr2.getEntityMetaData()) &&
            attr1.getOrdinalPosition() == attr2.getOrdinalPosition() &&
            attr1.isRequired() == attr2.isRequired() &&
            attr1.getMaxLength() == attr2.getMaxLength() &&
            CommonUtils.equalObjects(attr1.getPrecision(), attr2.getPrecision()) &&
            CommonUtils.equalObjects(attr1.getScale(), attr2.getScale()) &&
            attr1.getTypeID() == attr2.getTypeID() &&
            CommonUtils.equalObjects(attr1.getTypeName(), attr2.getTypeName());
    }

    public static double makeNumericValue(Object value) {
        if (value == null) {
            return 0;
        } else if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof Date) {
            return ((Date) value).getTime();
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        } else {
            return 0;
        }
    }

    public static void bindAttributes(
        @NotNull DBCSession session,
        @Nullable DBSEntity sourceEntity,
        @Nullable DBCResultSet resultSet,
        @NotNull DBDAttributeBinding[] bindings,
        @Nullable List<Object[]> rows) throws DBException
    {
        DBRProgressMonitor monitor = session.getProgressMonitor();
        DBPDataSource dataSource = session.getDataSource();
        DBPDataSourceContainer container = dataSource.getContainer();
        DBRProgressMonitor mdMonitor = container.isExtraMetadataReadEnabled() ?
            monitor : new LocalCacheProgressMonitor(monitor);

        final Map<DBCEntityMetaData, DBSEntity> entityBindingMap = new IdentityHashMap<>();

        monitor.beginTask("Discover resultset metadata", 3);
        try {
            SQLQuery sqlQuery = null;
            DBSEntity entity = null;
            int queryEntityMetaScore = -1;
            if (sourceEntity != null) {
                entity = sourceEntity;
            } else if (resultSet != null) {
                DBCStatement sourceStatement = resultSet.getSourceStatement();
                if (sourceStatement != null && sourceStatement.getStatementSource() != null) {
                    DBCExecutionSource executionSource = sourceStatement.getStatementSource();

                    monitor.subTask("Discover owner entity");
                    DBSDataContainer dataContainer = executionSource.getDataContainer();
                    if (dataContainer instanceof DBSEntity) {
                        entity = (DBSEntity) dataContainer;
                    }
                    DBCEntityMetaData entityMeta = null;
                    if (entity == null) {
                        // Discover from entity metadata
                        Object sourceDescriptor = executionSource.getSourceDescriptor();
                        if (sourceDescriptor instanceof SQLQuery) {
                            sqlQuery = (SQLQuery) sourceDescriptor;
                            entityMeta = sqlQuery.getEntityMetadata(false);
                        }
                        if (entityMeta != null) {
                            entity = DBUtils.getEntityFromMetaData(mdMonitor, session.getExecutionContext(), entityMeta);
                            if (entity != null) {
                                queryEntityMetaScore = entityMeta.getCompleteScore();
                                entityBindingMap.put(entityMeta, entity);
                            }
                        }
                    }
                }
            }

            boolean needsTableMetaForColumnResolution = dataSource.getInfo().needsTableMetaForColumnResolution();

            final Map<DBSEntity, DBDRowIdentifier> locatorMap = new IdentityHashMap<>();

            monitor.subTask("Discover attributes");
            for (DBDAttributeBinding binding : bindings) {
                monitor.subTask("Discover attribute '" + binding.getName() + "'");
                DBCAttributeMetaData attrMeta = binding.getMetaAttribute();
                if (attrMeta == null) {
                    continue;
                }

                SQLSelectItem selectItem = sqlQuery == null ? null : sqlQuery.getSelectItem(attrMeta.getOrdinalPosition());
                // We got table name and column name
                // To be editable we need this resultset contain set of columns from the same table
                // which construct any unique key
                DBSEntity attrEntity = null;
                if (sourceEntity == null) {
                    DBCEntityMetaData attrEntityMeta = attrMeta.getEntityMetaData();
                    if (attrEntityMeta == null && sqlQuery != null) {
                        if (selectItem != null && selectItem.isPlainColumn()) {
                            attrEntityMeta = selectItem.getEntityMetaData();
                        }
                    }
                    if (attrEntityMeta != null) {
                        attrEntity = entityBindingMap.get(attrEntityMeta);
                        if (attrEntity == null) {
                            if (entity != null &&
                                (queryEntityMetaScore > attrEntityMeta.getCompleteScore() || DBUtils.isView(entity)))
                            {
                                // If query entity score is greater than database provided entity meta score then use base entity (from SQL query)

                                // If this is a view then don't try to detect entity for each attribute
                                // MySQL returns source table name instead of view name. That's crazy.
                                attrEntity = entity;
                            } else {
                                attrEntity = DBUtils.getEntityFromMetaData(mdMonitor, session.getExecutionContext(), attrEntityMeta);

                                if (attrEntity == null && !mdMonitor.isForceCacheUsage()) {
                                    log.debug("Table '" + DBUtils.getSimpleQualifiedName(attrEntityMeta.getCatalogName(), attrEntityMeta.getSchemaName(), attrEntityMeta.getEntityName()) + "' not found in metadata catalog");
                                }
                            }
                        }
                        if (attrEntity != null) {
                            entityBindingMap.put(attrEntityMeta, attrEntity);
                        }
                    }
                }
                if (attrEntity == null) {
                    attrEntity = entity;
                }
                if (attrEntity != null && binding instanceof DBDAttributeBindingMeta bindingMeta) {
                    // Table column can be found from results metadata or from SQL query parser
                    // If datasource supports table names in result metadata then table name must present in results metadata.
                    // Otherwise it is an expression.

                    // It is a real table columns if:
                    //  - We use some explicit entity (e.g. table data editor)
                    //  - Table metadata was specified for column
                    //  - Database doesn't support column name collisions (default)
                    boolean updateColumnMeta = sourceEntity != null ||
                        bindingMeta.getMetaAttribute().getEntityMetaData() != null ||
                        !needsTableMetaForColumnResolution;

                    // Fix of #11194. If column name and alias are equals we could try to get real column name
                    // from parsed query because driver doesn't return it.
                    String columnName = attrMeta.getName();
                    if (sqlQuery != null &&
                        updateColumnMeta &&
                        CommonUtils.equalObjects(columnName, attrMeta.getLabel()))
                    {
                        int asteriskIndex = sqlQuery.getSelectItemAsteriskIndex();
                        if ((asteriskIndex < 0 || asteriskIndex > attrMeta.getOrdinalPosition()) &&
                            attrMeta.getOrdinalPosition() < sqlQuery.getSelectItemCount())
                        {
                            if (selectItem != null && selectItem.isPlainColumn()) {
                                String realColumnName = selectItem.getName();
                                if (!realColumnName.equalsIgnoreCase(columnName)) {
                                    if (DBUtils.isQuotedIdentifier(dataSource, realColumnName)) {
                                        columnName = DBUtils.getUnQuotedIdentifier(dataSource, realColumnName);
                                    } else {
                                        // #12008
                                        columnName = DBObjectNameCaseTransformer.transformName(dataSource, realColumnName);
                                    }
                                }
                            }
                        }
                    }

                    // Test pseudo attributes
                    DBDPseudoAttribute pseudoAttribute = DBUtils.getPseudoAttribute(attrEntity, columnName);
                    if (pseudoAttribute != null) {
                        bindingMeta.setPseudoAttribute(pseudoAttribute);
                    }

                    DBSEntityAttribute tableColumn = null;
                    if (bindingMeta.getPseudoAttribute() != null) {
                        tableColumn = bindingMeta.getPseudoAttribute().createFakeAttribute(attrEntity, attrMeta);
                    } else if (columnName != null) {
                        if (sqlQuery == null) {
                            tableColumn = attrEntity.getAttribute(mdMonitor, columnName);
                        } else {
                            boolean isAllColumns = sqlQuery.getSelectItemAsteriskIndex() != -1;
                            if (isAllColumns || (selectItem != null && (selectItem.isPlainColumn() || selectItem.getName().equals("*")))) {
                                tableColumn = attrEntity.getAttribute(mdMonitor, columnName);
                            }
                        }
                    }

                    if (tableColumn != null) {
                        boolean updateColumnHandler = updateColumnMeta && rows != null &&
                            (sqlQuery == null || !DBDAttributeBindingMeta.haveEqualsTypes(tableColumn, attrMeta));

                        DBCAttributeMetaData metaAttr = resultSet != null ? resultSet.getMeta().getAttributes().get(attrMeta.getOrdinalPosition()) : null;

                        if ((!updateColumnHandler && bindingMeta.getDataKind() != tableColumn.getDataKind()) ||
                            (resultSet != null && CommonUtils.isEmpty(metaAttr.getEntityName()) && !isSameDataTypes(tableColumn, metaAttr))
                        ) {
                            // Different data kind and meta attribute doesn't have table reference.
                            // Probably it is an alias which conflicts with column name
                            // Do not update entity attribute.
                            // It is a silly workaround for PG-like databases
                            log.debug("Cannot bind attribute '" + bindingMeta.getName() + "'");
                        } else if (bindingMeta.setEntityAttribute(tableColumn, updateColumnHandler) && rows != null) {
                            // We have new type and new value handler.
                            // We have to fix already fetched values.
                            // E.g. we fetched strings and found out that we should handle them as LOBs or enums.
                            try {
                                int pos = attrMeta.getOrdinalPosition();
                                for (Object[] row : rows) {
                                    row[pos] = binding.getValueHandler().getValueFromObject(session, tableColumn, row[pos], false, false);
                                }
                            } catch (DBCException e) {
                                log.warn("Error resolving attribute '" + binding.getName() + "' values", e);
                            }
                        }
                    }
                }
            }
            monitor.worked(1);

            {
                // Init row identifiers
                monitor.subTask("Detect unique identifiers");
                for (DBDAttributeBinding binding : bindings) {
                    if (!(binding instanceof DBDAttributeBindingMeta bindingMeta)) {
                        continue;
                    }
                    //monitor.subTask("Find attribute '" + binding.getName() + "' identifier");
                    DBSEntityAttribute attr = binding.getEntityAttribute();
                    if (attr == null) {
                        bindingMeta.setRowIdentifierStatus(ModelMessages.no_corresponding_table_column_text);
                        continue;
                    }
                    DBSEntity attrEntity = attr.getParentObject();
                    if (attrEntity != null) {
                        DBDRowIdentifier rowIdentifier = locatorMap.get(attrEntity);
                        if (rowIdentifier == null) {
                            DBSEntityConstraint entityIdentifier = getBestIdentifier(mdMonitor, attrEntity, bindings);
                            if (entityIdentifier != null) {
                                rowIdentifier = new DBDRowIdentifier(
                                    attrEntity,
                                    entityIdentifier);
                                locatorMap.put(attrEntity, rowIdentifier);
                            } else {
                                bindingMeta.setRowIdentifierStatus(ModelMessages.cannot_determine_unique_row_identifier_text);
                            }
                        }
                        bindingMeta.setRowIdentifier(rowIdentifier);
                    }
                }
                monitor.worked(1);
            }

            if (rows != null && !mdMonitor.isForceCacheUsage()) {
                monitor.subTask("Read results metadata");
                // Read nested bindings
                for (DBDAttributeBinding binding : bindings) {
                    binding.lateBinding(session, rows);
                }
            }

/*
            monitor.subTask("Load transformers");
            // Load transformers
            for (DBDAttributeBinding binding : bindings) {
                binding.loadTransformers(session, rows);
            }
*/

            {
                monitor.subTask("Complete metadata load");
                // Reload attributes in row identifiers
                for (DBDRowIdentifier rowIdentifier : locatorMap.values()) {
                    rowIdentifier.reloadAttributes(mdMonitor, bindings);
                }
            }
        }
        finally {
            monitor.done();
        }
    }

    private static boolean isSameDataTypes(@NotNull DBSEntityAttribute tableColumn, @NotNull DBCAttributeMetaData resultSetAttributeMeta) {
        if (tableColumn instanceof DBSTypedObjectEx) {
            DBSDataType columnDataType = ((DBSTypedObjectEx) tableColumn).getDataType();
            if (columnDataType != null) {
                return columnDataType.isStructurallyConsistentTypeWith(resultSetAttributeMeta);
            }
        }
        return tableColumn.getDataKind().isComplex() == resultSetAttributeMeta.getDataKind().isComplex();
    }

    /**
     * Returns read-only status for an attribute.
     */
    public static boolean isAttributeReadOnly(@Nullable DBDAttributeBinding attribute) {
        return isAttributeReadOnly(attribute, false);
    }

    /**
     * Returns read-only status for an attribute (also can check that row identifier is incomplete by checking a valid key).
     */
    public static boolean isAttributeReadOnly(@Nullable DBDAttributeBinding attribute, boolean checkValidKey) {
        if (attribute == null || attribute.getMetaAttribute() == null || attribute.getMetaAttribute().isReadOnly()) {
            return true;
        }
        DBDRowIdentifier rowIdentifier = attribute.getRowIdentifier();
        if (rowIdentifier == null || !(rowIdentifier.getEntity() instanceof DBSDataManipulator dataContainer)) {
            return true;
        }
        if (checkValidKey && rowIdentifier.isIncomplete()) {
            return true;
        }
        return !dataContainer.isFeatureSupported(DBSDataManipulator.FEATURE_DATA_UPDATE);
    }

    public static String getAttributeReadOnlyStatus(@NotNull DBDAttributeBinding attribute) {
        return getAttributeReadOnlyStatus(attribute, true);
    }

    public static String getAttributeReadOnlyStatus(@NotNull DBDAttributeBinding attribute, boolean checkValidKey) {
        if (attribute == null || attribute.getMetaAttribute() == null) {
            return "Null meta attribute";
        }
        if (attribute.getMetaAttribute().isReadOnly()) {
            return "Attribute is read-only";
        }
        DBDRowIdentifier rowIdentifier = attribute.getRowIdentifier();
        if (rowIdentifier == null) {
            String status = attribute.getRowIdentifierStatus();
            return status != null ? status : "No row identifier found";
        }
        if (checkValidKey) {
            if (rowIdentifier.isIncomplete()) {
                return "No valid row identifier found";
            }
        }
        DBSEntity dataContainer = rowIdentifier.getEntity();
        if (!(dataContainer instanceof DBSDataManipulator)) {
            return "Underlying entity doesn't support data modification";
        }
        if (!((DBSDataManipulator) dataContainer).isFeatureSupported(DBSDataManipulator.FEATURE_DATA_UPDATE)) {
            return "Underlying entity doesn't support data update";
        }
        return null;
    }

    /**
     * Checks if a result set is read-only.
     */
    public static boolean isResultSetReadOnly(@Nullable DBCExecutionContext executionContext) {
        return executionContext == null ||
            !executionContext.isConnected() ||
            !executionContext.getDataSource().getContainer().hasModifyPermission(DBPDataSourcePermission.PERMISSION_EDIT_DATA) ||
            executionContext.getDataSource().getInfo().isReadOnlyData();
    }

    /**
     * Gets read-only status for a result set.
     */
    @Nullable
    public static String getResultSetReadOnlyStatus(@Nullable DBPDataSourceContainer container) {
        DBPDataSource dataSource = container == null ? null : container.getDataSource();
        if (dataSource == null || !container.isConnected()) {
            return "No connection to database";
        }
        if (container.isConnectionReadOnly()) {
            return "Connection is in read-only state";
        }
        if (dataSource.getInfo().isReadOnlyData()) {
            return "Read-only data container";
        }
        if (!container.hasModifyPermission(DBPDataSourcePermission.PERMISSION_EDIT_DATA)) {
            return "Data edit restricted";
        }
        return null;
    }

    public static List<DBEPersistAction> getActionsListFromCommandContext(@NotNull DBRProgressMonitor monitor, DBECommandContext commandContext, DBCExecutionContext executionContext, Map<String, Object> options, @Nullable List<DBEPersistAction> actions) throws DBException {
        if (actions == null) {
            actions = new ArrayList<>();
        }
        for (DBECommand cmd : commandContext.getFinalCommands()) {
            DBEPersistAction[] persistActions = cmd.getPersistActions(monitor, executionContext, options);
            if (persistActions != null) {
                Collections.addAll(actions, persistActions);
            }
        }
        return actions;
    }

    @Nullable
    public static DBSEntity detectSingleSourceTable(DBDAttributeBinding ... attributes) {
        // Check single source flag
        DBSEntity sourceTable = null;
        for (DBDAttributeBinding attribute : attributes) {
            if (attribute.isPseudoAttribute()) {
                continue;
            }
            DBDRowIdentifier rowIdentifier = attribute.getRowIdentifier();
            if (rowIdentifier != null) {
                if (sourceTable == null) {
                    sourceTable = rowIdentifier.getEntity();
                } else if (sourceTable != rowIdentifier.getEntity()) {
                    return null;
                }
            }
        }
        return sourceTable;
    }

    /**
     * Checks if the data source has pending statements that are still executing.
     */
    public static boolean isExecutionInProgress(@NotNull DBPDataSource dataSource) {
        for (DBSInstance instance : dataSource.getAvailableInstances()) {
            for (DBCExecutionContext context : instance.getAllContexts()) {
                QMMConnectionInfo qmConnection = QMUtils.getCurrentConnection(context);
                if (qmConnection != null) {
                    QMMStatementExecuteInfo lastExec = qmConnection.getExecutionStack();
                    if (lastExec != null && !lastExec.isClosed()) {
                        // It is in progress
                        return true;
                    }
                }
            }
        }
        return false;
    }
}