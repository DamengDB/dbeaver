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
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBDatabaseException;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCStatement;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCCompositeCache;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCObjectCache;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCObjectLookupCache;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCStructLookupCache;
import org.jkiss.dbeaver.model.messages.ModelMessages;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSEntity;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectContainer;
import org.jkiss.dbeaver.model.struct.rdb.DBSProcedureContainer;
import org.jkiss.dbeaver.model.struct.rdb.DBSProcedureType;
import org.jkiss.dbeaver.model.struct.rdb.DBSSchema;
import org.jkiss.utils.ArrayUtils;
import org.jkiss.utils.CommonUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DamengSchema
 */
public class DamengSchema extends DamengGlobalObject implements DBSSchema, DBPRefreshableObject, DBPSystemObject,
    DBSProcedureContainer, DBPObjectStatisticsCollector, DBPScriptObject {
	
    private static final Log log = Log.getLog(DamengSchema.class);

    // Synonyms read is very expensive. Exclude them from children by default
    // Children are used in auto-completion which must be fast
    private static boolean SYNONYMS_AS_CHILDREN = false;

    final public TableCache tableCache = new TableCache();

    final public ConstraintCache constraintCache = new ConstraintCache();

    final public ForeignKeyCache foreignKeyCache = new ForeignKeyCache();

    // Schema Trigger
    final public TriggerCache triggerCache = new TriggerCache();

    final public TableTriggerCache tableTriggerCache = new TableTriggerCache();

    final public DataBaseTriggerCache databaseTriggerCache = new DataBaseTriggerCache();

    final public ViewTriggerCache viewTriggerCache = new ViewTriggerCache();

    final public IndexCache indexCache = new IndexCache();

    final public DataTypeCache dataTypeCache = new DataTypeCache();

    final public AllDataTypeCache allDataTypeCache = new AllDataTypeCache();

    final public DefineTypeCache defineTypeCache = new DefineTypeCache();

    final public SequenceCache sequenceCache = new SequenceCache();

    final public PackageCache packageCache = new PackageCache();

    final public SynonymCache synonymCache = new SynonymCache();

    final public DomainsCache domainsCache = new DomainsCache();

    final public DBLinkCache dbLinkCache = new DBLinkCache();

    final public ProceduresCache proceduresCache = new ProceduresCache();

    final public ExternalFunctionsCache externalFunctionsCache = new ExternalFunctionsCache();

    final public UdOperatorsCache udOperatorsCache = new UdOperatorsCache();

    private volatile boolean hasStatistics;

    private long id;

    private String name;

    private Date createTime;

    private transient DamengUser user;

    private DamengTable planTable;

    public DamengSchema(DamengDataSource dataSource, long id, String name) {
        super(dataSource, id > 0);
        this.id = id;
        this.name = name;
    }

    public DamengSchema(@NotNull DamengDataSource dataSource, @NotNull ResultSet dbResult) {
        super(dataSource, true);
        this.id = JDBCUtils.safeGetLong(dbResult, "USER_ID");
        this.name = JDBCUtils.safeGetString(dbResult, "USERNAME");
        if (CommonUtils.isEmpty(this.name)) {
            log.warn("Empty schema name fetched");
            this.name = "? " + super.hashCode();
        }
        this.createTime = JDBCUtils.safeGetTimestamp(dbResult, "CREATED");
        SYNONYMS_AS_CHILDREN = CommonUtils.getBoolean(dataSource.getContainer().getConnectionConfiguration()
            .getProviderProperty(DamengConstants.PROP_SEARCH_METADATA_IN_SYNONYMS));

        if (isSys()) {
            planTable = new DamengTable(this, DamengConstants.DM_PLAN_TABLE);
        }
    }

    private static DamengTableColumn getTableColumn(JDBCSession session, DamengTableBase parent, ResultSet dbResult,
                                                    String columnName) throws DBException {

        DamengTableColumn tableColumn = columnName == null ? null
            : parent.getAttribute(session.getProgressMonitor(), columnName);
        if (tableColumn == null) {
            log.debug("Column '" + columnName + "' not found in table '" + parent.getName() + "'");
        }
        return tableColumn;
    }

    public boolean isPublic() {
        return DamengConstants.USER_PUBLIC.equals(this.name);
    }

    public boolean isSys() {
        return DamengConstants.SCHEMA_SYS.equals(this.name);
    }

    @Property(order = 200)
    public long getId() {
        return id;
    }

    @Property(order = 190)
    public Date getCreateTime() {
        return createTime;
    }

    @NotNull
    @Override
    @Property(viewable = true, editable = true, order = 1)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Nullable
    @Override
    public String getDescription() {
        return null;
    }

    /**
     * User reference never read directly from database. It is used by managers to
     * create/delete/alter schemas
     *
     * @return user reference or null
     */
    public DamengUser getUser() {
        return user;
    }

    public void setUser(DamengUser user) {
        this.user = user;
    }

    @Association
    public Collection<DamengTableIndex> getIndexes(DBRProgressMonitor monitor) throws DBException {
        return indexCache.getObjects(monitor, this, null);
    }

    @Association
    public Collection<DamengTable> getTables(DBRProgressMonitor monitor) throws DBException {
        return tableCache.getTypedObjects(monitor, this, DamengTable.class);
    }

    public DamengTable getTable(DBRProgressMonitor monitor, String name) throws DBException {
        return tableCache.getObject(monitor, this, name, DamengTable.class);
    }

    @Association
    public Collection<DamengView> getViews(DBRProgressMonitor monitor) throws DBException {
        return tableCache.getTypedObjects(monitor, this, DamengView.class);
    }

    public DamengView getView(DBRProgressMonitor monitor, String name) throws DBException {
        return tableCache.getObject(monitor, this, name, DamengView.class);
    }

    @Association
    public Collection<DamengMaterializedView> getMaterializedViews(DBRProgressMonitor monitor) throws DBException {
        return tableCache.getTypedObjects(monitor, this, DamengMaterializedView.class);
    }

    @Association
    public DamengMaterializedView getMaterializedView(DBRProgressMonitor monitor, String name) throws DBException {
        return tableCache.getObject(monitor, this, name, DamengMaterializedView.class);
    }

    public TableCache getTableCache() {
        return tableCache;
    }

    @Association
    public Collection<DamengDataType> getDataTypes(DBRProgressMonitor monitor) throws DBException {
        return dataTypeCache.getAllObjects(monitor, this);
    }

    @Association
    public Collection<DamengDefineType> getDefineTypes(DBRProgressMonitor monitor) throws DBException {
        return defineTypeCache.getAllObjects(monitor, this);
    }

    public DamengDataType getDataType(DBRProgressMonitor monitor, String name) throws DBException {
        DamengDataType type = isPublic() ? getTypeBySynonym(monitor, name)
            : dataTypeCache.getObject(monitor, this, name);
        if (type == null) {
            if (name.equalsIgnoreCase("TOPOGEOMETRY")) {
                type = allDataTypeCache.getObject(monitor, this, name);
                if (type != null) {
                    return type;
                }
            }
            if (!isPublic()) {
                return getTypeBySynonym(monitor, name);
            }
        }
        return type;
    }

    @Nullable
    private DamengDataType getTypeBySynonym(DBRProgressMonitor monitor, String name) throws DBException {
        final DamengSynonym synonym = synonymCache.getObject(monitor, this, name);
        if (synonym != null && (synonym.getObjectType() == DamengObjectType.TYPE
            || synonym.getObjectType() == DamengObjectType.CLASS_BODY)) {
            Object object = synonym.getObject(monitor);
            if (object instanceof DamengDataType) {
                return (DamengDataType) object;
            }
        }
        return null;
    }

    @Association
    public Collection<DamengSequence> getSequences(DBRProgressMonitor monitor) throws DBException {
        return sequenceCache.getAllObjects(monitor, this);
    }

    @Association
    public Collection<DamengPackage> getPackages(DBRProgressMonitor monitor) throws DBException {
        return packageCache.getAllObjects(monitor, this);
    }

    @Association
    public Collection<DamengProcedureStandalone> getProceduresOnly(DBRProgressMonitor monitor) throws DBException {
        return getProcedures(monitor).stream().filter(proc -> proc.getProcedureType() == DBSProcedureType.PROCEDURE)
            .collect(Collectors.toList());
    }

    @Association
    public Collection<DamengProcedureStandalone> getFunctionsOnly(DBRProgressMonitor monitor) throws DBException {
        return getProcedures(monitor).stream().filter(
                proc -> (proc.getProcedureType() == DBSProcedureType.FUNCTION && proc.isExternalFunction() == false))
            .collect(Collectors.toList());
    }

    @Association
    public Collection<DamengExternalFunction> getExternalFunctionsOnly(DBRProgressMonitor monitor) throws DBException {
        return getExternalFunctions(monitor).stream().filter(
                proc -> (proc.getProcedureType() == DBSProcedureType.FUNCTION && proc.isExternalFunction() == true))
            .collect(Collectors.toList());
    }

    // getUdoperators

    @Association
    public Collection<DamengDomain> getDomainsOnly(DBRProgressMonitor monitor) throws DBException {
        return getDomains(monitor).stream().collect(Collectors.toList());
    }

    @Association
    public Collection<DamengUdOperator> getUdoperatorsOnly(DBRProgressMonitor monitor) throws DBException {
        return getUdoperators(monitor).stream().collect(Collectors.toList());
    }

    @Association
    public Collection<DamengProcedureStandalone> getProcedures(DBRProgressMonitor monitor) throws DBException {
        return proceduresCache.getAllObjects(monitor, this);
    }

    @Override
    public DamengProcedureStandalone getProcedure(DBRProgressMonitor monitor, String uniqueName) throws DBException {
        return proceduresCache.getObject(monitor, this, uniqueName);
    }

    @Association
    public Collection<DamengSynonym> getSynonyms(DBRProgressMonitor monitor) throws DBException {
        return synonymCache.getAllObjects(monitor, this);
    }

    @Association
    public DamengSynonym getSynonym(DBRProgressMonitor monitor, String name) throws DBException {
        return synonymCache.getObject(monitor, this, name);
    }

    @Association
    public Collection<DamengSchemaTrigger> getTriggers(DBRProgressMonitor monitor) throws DBException {
        return triggerCache.getAllObjects(monitor, this);
    }

    @Association
    public Collection<DamengTableTrigger> getTableTriggers(DBRProgressMonitor monitor) throws DBException {
        return tableTriggerCache.getAllObjects(monitor, this);
    }

    @Association
    public Collection<DamengDataBaseTrigger> getDataBaseTriggers(DBRProgressMonitor monitor) throws DBException {
        return databaseTriggerCache.getAllObjects(monitor, this);
    }

    @Association
    public Collection<DamengViewTrigger> getViewTriggers(DBRProgressMonitor monitor) throws DBException {
        return viewTriggerCache.getAllObjects(monitor, this);
    }

    @Association
    public Collection<DamengDBLink> getDatabaseLinks(DBRProgressMonitor monitor) throws DBException {
        return dbLinkCache.getAllObjects(monitor, this);
    }

    @Association
    public Collection<DamengExternalFunction> getExternalFunctions(DBRProgressMonitor monitor) throws DBException {
        return externalFunctionsCache.getAllObjects(monitor, this);
    }

    public DamengExternalFunction getExternalFunction(DBRProgressMonitor monitor, String uniqueName)
        throws DBException {
        return externalFunctionsCache.getObject(monitor, this, uniqueName);
    }

    @Association
    public Collection<DamengDomain> getDomains(DBRProgressMonitor monitor) throws DBException {
        return domainsCache.getAllObjects(monitor, this);
    }

    public DamengDomain getDomain(DBRProgressMonitor monitor, String uniqueName) throws DBException {
        return domainsCache.getObject(monitor, this, uniqueName);
    }

    @Association
    public Collection<DamengUdOperator> getUdoperators(DBRProgressMonitor monitor) throws DBException {
        return udOperatorsCache.getAllObjects(monitor, this);
    }

    @Association
    public DamengUdOperator getUdoperator(DBRProgressMonitor monitor, String uniqueName) throws DBException {
        return udOperatorsCache.getObject(monitor, this, uniqueName);
    }

    @Property(order = 90)
    public DamengUser getSchemaUser(DBRProgressMonitor monitor) throws DBException {
        return getDataSource().getUser(monitor, name);
    }

    @Override
    public Collection<DBSObject> getChildren(@NotNull DBRProgressMonitor monitor) throws DBException {
        List<DBSObject> children = new ArrayList<>();
        children.addAll(tableCache.getAllObjects(monitor, this));
        if (SYNONYMS_AS_CHILDREN) {
            children.addAll(synonymCache.getAllObjects(monitor, this));
        }
        children.addAll(packageCache.getAllObjects(monitor, this));
        return children;
    }

    @Override
    public DBSObject getChild(@NotNull DBRProgressMonitor monitor, @NotNull String childName) throws DBException {
        if (isSys() && childName.equals(DamengConstants.DM_PLAN_TABLE)) {
            return planTable;
        }

        final DamengTableBase table = tableCache.getObject(monitor, this, childName);
        if (table != null) {
            return table;
        }
        final DamengDataBaseTrigger dataTrigger = databaseTriggerCache.getObject(monitor, this, childName);
        if (dataTrigger != null) {
            return dataTrigger;
        }

        final DamengSchemaTrigger schemaTrigger = triggerCache.getObject(monitor, this, childName);
        if (schemaTrigger != null) {
            return schemaTrigger;
        }

        if (SYNONYMS_AS_CHILDREN) {
            DamengSynonym synonym = synonymCache.getObject(monitor, this, childName);
            if (synonym != null) {
                return synonym;
            }
        }
        return packageCache.getObject(monitor, this, childName);
    }

    @NotNull
    @Override
    public Class<? extends DBSEntity> getPrimaryChildType(@Nullable DBRProgressMonitor monitor) throws DBException {
        return DamengTable.class;
    }

    @Override
    public synchronized void cacheStructure(@NotNull DBRProgressMonitor monitor, int scope) throws DBException {
        monitor.subTask("Cache tables");
        tableCache.getAllObjects(monitor, this);
        if ((scope & STRUCT_ATTRIBUTES) != 0) {
            monitor.subTask("Cache table columns");
            tableCache.loadChildren(monitor, this, null);
        }
        if ((scope & STRUCT_ASSOCIATIONS) != 0) {
            monitor.subTask("Cache table indexes");
            indexCache.getObjects(monitor, this, null);
            monitor.subTask("Cache table constraints");
            constraintCache.getObjects(monitor, this, null);
            foreignKeyCache.getObjects(monitor, this, null);
            tableTriggerCache.getAllObjects(monitor, this);
        }
    }

    @Override
    public synchronized DBSObject refreshObject(@NotNull DBRProgressMonitor monitor) throws DBException {
        hasStatistics = false;
        tableCache.clearCache();
        foreignKeyCache.clearCache();
        constraintCache.clearCache();
        indexCache.clearCache();
        packageCache.clearCache();
        proceduresCache.clearCache();
        triggerCache.clearCache();
        tableTriggerCache.clearCache();
        dataTypeCache.clearCache();
        allDataTypeCache.clearCache();
        sequenceCache.clearCache();
        synonymCache.clearCache();
        databaseTriggerCache.clearCache();
        viewTriggerCache.clearCache();
        dbLinkCache.clearCache();
        externalFunctionsCache.clearCache();
        domainsCache.clearCache();
        udOperatorsCache.clearCache();
        return this;
    }

    @Override
    public boolean isSystem() {
        return ArrayUtils.contains(DamengConstants.SYSTEM_SCHEMAS, getName());
    }

    @Override
    public String toString() {
        return "Schema " + name;
    }

    void resetStatistics() {
        this.hasStatistics = false;
    }

    @Override
    public boolean isStatisticsCollected() {
        return hasStatistics;
    }

    @Override
    public void collectObjectStatistics(DBRProgressMonitor monitor, boolean totalSizeOnly, boolean forceRefresh)
        throws DBException {
        if (hasStatistics && !forceRefresh) {
            return;
        }
        try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Load table status")) {
            boolean hasDBA = getDataSource().isViewAvailable(monitor, DamengConstants.SCHEMA_SYS, "DBA_SEGMENTS");
            try (JDBCPreparedStatement dbStat = session.prepareStatement("SELECT SEGMENT_NAME,SUM(bytes) TABLE_SIZE\n"
                + "FROM " + DamengUtils.getSysSchemaPrefix(getDataSource())
                + (hasDBA ? "DBA_SEGMENTS" : "USER_SEGMENTS") + " s\n" + "WHERE S.SEGMENT_TYPE='TABLE'"
                + (hasDBA ? " AND s.OWNER = ?" : "") + "\n" + "GROUP BY SEGMENT_NAME")) {
                if (hasDBA) {
                    dbStat.setString(1, getName());
                }
                try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                    while (dbResult.next()) {
                        String tableName = dbResult.getString(1);
                        DamengTable table = getTable(monitor, tableName);
                        if (table != null) {
                            table.fetchTableSize(dbResult);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new DBCException("Error reading table statistics", e);
        } finally {
            for (DamengTableBase table : tableCache.getCachedObjects()) {
                if (table instanceof DamengTable && !((DamengTable) table).hasStatistics()) {
                    ((DamengTable) table).setTableSize(0L);
                }
            }
            hasStatistics = true;
        }
    }

    @Override
    public String getObjectDefinitionText(DBRProgressMonitor monitor, Map<String, Object> options) throws DBException {
        StringBuilder sql = new StringBuilder();
        sql.append("-- DROP USER ").append(DBUtils.getQuotedIdentifier(this)).append(";\n\n");
        sql.append("CREATE USER ").append(DBUtils.getQuotedIdentifier(this)).append("\n-- IDENTIFIED BY <password>\n")
            .append(";\n");

        // Show DDL for all schema objects
        monitor.beginTask("Cache schema", 1);
        cacheStructure(monitor, DBSObjectContainer.STRUCT_ALL);
        monitor.done();

        Collection<DamengDataType> dataTypes = getDataTypes(monitor);
        if (!monitor.isCanceled()) {
            monitor.beginTask("Load data types", dataTypes.size());
            for (DamengDataType dataType : dataTypes) {
                addDDLLine(sql, dataType.getObjectDefinitionText(monitor, options));
                addDDLLine(sql, dataType.getExtendedDefinitionText(monitor));
                monitor.worked(1);
                if (monitor.isCanceled()) {
                    break;
                }
            }
            monitor.done();
        }

        if (!monitor.isCanceled()) {
            List<DamengTableBase> tablesOrViews = getTableCache().getAllObjects(monitor, this);
            monitor.beginTask("Read tables DDL", tablesOrViews.size());
            for (DamengTableBase tableBase : tablesOrViews) {
                monitor.worked(1);
                if (tableBase instanceof DamengTable && ((DamengTable) tableBase).isNested()) {
                    continue;
                }
                monitor.subTask("Load table '" + tableBase.getName() + "' DDL");
                addDDLLine(sql, tableBase.getDDL(monitor, DamengDDLFormat.getCurrentFormat(getDataSource()), options));
                if (monitor.isCanceled()) {
                    break;
                }
            }
            monitor.done();
        }

        if (!monitor.isCanceled()) {
            Collection<DamengProcedureStandalone> procedures = getProcedures(monitor);
            monitor.beginTask("Load procedures", procedures.size());
            for (DamengProcedureStandalone procedure : procedures) {
                monitor.subTask(procedure.getName());
                addDDLLine(sql, procedure.getObjectDefinitionText(monitor, options));
                monitor.worked(1);
                if (monitor.isCanceled()) {
                    break;
                }
            }
            monitor.done();
        }

        // View Trigger
        if (!monitor.isCanceled()) {
            Collection<DamengViewTrigger> viewTriggers = getViewTriggers(monitor);
            monitor.beginTask("Load triggers", viewTriggers.size());
            for (DamengViewTrigger viewTrigger : viewTriggers) {
                monitor.subTask(viewTrigger.getName());
                addDDLLine(sql, viewTrigger.getObjectDefinitionText(monitor, options));
                monitor.worked(1);
                if (monitor.isCanceled()) {
                    break;
                }
            }
            monitor.done();
        }

        // Schema Trigger
        if (!monitor.isCanceled()) {
            Collection<DamengSchemaTrigger> triggers = getTriggers(monitor);
            monitor.beginTask("Load triggers", triggers.size());
            for (DamengSchemaTrigger trigger : triggers) {
                monitor.subTask(trigger.getName());
                addDDLLine(sql, trigger.getObjectDefinitionText(monitor, options));
                monitor.worked(1);
                if (monitor.isCanceled()) {
                    break;
                }
            }
            monitor.done();
        }

        // Database Trigger
        if (!monitor.isCanceled()) {
            Collection<DamengDataBaseTrigger> dataBaseTriggers = getDataBaseTriggers(monitor);
            monitor.beginTask("Load triggers", dataBaseTriggers.size());
            for (DamengDataBaseTrigger dataBaseTrigger : dataBaseTriggers) {
                monitor.subTask(dataBaseTrigger.getName());
                addDDLLine(sql, dataBaseTrigger.getObjectDefinitionText(monitor, options));
                monitor.worked(1);
                if (monitor.isCanceled()) {
                    break;
                }
            }
            monitor.done();
        }

        // Sequence
        if (!monitor.isCanceled()) {
            Collection<DamengSequence> sequences = getSequences(monitor);
            monitor.beginTask("Load sequences", sequences.size());
            for (DamengSequence sequence : sequences) {
                monitor.subTask(sequence.getName());
                addDDLLine(sql, sequence.getObjectDefinitionText(monitor, options));
                monitor.worked(1);
                if (monitor.isCanceled()) {
                    break;
                }
            }
            monitor.done();
        }

        /**
         * Package
         */
        if (!monitor.isCanceled()) {
            Collection<DamengPackage> packages = getPackages(monitor);
            monitor.beginTask("Load externalFunctions", packages.size());
            for (DamengPackage damengPackage : packages) {
                monitor.subTask(damengPackage.getName());
                addDDLLine(sql, damengPackage.getObjectDefinitionText(monitor, options));
                addDDLLine(sql, damengPackage.getExtendedDefinitionText(monitor));
                monitor.worked(1);
                if (monitor.isCanceled()) {
                    break;
                }
            }
            monitor.done();
        }

        /**
         * External function
         */
        if (!monitor.isCanceled()) {
            Collection<DamengExternalFunction> externalFunctions = getExternalFunctions(monitor);
            monitor.beginTask("Load externalFunctions", externalFunctions.size());
            for (DamengExternalFunction externalFunction : externalFunctions) {
                monitor.subTask(externalFunction.getName());
                addDDLLine(sql, externalFunction.getObjectDefinitionText(monitor, options));
                monitor.worked(1);
                if (monitor.isCanceled()) {
                    break;
                }
            }
            monitor.done();
        }

        /**
         * Dblinks
         */

        if (!monitor.isCanceled()) {
            Collection<DamengDBLink> dblinks = getDatabaseLinks(monitor);
            monitor.beginTask("Load dblinks", dblinks.size());
            for (DamengDBLink dblink : dblinks) {
                monitor.subTask(dblink.getName());
                addDDLLine(sql, dblink.getObjectDefinitionText(monitor, options));
                monitor.worked(1);
                if (monitor.isCanceled()) {
                    break;
                }
            }
            monitor.done();
        }

        /**
         * Synonyms
         */
        if (!monitor.isCanceled()) {
            Collection<DamengSynonym> synonyms = getSynonyms(monitor);
            monitor.beginTask("Load synonyms", synonyms.size());
            for (DamengSynonym synonym : synonyms) {
                monitor.subTask(synonym.getName());
                addDDLLine(sql, synonym.getObjectDefinitionText(monitor, options));
                monitor.worked(1);
                if (monitor.isCanceled()) {
                    break;
                }
            }
            monitor.done();
        }

        /**
         * Domains
         */
        if (!monitor.isCanceled()) {
            Collection<DamengDomain> domains = getDomains(monitor);
            monitor.beginTask("Load domains", domains.size());
            for (DamengDomain domain : domains) {
                monitor.subTask(domain.getName());
                addDDLLine(sql, domain.getObjectDefinitionText(monitor, options));
                monitor.worked(1);
                if (monitor.isCanceled()) {
                    break;
                }
            }
            monitor.done();
        }

        /**
         * DefineType
         */
        if (!monitor.isCanceled()) {
            Collection<DamengDefineType> defineTypes = getDefineTypes(monitor);
            monitor.beginTask("Load domains", defineTypes.size());
            for (DamengDefineType defineType : defineTypes) {
                monitor.subTask(defineType.getName());
                addDDLLine(sql, defineType.getObjectDefinitionText(monitor, options));
                monitor.worked(1);
                if (monitor.isCanceled()) {
                    break;
                }
            }
            monitor.done();
        }

        /**
         * UdOperator
         */
        if (!monitor.isCanceled()) {
            Collection<DamengUdOperator> udOperators = getUdoperators(monitor);
            monitor.beginTask("Load domains", udOperators.size());
            for (DamengUdOperator udOperator : udOperators) {
                monitor.subTask(udOperator.getName());
                addDDLLine(sql, udOperator.getObjectDefinitionText(monitor, options));
                monitor.worked(1);
                if (monitor.isCanceled()) {
                    break;
                }
            }
            monitor.done();
        }

        return sql.toString();
    }

    private void addDDLLine(StringBuilder sql, String ddl) {
        if (!CommonUtils.isEmpty(ddl)) {
            sql.append("\n").append(ddl);
            if (!ddl.trim().endsWith(";")) {
                sql.append(";");
            }
            sql.append("\n");
        }
    }

    private List<SpecialPosition> parsePositions(String value) {

        if (value == null) {
            return Collections.emptyList();
        }

        if (value.length() < 3) {
            return Collections.emptyList();
        }

        List<SpecialPosition> result = new ArrayList<>(1);

        String data[] = value.split(",");

        for (String s : data) {

            result.add(new SpecialPosition(s));

        }

        return result;

    }

    /**
     * DataType cache implementation
     */
    static class DataTypeCache extends JDBCObjectCache<DamengSchema, DamengDataType> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengSchema owner)
            throws SQLException {
            // Change the sql to get the Classes
            JDBCPreparedStatement dbStat = session.prepareStatement(
                "select  PKG_OBJ.ID ID, PKG_OBJ.NAME NAME, PKG_OBJ.CRTDATE CRTDATE, PKG_OBJ.INFO1, SCH_OBJ.ID SCHID, SCH_OBJ.NAME SCHNAME, PKG_OBJ.VALID, PKG_OBJ.SUBTYPE$, PKG_OBJ.INFO2 INFO2, p_pkg_obj.name p_pkg_name, p_pkg_sch_obj.name p_pkg_sch_name,\n"
                    +
                    "(select top 1 txt from systexts where id = pkg_obj.id order by seqno asc) DEFINITION from (select ID, NAME from SYSOBJECTS where TYPE$='SCH' and  name = ?) SCH_OBJ,\n"
                    +
                    "(select distinct PKG_OBJ_INNER.ID, PKG_OBJ_INNER.NAME, PKG_OBJ_INNER.SCHID, PKG_OBJ_INNER.CRTDATE, PKG_OBJ_INNER.INFO1, PKG_OBJ_INNER.INFO2, PKG_OBJ_INNER.SUBTYPE$, PKG_OBJ_INNER.VALID from SYSOBJECTS PKG_OBJ_INNER, SYSOBJECTS SCH_OBJ_INNER, SYSOBJECTS USER_OBJ_INNER where PKG_OBJ_INNER.TYPE$='SCHOBJ' and (PKG_OBJ_INNER.SUBTYPE$='CLASS' OR PKG_OBJ_INNER.SUBTYPE$='JCLASS') \n"
                    +
                    "and  PKG_OBJ_INNER.SCHID = (select id from sysobjects where TYPE$ = 'SCH' AND name = ?) and USER_OBJ_INNER.SUBTYPE$ = 'USER' and SCH_OBJ_INNER.ID = PKG_OBJ_INNER.SCHID and SCH_OBJ_INNER.PID = USER_OBJ_INNER.ID and SF_CHECK_PRIV_OPT(UID(), CURRENT_USERTYPE(), PKG_OBJ_INNER.ID, USER_OBJ_INNER.ID, USER_OBJ_INNER.INFO1, PKG_OBJ_INNER.ID) = 1) PKG_OBJ left join sysobjects p_pkg_obj on pkg_obj.info2 = p_pkg_obj.id \n"
                    +
                    "left join sysobjects p_pkg_sch_obj on p_pkg_obj.schid = p_pkg_sch_obj.id where PKG_OBJ.SCHID=SCH_OBJ.ID  ORDER BY NAME");
            dbStat.setString(1, owner.getName());
            dbStat.setString(2, owner.getName());
            return dbStat;
        }

        protected boolean classIsType(String ddl) {
            if (ddl == null) {
                return false;
            }

            int idx = ddl.indexOf('(');
            if (idx > 0) {
                ddl = ddl.substring(0, idx);
            }

            boolean asIs = false;
            String[] tmp = ddl.split(" ");
            for (int i = 0; i < tmp.length; i++) {
                if (tmp[i].equalsIgnoreCase("object")) {
                    if (asIs) {
                        return true;
                    }
                }

                asIs = tmp[i].equalsIgnoreCase("as") || tmp[i].equalsIgnoreCase("is");
            }

            return false;
        }

        @Override
        protected DamengDataType fetchObject(@NotNull JDBCSession session, @NotNull DamengSchema owner,
                                             @NotNull JDBCResultSet resultSet) throws SQLException {
            String type = JDBCUtils.safeGetString(resultSet, "SUBTYPE$");
            String definition = JDBCUtils.safeGetString(resultSet, "DEFINITION");
            if (type.equalsIgnoreCase("CLASS")) // as|is
            {
                if (classIsType(definition)) {
                    return null;
                }
            }
            return new DamengDataType(owner, resultSet);
        }
    }

    /**
     * DataType cache implementation
     */
    static class AllDataTypeCache extends JDBCObjectCache<DamengSchema, DamengDataType> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengSchema owner)
            throws SQLException {
            // Change the sql to get all type
            JDBCPreparedStatement dbStat = session.prepareStatement(
                "select  PKG_OBJ.ID ID, PKG_OBJ.NAME NAME, PKG_OBJ.CRTDATE CRTDATE, PKG_OBJ.INFO1, SCH_OBJ.ID SCHID, SCH_OBJ.NAME SCHNAME, PKG_OBJ.VALID, PKG_OBJ.SUBTYPE$, PKG_OBJ.INFO2 INFO2, p_pkg_obj.name p_pkg_name, p_pkg_sch_obj.name p_pkg_sch_name,\n"
                    +
                    "(select top 1 txt from systexts where id = pkg_obj.id order by seqno asc) DEFINITION from (select ID, NAME from SYSOBJECTS where TYPE$='SCH' and  name = ?) SCH_OBJ,\n"
                    +
                    "(select distinct PKG_OBJ_INNER.ID, PKG_OBJ_INNER.NAME, PKG_OBJ_INNER.SCHID, PKG_OBJ_INNER.CRTDATE, PKG_OBJ_INNER.INFO1, PKG_OBJ_INNER.INFO2, PKG_OBJ_INNER.SUBTYPE$, PKG_OBJ_INNER.VALID from SYSOBJECTS PKG_OBJ_INNER, SYSOBJECTS SCH_OBJ_INNER, SYSOBJECTS USER_OBJ_INNER where PKG_OBJ_INNER.TYPE$='SCHOBJ' and (PKG_OBJ_INNER.SUBTYPE$='CLASS' OR PKG_OBJ_INNER.SUBTYPE$='JCLASS' OR PKG_OBJ_INNER.SUBTYPE$='TYPE') \n"
                    +
                    "and  PKG_OBJ_INNER.SCHID = (select id from sysobjects where TYPE$ = 'SCH' AND name = ?) and USER_OBJ_INNER.SUBTYPE$ = 'USER' and SCH_OBJ_INNER.ID = PKG_OBJ_INNER.SCHID and SCH_OBJ_INNER.PID = USER_OBJ_INNER.ID and SF_CHECK_PRIV_OPT(UID(), CURRENT_USERTYPE(), PKG_OBJ_INNER.ID, USER_OBJ_INNER.ID, USER_OBJ_INNER.INFO1, PKG_OBJ_INNER.ID) = 1) PKG_OBJ left join sysobjects p_pkg_obj on pkg_obj.info2 = p_pkg_obj.id \n"
                    +
                    "left join sysobjects p_pkg_sch_obj on p_pkg_obj.schid = p_pkg_sch_obj.id where PKG_OBJ.SCHID=SCH_OBJ.ID  ORDER BY NAME");
            dbStat.setString(1, owner.getName());
            dbStat.setString(2, owner.getName());
            return dbStat;
        }

        @Override
        protected DamengDataType fetchObject(@NotNull JDBCSession session, @NotNull DamengSchema owner,
                                             @NotNull JDBCResultSet resultSet) throws SQLException {
            return new DamengDataType(owner, resultSet);
        }
    }

    /**
     * DataType cache implementation
     */
    static class DefineTypeCache extends JDBCObjectCache<DamengSchema, DamengDefineType> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengSchema owner)
            throws SQLException {
            // Change the sql to get the Custom Type
            JDBCPreparedStatement dbStat = session.prepareStatement(
                "select  TYPE_OBJ.ID, TYPE_OBJ.NAME, TYPE_OBJ.CRTDATE, SCH_OBJ.ID, SCH_OBJ.NAME, TYPE_OBJ.INFO1, TYPE_OBJ.SUBTYPE$,\n"
                    +
                    "(select top 1 txt from systexts where id = type_obj.id order by seqno asc) DEFINITION, TYPE_OBJ.VALID from (select ID, NAME from SYSOBJECTS where TYPE$='SCH' and  NAME=?) SCH_OBJ,\n"
                    +
                    "(select distinct PKG_OBJ_INNER.ID, PKG_OBJ_INNER.NAME, PKG_OBJ_INNER.SCHID, PKG_OBJ_INNER.CRTDATE, PKG_OBJ_INNER.INFO1, PKG_OBJ_INNER.VALID, PKG_OBJ_INNER.SUBTYPE$ from SYSOBJECTS PKG_OBJ_INNER, SYSOBJECTS SCH_OBJ_INNER, SYSOBJECTS USER_OBJ_INNER where PKG_OBJ_INNER.TYPE$='SCHOBJ' and (PKG_OBJ_INNER.SUBTYPE$='TYPE' or PKG_OBJ_INNER.SUBTYPE$='CLASS')\n"
                    +
                    " and USER_OBJ_INNER.SUBTYPE$ = 'USER' and SCH_OBJ_INNER.ID = PKG_OBJ_INNER.SCHID and SCH_OBJ_INNER.PID = USER_OBJ_INNER.ID and SF_CHECK_PRIV_OPT(UID(), CURRENT_USERTYPE(), PKG_OBJ_INNER.ID, USER_OBJ_INNER.ID, USER_OBJ_INNER.INFO1, PKG_OBJ_INNER.ID) = 1) TYPE_OBJ where TYPE_OBJ.SCHID=SCH_OBJ.ID ORDER BY TYPE_OBJ.NAME");
            dbStat.setString(1, owner.getName());
            return dbStat;
        }

        protected boolean classIsType(String ddl) {
            if (ddl == null) {
                return false;
            }

            int idx = ddl.indexOf('(');
            if (idx > 0) {
                ddl = ddl.substring(0, idx);
            }

            boolean asIs = false;
            String[] tmp = ddl.split(" ");
            for (int i = 0; i < tmp.length; i++) {
                if (tmp[i].equalsIgnoreCase("object")) {
                    if (asIs) {
                        return true;
                    }
                }

                asIs = tmp[i].equalsIgnoreCase("as") || tmp[i].equalsIgnoreCase("is");
            }

            return false;
        }

        @Override
        protected DamengDefineType fetchObject(@NotNull JDBCSession session, @NotNull DamengSchema owner,
                                               @NotNull JDBCResultSet resultSet) throws SQLException {
            String type = JDBCUtils.safeGetString(resultSet, "SUBTYPE$");
            String definition = JDBCUtils.safeGetString(resultSet, "DEFINITION");
            if (type.equalsIgnoreCase("CLASS")) // as|is
            {
                if (!classIsType(definition)) {
                    return null;
                }
            }
            return new DamengDefineType(owner, resultSet);
        }
    }

    /**
     * Sequence cache implementation
     */
    static class SequenceCache extends JDBCObjectCache<DamengSchema, DamengSequence> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengSchema owner)
            throws SQLException {
            final JDBCPreparedStatement dbStat = session.prepareStatement("SELECT "
                + DamengUtils.getSysCatalogHint(owner.getDataSource()) + " * FROM " + DamengUtils
                .getAdminAllViewPrefix(session.getProgressMonitor(), owner.getDataSource(), "SEQUENCES")
                + " WHERE SEQUENCE_OWNER=? ORDER BY SEQUENCE_NAME");
            dbStat.setString(1, owner.getName());
            return dbStat;
        }

        @Override
        protected DamengSequence fetchObject(@NotNull JDBCSession session, @NotNull DamengSchema owner,
                                             @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengSequence(owner, resultSet);
        }
    }

    /**
     * Procedures cache implementation
     */
    static class ProceduresCache extends JDBCObjectLookupCache<DamengSchema, DamengProcedureStandalone> {

        @NotNull
        @Override
        public JDBCStatement prepareLookupStatement(@NotNull JDBCSession session, @NotNull DamengSchema owner,
                                                    @Nullable DamengProcedureStandalone object, @Nullable String objectName)
            throws SQLException {
            // Add INFO1 , INFO2 to distinguish the function and external
            // function
            String sql = "SELECT * FROM (SELECT  * FROM ALL_OBJECTS WHERE "
                + "OBJECT_TYPE IN ('PROCEDURE','FUNCTION')  AND OWNER = ?"
                + (object == null && objectName == null ? "" : "AND OBJECT_NAME=? ") + ") PFINFO,"
                + "(SELECT INFO1 , INFO2 ,ID FROM SYSOBJECTS WHERE SUBTYPE$ = 'PROC' ) "
                + "PROC_TYPE WHERE  PROC_TYPE.ID = PFINFO.OBJECT_ID ORDER BY OBJECT_NAME";

            JDBCPreparedStatement dbStat = session.prepareStatement(sql);

            dbStat.setString(1, owner.getName());
            if (object != null || objectName != null) {
                dbStat.setString(2, object != null ? object.getName() : objectName);
            }
            return dbStat;
        }

        @Override
        protected DamengProcedureStandalone fetchObject(@NotNull JDBCSession session, @NotNull DamengSchema owner,
                                                        @NotNull JDBCResultSet dbResult) throws SQLException, DBException {
            return new DamengProcedureStandalone(owner, dbResult);
        }

    }

    /**
     * Procedures cache implementation
     */
    static class ExternalFunctionsCache extends JDBCObjectLookupCache<DamengSchema, DamengExternalFunction> {

        @NotNull
        @Override
        public JDBCStatement prepareLookupStatement(@NotNull JDBCSession session, @NotNull DamengSchema owner,
                                                    @Nullable DamengExternalFunction object, @Nullable String objectName)
            throws SQLException {
            String sql = "SELECT * FROM (SELECT  * FROM ALL_OBJECTS WHERE "
                + "OBJECT_TYPE IN ('PROCEDURE','FUNCTION')  AND OWNER = ?"
                + (object == null && objectName == null ? "" : "AND OBJECT_NAME=? ") + ") PFINFO,"
                + "(SELECT INFO1 , INFO2 ,ID FROM SYSOBJECTS WHERE SUBTYPE$ = 'PROC' ) "
                + "PROC_TYPE WHERE  PROC_TYPE.ID = PFINFO.OBJECT_ID ORDER BY OBJECT_NAME";

            JDBCPreparedStatement dbStat = session.prepareStatement(sql);

            dbStat.setString(1, owner.getName());
            if (object != null || objectName != null) {
                dbStat.setString(2, object != null ? object.getName() : objectName);
            }
            return dbStat;
        }

        @Override
        protected DamengExternalFunction fetchObject(@NotNull JDBCSession session, @NotNull DamengSchema owner,
                                                     @NotNull JDBCResultSet dbResult) throws SQLException, DBException {
            return new DamengExternalFunction(owner, dbResult);
        }

    }

    static class PackageCache extends JDBCObjectCache<DamengSchema, DamengPackage> {

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengSchema owner)
            throws SQLException {
            JDBCPreparedStatement dbStat = session.prepareStatement(
                "select  PKG_OBJ.ID ID, PKG_OBJ.NAME OBJECT_NAME, PKG_OBJ.CRTDATE CRTDATE, PKG_OBJ.INFO1 INFO1, SCH_OBJ.ID SCHID, SCH_OBJ.NAME SCHNAME, PKG_OBJ.VALID,\n"
                    +
                    "(select top 1 txt from systexts where id = pkg_obj.id order by seqno asc) DEFINITION from (select ID, NAME from SYSOBJECTS where TYPE$='SCH' and  name = ? ) SCH_OBJ, \n"
                    +
                    "(select distinct PKG_OBJ_INNER.ID, PKG_OBJ_INNER.NAME, PKG_OBJ_INNER.SCHID, PKG_OBJ_INNER.CRTDATE, PKG_OBJ_INNER.INFO1, PKG_OBJ_INNER.INFO2, PKG_OBJ_INNER.SUBTYPE$, PKG_OBJ_INNER.VALID from SYSOBJECTS PKG_OBJ_INNER, SYSOBJECTS SCH_OBJ_INNER, SYSOBJECTS USER_OBJ_INNER where PKG_OBJ_INNER.TYPE$='SCHOBJ' and PKG_OBJ_INNER.SUBTYPE$='PKG' \n"
                    +
                    "and  PKG_OBJ_INNER.SCHID = (select id from sysobjects where TYPE$ = 'SCH' AND name = ?) and USER_OBJ_INNER.SUBTYPE$ = 'USER' and SCH_OBJ_INNER.ID = PKG_OBJ_INNER.SCHID and SCH_OBJ_INNER.PID = USER_OBJ_INNER.ID and SF_CHECK_PRIV_OPT(UID(), CURRENT_USERTYPE(), PKG_OBJ_INNER.ID, USER_OBJ_INNER.ID, USER_OBJ_INNER.INFO1, PKG_OBJ_INNER.ID) = 1) PKG_OBJ where PKG_OBJ.SCHID=SCH_OBJ.ID  ORDER BY OBJECT_NAME\n");
            dbStat.setString(1, owner.getName());
            dbStat.setString(2, owner.getName());
            return dbStat;
        }

        @Override
        protected DamengPackage fetchObject(@NotNull JDBCSession session, @NotNull DamengSchema owner,
                                            @NotNull JDBCResultSet dbResult) throws SQLException, DBException {
            return new DamengPackage(owner, dbResult);
        }

    }

    /**
     * Sequence cache implementation
     */
    static class SynonymCache extends JDBCObjectLookupCache<DamengSchema, DamengSynonym> {
        @NotNull
        @Override
        public JDBCStatement prepareLookupStatement(@NotNull JDBCSession session, @NotNull DamengSchema owner,
                                                    DamengSynonym object, String objectName) throws SQLException {
            String synonymTypeFilter = (session.getDataSource().getContainer().getPreferenceStore()
                .getBoolean(DamengConstants.PREF_DBMS_READ_ALL_SYNONYMS) ? ""
                : "AND O.OBJECT_TYPE NOT IN ('JAVA CLASS','PACKAGE BODY')\n");

            String synonymName = object != null ? object.getName() : objectName;

            StringBuilder sql = new StringBuilder();
            sql.append(
                    "SELECT OWNER, SYNONYM_NAME, MAX(TABLE_OWNER) as TABLE_OWNER, MAX(TABLE_NAME) as TABLE_NAME, MAX(DB_LINK) as DB_LINK, MAX(OBJECT_TYPE) as OBJECT_TYPE FROM (\n")
                .append("SELECT S.*, NULL OBJECT_TYPE FROM ").append(DamengUtils
                    .getAdminAllViewPrefix(session.getProgressMonitor(), owner.getDataSource(), "SYNONYMS"))
                .append(" S WHERE S.OWNER = ?");
            if (synonymName != null) {
                sql.append(" AND S.SYNONYM_NAME = ?");
            }
            sql.append("\nUNION ALL\n").append("SELECT S.*,O.OBJECT_TYPE FROM ")
                .append(DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(), owner.getDataSource(),
                    "SYNONYMS"))
                .append(" S, ").append(DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(),
                    owner.getDataSource(), "OBJECTS"))
                .append(" O\n").append("WHERE S.OWNER = ?\n");
            if (synonymName != null) {
                sql.append(" AND S.SYNONYM_NAME = ? ");
            }
            sql.append(synonymTypeFilter).append(
                "AND O.OWNER=S.TABLE_OWNER AND O.OBJECT_NAME=S.TABLE_NAME AND O.SUBOBJECT_NAME IS NULL\n)\n");
            sql.append("GROUP BY OWNER, SYNONYM_NAME");
            if (synonymName == null) {
                sql.append("\nORDER BY SYNONYM_NAME");
            }

            JDBCPreparedStatement dbStat = session.prepareStatement(sql.toString());
            int paramNum = 1;
            dbStat.setString(paramNum++, owner.getName());
            if (synonymName != null) {
                dbStat.setString(paramNum++, synonymName);
            }
            dbStat.setString(paramNum++, owner.getName());
            if (synonymName != null) {
                dbStat.setString(paramNum++, synonymName);
            }
            return dbStat;
        }

        @Override
        protected DamengSynonym fetchObject(@NotNull JDBCSession session, @NotNull DamengSchema owner,
                                            @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengSynonym(owner, resultSet);
        }

    }

    static class DBLinkCache extends JDBCObjectCache<DamengSchema, DamengDBLink> {

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengSchema owner)
            throws SQLException {

            StringBuffer getDblinkSql = new StringBuffer();

            JDBCPreparedStatement dbStat = null;

            if (owner.getName() == null || owner.getName().toUpperCase().equals("PUBLIC")) {
                getDblinkSql.append(" SELECT DISTINCT GLINK_OBJ_INNER.SCHID " + DamengDBLink.SCHID
                    + ", GLINK_OBJ_INNER.NAME " + DamengDBLink.DBLINK_NAME + ", NULL " + DamengDBLink.SCH_NAME
                    + ",SF_DBLINK_GET_LOGNAME('SYSDBA', GLINK_OBJ_INNER.NAME, 0)" + DamengDBLink.LOGIN_USER_NAME
                    + ", SF_DBLINK_GET_CONSTR('SYSDBA', GLINK_OBJ_INNER.NAME, 0)" + DamengDBLink.CONNECTION_STRING
                    + ", GLINK_OBJ_INNER.CRTDATE " + DamengDBLink.CREATED_DATE + ", GLINK_OBJ_INNER.INFO1 "
                    + DamengDBLink.DB_TYPE_ID
                    + " FROM SYSOBJECTS GLINK_OBJ_INNER WHERE GLINK_OBJ_INNER.TYPE$ = 'GDBLINK' AND "
                    + "SF_CHECK_PRIV_OPT(UID(), CURRENT_USERTYPE(),GLINK_OBJ_INNER.ID, -1, -1, "
                    + "GLINK_OBJ_INNER.ID) = 1 ORDER BY " + DamengDBLink.DBLINK_NAME);
                dbStat = session.prepareStatement(getDblinkSql.toString());
            } else {
                getDblinkSql.append(" SELECT  LINKS.SCHID " + DamengDBLink.SCHID + ", " + " LINKS.NAME "
                    + DamengDBLink.DBLINK_NAME + ",  SCHS.NAME " + DamengDBLink.SCH_NAME + ", "
                    + " SF_DBLINK_GET_LOGNAME(SCHS.NAME, LINKS.NAME, 1)" + DamengDBLink.LOGIN_USER_NAME + ", "
                    + " SF_DBLINK_GET_CONSTR(SCHS.NAME, LINKS.NAME, 1) " + DamengDBLink.CONNECTION_STRING + ", "
                    + DamengDBLink.CREATED_DATE + ", INFO1 " + DamengDBLink.DB_TYPE_ID
                    + " FROM (SELECT DISTINCT LINK_OBJ_INNER.ID, "
                    + " LINK_OBJ_INNER.NAME, LINK_OBJ_INNER.SCHID, LINK_OBJ_INNER.CRTDATE, "
                    + " LINK_OBJ_INNER.INFO1 FROM SYSOBJECTS LINK_OBJ_INNER, SYSOBJECTS SCH_OBJ_INNER, "
                    + " SYSOBJECTS USER_OBJ_INNER WHERE LINK_OBJ_INNER.SUBTYPE$ = 'DBLINK' AND  "
                    + " USER_OBJ_INNER.SUBTYPE$ = 'USER' AND SCH_OBJ_INNER.ID = LINK_OBJ_INNER.SCHID "
                    + " AND SCH_OBJ_INNER.PID = USER_OBJ_INNER.ID AND SF_CHECK_PRIV_OPT(UID(),"
                    + " CURRENT_USERTYPE(), LINK_OBJ_INNER.ID, USER_OBJ_INNER.ID, USER_OBJ_INNER.INFO1, "
                    + " LINK_OBJ_INNER.ID) = 1) LINKS, (SELECT ID, NAME FROM SYSOBJECTS WHERE TYPE$='SCH' "
                    + " AND NAME = ? ) SCHS WHERE " + "LINKS.SCHID = SCHS.ID ORDER BY DBLINK_NAME");
                dbStat = session.prepareStatement(getDblinkSql.toString());
                dbStat.setString(1, owner.getName());
            }
            return dbStat;
        }

        @Override
        protected DamengDBLink fetchObject(@NotNull JDBCSession session, @NotNull DamengSchema owner,
                                           @NotNull JDBCResultSet dbResult) throws SQLException, DBException {
            return new DamengDBLink(session.getProgressMonitor(), owner, dbResult);
        }

    }

    static class TriggerCache extends JDBCObjectCache<DamengSchema, DamengSchemaTrigger> {

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengSchema schema)
            throws SQLException {
            // change the sql to get Schema Trigger
            JDBCPreparedStatement dbStat = session.prepareStatement("SELECT t.*, s.crtdate\n" + "FROM "
                + DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(), schema.getDataSource(),
                "TRIGGERS t")
                + " , SYSOBJECTS s WHERE OWNER=? AND TRIM(BASE_OBJECT_TYPE) IN ('SCHEMA') AND s.name = t.trigger_name\n"
                + "ORDER BY TRIGGER_NAME");
            dbStat.setString(1, schema.getName());
            return dbStat;
        }

        @Override
        protected DamengSchemaTrigger fetchObject(@NotNull JDBCSession session, @NotNull DamengSchema dmSchema,
                                                  @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengSchemaTrigger(dmSchema, resultSet);
        }
    }

    static class DataBaseTriggerCache extends JDBCObjectCache<DamengSchema, DamengDataBaseTrigger> {

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengSchema schema)
            throws SQLException {
            JDBCPreparedStatement dbStat = session.prepareStatement("SELECT t.*, s.crtdate\n" + "FROM "
                + DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(), schema.getDataSource(),
                "TRIGGERS t")
                + " , SYSOBJECTS s WHERE OWNER=? AND TRIM(BASE_OBJECT_TYPE) IN ('DATABASE') AND s.name = t.trigger_name\n"
                + "ORDER BY TRIGGER_NAME");
            dbStat.setString(1, schema.getName());
            return dbStat;
        }

        @Override
        protected DamengDataBaseTrigger fetchObject(@NotNull JDBCSession session, @NotNull DamengSchema dmSchema,
                                                    @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengDataBaseTrigger(dmSchema, resultSet);
        }
    }

    static class DomainsCache extends JDBCObjectCache<DamengSchema, DamengDomain> {

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengSchema schema)
            throws SQLException {
            JDBCPreparedStatement dbStat = session.prepareStatement(
                " SELECT DOM.ID, DOM.NAME DOMAIN_NAME, DOM.CRTDATE, DOM.CHECKINFO, COL.TYPE$ DATA_TYPE, \n"
                    + " SF_GET_COLUMN_SIZE(COL.TYPE$, CAST(COL.LENGTH$ AS INT), CAST(COL.SCALE AS INT)) LEN,\n"
                    + " COL.SCALE, COL.DEFVAL, SCH.ID, SCH.NAME SCHNAME FROM "
                    + " (SELECT ID, NAME FROM SYSOBJECTS WHERE TYPE$='SCH' \n"
                    + " AND  NAME = ?) SCH, (SELECT * FROM (SELECT DISTINCT DOMAIN_OBJ_INNER.ID, DOMAIN_OBJ_INNER.NAME, \n"
                    + " DOMAIN_OBJ_INNER.CRTDATE, DOMAIN_OBJ_INNER.SCHID FROM SYSOBJECTS DOMAIN_OBJ_INNER, SYSOBJECTS SCH_OBJ_INNER,\n"
                    + " SYSOBJECTS USER_OBJ_INNER WHERE DOMAIN_OBJ_INNER.SUBTYPE$='DOMAIN' AND USER_OBJ_INNER.SUBTYPE$ = 'USER' \n"
                    + " AND SCH_OBJ_INNER.ID = DOMAIN_OBJ_INNER.SCHID AND SCH_OBJ_INNER.PID = USER_OBJ_INNER.ID \n"
                    + " AND SF_CHECK_PRIV_OPT(UID(), CURRENT_USERTYPE(),DOMAIN_OBJ_INNER.ID, USER_OBJ_INNER.ID, \n"
                    + " USER_OBJ_INNER.INFO1, DOMAIN_OBJ_INNER.ID) = 1) OBJ LEFT JOIN \n"
                    + " (SELECT TABLEID, CHECKINFO FROM SYSCONS) CONS ON(OBJ.ID=CONS.TABLEID)) DOM, \n"
                    + " (SELECT ID, TYPE$, LENGTH$, SCALE, DEFVAL FROM SYSCOLUMNS) COL \n"
                    + "  WHERE DOM.SCHID=SCH.ID AND COL.ID=DOM.ID ORDER BY DOM.NAME");
            dbStat.setString(1, schema.getName());
            return dbStat;
        }

        @Override
        protected DamengDomain fetchObject(@NotNull JDBCSession session, @NotNull DamengSchema dmSchema,
                                           @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengDomain(dmSchema, resultSet);
        }
    }

    /**
     * UdOperator
     */
    static class UdOperatorsCache extends JDBCObjectCache<DamengSchema, DamengUdOperator> {

        @Override
        protected @NotNull JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session,
                                                                 @NotNull DamengSchema owner) throws SQLException {
            // Change the sql to get UdOperator
            JDBCPreparedStatement dbStat = session.prepareStatement(
                "   SELECT   OP_OBJ.ID, OP_OBJ.NAME OP_NAME, SCH_OBJ.ID SCHID, SCH_OBJ.NAME SCH_NAME,"
                    + " OP_OBJ.VERSION OP_VERSION, OP_OBJ.CRTDATE OP_CRTDATE, OP_OBJ.VALID OP_VALID, "
                    + " OP_ARG.ID ARG_ID, OP_ARG.LEFTARG_TYPE, OP_ARG.RIGHTARG_TYPE, OP_ARG.CRTDATE ARG_CRTDATE,"
                    + " OP_ARG.VERSION ARG_VERSION, OP_ARG.VALID ARG_VALID, SF_GET_UNICODE_FLAG() CHARSET, OP_ARG.INFO1,"
                    + " OP_ARG.INFO2 FROM (SELECT * FROM SYSOBJECTS WHERE TYPE$ = 'SCHOBJ' "
                    + " AND SUBTYPE$ = 'OPERATOR') OP_OBJ, (SELECT * FROM SYSOPARGS) OP_ARG, "
                    + " (SELECT ID, NAME FROM SYSOBJECTS WHERE TYPE$='SCH' AND  NAME = ? ) SCH_OBJ "
                    + " WHERE OP_OBJ.ID = OP_ARG.PID AND OP_OBJ.SCHID = SCH_OBJ.ID "
                    + " ORDER BY OP_OBJ.NAME, OP_ARG.ID ");
            dbStat.setString(1, owner.getName());
            return dbStat;
        }

        /**
         * UdOperator overload
         */
        @Override
        protected synchronized void loadObjects(DBRProgressMonitor monitor, DamengSchema owner) throws DBException {

            final int DEFAULT_MAX_CACHE_SIZE = 1000000;
            if (isFullyCached() || monitor.isCanceled()) {
                return;
            }

            List<DamengUdOperator> tmpObjectList = new ArrayList<>();
            Map<String, DamengUdOperator> udOperatorMap = new HashMap<String, DamengUdOperator>();

            DBPDataSource dataSource = owner.getDataSource();
            if (dataSource == null) {
                throw new DBException(ModelMessages.error_not_connected_to_database);
            }
            if (owner.isPersisted()) {
                // Load cache from database only for persisted objects
                try {
                    try (JDBCSession session = DBUtils.openMetaSession(monitor, owner,
                        "Load objects from " + owner.getName())) {
                        beforeCacheLoading(session, owner);
                        try (JDBCStatement dbStat = prepareObjectsStatement(session, owner)) {
                            monitor.subTask("Load " + getCacheName());
                            dbStat.setFetchSize(DBConstants.METADATA_FETCH_SIZE);
                            dbStat.executeStatement();
                            JDBCResultSet dbResult = dbStat.getResultSet();
                            if (dbResult != null) {
                                try {
                                    while (dbResult.next()) {
                                        if (monitor.isCanceled()) {
                                            return;
                                        }
                                        String id = dbResult.getString(1);

                                        DamengUdOperator udOperator = null;
                                        if (udOperatorMap.containsKey(id)) {
                                            udOperator = udOperatorMap.get(id);
                                            udOperator.setOverrideCount(udOperator.getOverrideCount() + 1);

                                        } else {
                                            udOperator = new DamengUdOperator(owner, dbResult);
                                            udOperatorMap.put(id, udOperator);
                                        }

                                        String overrideLeftArgType = new DamengTableColumn().getClassName(session,
                                            JDBCUtils.safeGetString(dbResult, "LEFTARG_TYPE"), false);
                                        String overrideRightArgType = new DamengTableColumn().getClassName(session,
                                            JDBCUtils.safeGetString(dbResult, "RIGHTARG_TYPE"), false);

                                        DamengUdOperatorOverride udOperatorOverride = new DamengUdOperatorOverride(
                                            session, owner, dbResult, udOperator, overrideLeftArgType,
                                            overrideRightArgType);

                                        udOperator.getUdOperatorOverrideList().add(udOperatorOverride);

                                    }
                                } finally {
                                    dbResult.close();
                                }
                            }
                        } finally {
                            afterCacheLoading(session, owner);
                        }
                    } catch (SQLException ex) {
                        throw new DBDatabaseException(ex, dataSource);
                    } catch (DBException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        throw new DBException("Internal driver error", ex);
                    }
                } catch (Exception e) {
                    if (!handleCacheReadError(e)) {
                        throw e;
                    }
                }
            }

            // Traversal map and add to ObjectList
            for (String key : udOperatorMap.keySet()) {
                tmpObjectList.add(udOperatorMap.get(key));

            }

            if (tmpObjectList.size() == DEFAULT_MAX_CACHE_SIZE) {
                log.warn("Maximum cache size exceeded (" + DEFAULT_MAX_CACHE_SIZE + ") in " + this);
            }

            addCustomObjects(monitor, owner, tmpObjectList);

            Comparator<DamengUdOperator> comparator = getListOrderComparator();
            if (comparator != null && !CommonUtils.isEmpty(tmpObjectList)) {
                tmpObjectList.sort(comparator);
            }

            detectCaseSensitivity(owner);
            mergeCache(tmpObjectList);
            this.invalidateObjects(monitor, owner, new CacheIterator());

        }

        @Override
        protected @Nullable DamengUdOperator fetchObject(@NotNull JDBCSession session, @NotNull DamengSchema owner,
                                                         @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return null;
        }

    }

    public class TableCache extends JDBCStructLookupCache<DamengSchema, DamengTableBase, DamengTableColumn> {

        TableCache() {
            super(DamengConstants.COLUMN_OBJECT_NAME);
            setListOrderComparator(DBUtils.nameComparator());
        }

        @NotNull
        @Override
        public JDBCStatement prepareLookupStatement(@NotNull JDBCSession session, @NotNull DamengSchema owner,
                                                    @Nullable DamengTableBase object, @Nullable String objectName) throws SQLException {
            String tableOper = "=";

            if (object instanceof DamengMaterializedView) {
                System.out.print("11111");
            }

            boolean hasAllAllTables = owner.getDataSource().isViewAvailable(session.getProgressMonitor(), null,
                "ALL_ALL_TABLES");
            boolean useAlternativeQuery = CommonUtils
                .toBoolean(getDataSource().getContainer().getConnectionConfiguration()
                    .getProviderProperty(DamengConstants.PROP_METADATA_USE_ALTERNATIVE_TABLE_QUERY));
            String tablesSource = hasAllAllTables ? "ALL_TABLES" : "TABLES";
            String tableTypeColumns = hasAllAllTables ? "t.TABLE_TYPE_OWNER,t.TABLE_TYPE"
                : "NULL as TABLE_TYPE_OWNER, NULL as TABLE_TYPE";

            JDBCPreparedStatement dbStat;
            if (!useAlternativeQuery) {
                dbStat = session.prepareStatement("SELECT * FROM ( SELECT "
                    + DamengUtils.getSysCatalogHint(owner.getDataSource())
                    + " s.INFO3, s.INFO1 & 0x001FFFE0  symbol,O.*,\n" + tableTypeColumns
                    + ",t.TABLESPACE_NAME,t.PARTITIONED,t.IOT_TYPE,t.IOT_NAME,t.NESTED,t.NUM_ROWS\n" + "FROM "
                    + DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(), getDataSource(), "OBJECTS")
                    + " O\n" + ", "
                    + DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(), owner.getDataSource(),
                    tablesSource)
                    + " t, sysobjects s WHERE t.OWNER(+) = O.OWNER AND t.TABLE_NAME(+) = o.OBJECT_NAME\n"
                    +
                    " AND O.OWNER=? AND O.OBJECT_TYPE IN ('TABLE') and s.NAME = o.OBJECT_NAME and s.schid = (select ID from sysobjects where name = ? and TYPE$ = 'SCH') "
                    +
                    " AND INFO3 & 0x100000 != 0x100000 AND INFO3 & 0x200000 != 0x200000 AND INFO3 & 0x003F NOT IN (0x0A, 0x20) AND (INFO3 & 0x100000000) = 0 AND (PID=-1 or PID=0) AND INFO3 & 0x003F != 13 "
                    + " UNION \n" + " SELECT s.INFO3, s.INFO1 & 0x001FFFE0  symbol,O.*,\n" + tableTypeColumns
                    + ",t.TABLESPACE_NAME,t.PARTITIONED,t.IOT_TYPE,t.IOT_NAME,t.NESTED,t.NUM_ROWS\n" + "FROM "
                    + DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(), getDataSource(), "OBJECTS")
                    + " O\n" + ", "
                    + DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(), owner.getDataSource(),
                    tablesSource)
                    + " t, sysobjects s WHERE t.OWNER(+) = O.OWNER AND t.TABLE_NAME(+) = o.OBJECT_NAME\n"
                    +
                    "AND O.OWNER=? AND O.OBJECT_TYPE IN ('VIEW', 'MATERIALIZED VIEW') and s.NAME = o.OBJECT_NAME and s.schid = (select ID from sysobjects where name = ? and TYPE$ = 'SCH') "
                    + " ) where symbol >= 0  "
                    + (object == null && objectName == null ? "" : " AND OBJECT_NAME" + tableOper + "?")
                    + (object instanceof DamengTable ? " AND OBJECT_TYPE='TABLE'" : "")
                    + (object instanceof DamengView ? " AND OBJECT_TYPE='VIEW'" : "")
                    + (object instanceof DamengMaterializedView ? " AND OBJECT_TYPE='MATERIALIZED VIEW'" : ""));
                dbStat.setString(1, owner.getName());
                dbStat.setString(2, owner.getName());
                dbStat.setString(3, owner.getName());
                dbStat.setString(4, owner.getName());
                if (object != null || objectName != null) {
                    dbStat.setString(5, object != null ? object.getName() : objectName);
                }

                return dbStat;
            } else {
                return getAlternativeTableStatement(session, owner, object, objectName, tablesSource, tableTypeColumns);
            }
        }

        @Override
        protected DamengTableBase fetchObject(@NotNull JDBCSession session, @NotNull DamengSchema owner,
                                              @NotNull JDBCResultSet dbResult) throws SQLException, DBException {
            final String tableType = JDBCUtils.safeGetString(dbResult, DamengConstants.COLUMN_OBJECT_TYPE);
            final int symbol = JDBCUtils.safeGetInt(dbResult, "SYMBOL");
            if ("TABLE".equals(tableType)) {
                return new DamengTable(session.getProgressMonitor(), owner, dbResult);
            } else {
                if (symbol != 0) {
                    return new DamengMaterializedView(owner, dbResult);
                }

                return new DamengView(owner, dbResult);
            }
        }

        @Override
        protected JDBCStatement prepareChildrenStatement(@NotNull JDBCSession session, @NotNull DamengSchema owner,
                                                         @Nullable DamengTableBase forTable) throws SQLException {
            String colsView;
            if (!owner.getDataSource().isViewAvailable(session.getProgressMonitor(), DamengConstants.SCHEMA_SYS,
                "ALL_TAB_COLS")) {
                colsView = "TAB_COLUMNS";
            } else {
                colsView = "TAB_COLS";
            }
            StringBuilder sql = new StringBuilder(500);
            sql.append("SELECT ").append(DamengUtils.getSysCatalogHint(owner.getDataSource()))
                .append("\nc.*,c.TABLE_NAME as OBJECT_NAME " + "FROM ")
                .append(DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(), getDataSource(), colsView))
                .append(" c\n" +
                    "WHERE c.OWNER=?");
            if (forTable != null) {
                sql.append(" AND c.TABLE_NAME=?");
            }
            JDBCPreparedStatement dbStat = session.prepareStatement(sql.toString());
            dbStat.setString(1, owner.getName());
            if (forTable != null) {
                dbStat.setString(2, forTable.getName());
            }
            return dbStat;
        }

        @Override
        protected DamengTableColumn fetchChild(@NotNull JDBCSession session, @NotNull DamengSchema owner,
                                               @NotNull DamengTableBase table, @NotNull JDBCResultSet dbResult)
            throws SQLException, DBException {
            return new DamengTableColumn(session, session.getProgressMonitor(), table, dbResult);
        }

        @Override
        protected void cacheChildren(DamengTableBase parent, List<DamengTableColumn> dmTableColumns) {
            dmTableColumns.sort(DBUtils.orderComparator());
            super.cacheChildren(parent, dmTableColumns);
        }

        @NotNull
        private JDBCStatement getAlternativeTableStatement(@NotNull JDBCSession session, @NotNull DamengSchema owner,
                                                           @Nullable DamengTableBase object, @Nullable String objectName,
                                                           String tablesSource,
                                                           String tableTypeColumns) throws SQLException {
            boolean hasName = object == null && objectName != null;
            JDBCPreparedStatement dbStat;
            StringBuilder sql = new StringBuilder();
            String tableQuery = "SELECT t.OWNER, t.TABLE_NAME AS OBJECT_NAME, 'TABLE' AS OBJECT_TYPE, 'VALID' AS STATUS,"
                + tableTypeColumns + ", t.TABLESPACE_NAME,\n"
                + "t.PARTITIONED, t.IOT_TYPE, t.IOT_NAME, t.TEMPORARY, t.SECONDARY, t.NESTED, t.NUM_ROWS\n"
                + "FROM " + DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(), owner.getDataSource(),
                tablesSource)
                + " t\n" + "WHERE t.OWNER =?\n" + "AND NESTED = 'NO'\n";
            String viewQuery =
                "SELECT o.OWNER, o.OBJECT_NAME, 'VIEW' AS OBJECT_TYPE, o.STATUS, NULL, NULL, NULL, 'NO', NULL, NULL, o.TEMPORARY, o.SECONDARY, 'NO', 0\n"
                    + "FROM "
                    + DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(), getDataSource(), "OBJECTS")
                    + " o\n" + "WHERE o.OWNER =?\n" + "AND o.OBJECT_TYPE = 'VIEW'\n";
            String mviewQuery =
                "SELECT o.OWNER, o.OBJECT_NAME, 'MATERIALIZED VIEW' AS OBJECT_TYPE, o.STATUS, NULL, NULL, NULL, 'NO', NULL, NULL, o.TEMPORARY, o.SECONDARY, 'NO', 0\n"
                    + "FROM "
                    + DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(), getDataSource(), "OBJECTS")
                    + " o\n" + "WHERE o.OWNER =?\n" + "AND o.OBJECT_TYPE = 'MATERIALIZED VIEW'";
            String unionAll = "UNION ALL ";
            if (hasName) {
                sql.append("SELECT * FROM (");
            }
            if (object == null) {
                sql.append(tableQuery).append(unionAll).append(viewQuery).append(unionAll).append(mviewQuery);
            } else if (object instanceof DamengMaterializedView) {
                sql.append(mviewQuery);
            } else if (object instanceof DamengView) {
                sql.append(viewQuery);
            } else {
                sql.append(tableQuery);
            }
            if (hasName) {
                sql.append(") WHERE OBJECT_NAME").append("=?");
            } else if (object != null) {
                if (object instanceof DamengTable) {
                    sql.append(" AND t.TABLE_NAME=?");
                } else {
                    sql.append(" AND o.OBJECT_NAME=?");
                }
            }
            dbStat = session.prepareStatement(sql.toString());
            String ownerName = owner.getName();
            dbStat.setString(1, ownerName);
            if (object == null) {
                dbStat.setString(2, ownerName);
                dbStat.setString(3, ownerName);
                if (objectName != null) {
                    dbStat.setString(4, objectName);
                }
            } else {
                dbStat.setString(2, object.getName());
            }
            return dbStat;
        }
    }

    /**
     * Constraint cache implementation
     */
    class ConstraintCache extends
        JDBCCompositeCache<DamengSchema, DamengTableBase, DamengTableConstraint, DamengTableConstraintColumn> {
        ConstraintCache() {
            super(tableCache, DamengTableBase.class, DamengConstants.COL_TABLE_NAME,
                DamengConstants.COL_CONSTRAINT_NAME);
        }

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(JDBCSession session, DamengSchema owner,
                                                        DamengTableBase forTable) throws SQLException {

            boolean useSimpleConnection = CommonUtils
                .toBoolean(session.getDataSource().getContainer().getConnectionConfiguration()
                    .getProviderProperty(DamengConstants.PROP_METADATA_USE_SIMPLE_CONSTRAINTS));

            StringBuilder sql = new StringBuilder(500);
            JDBCPreparedStatement dbStat;

            if (owner.getDataSource().isAtLeastV11() && forTable != null && !useSimpleConnection) {

                sql.append("SELECT\r\n" + "    c.TABLE_NAME,\r\n" + "    c.CONSTRAINT_NAME,\r\n"
                    + "    c.CONSTRAINT_TYPE,\r\n" + "    c.STATUS,\r\n" + "    c.SEARCH_CONDITION,\r\n"
                    + "    (\r\n"
                    + "      SELECT LISTAGG(COLUMN_NAME || ':' || POSITION,',') WITHIN GROUP (ORDER BY \"POSITION\") \r\n"
                    + "      FROM ALL_CONS_COLUMNS col\r\n"
                    +
                    "      WHERE col.OWNER =? AND col.TABLE_NAME = ? AND col.CONSTRAINT_NAME = c.CONSTRAINT_NAME GROUP BY CONSTRAINT_NAME \r\n"
                    + "    ) COLUMN_NAMES_NUMS\r\n" + "FROM\r\n" + "    "
                    + DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(), getDataSource(),
                    "CONSTRAINTS")
                    + " c\r\n" + "WHERE\r\n" + "    c.CONSTRAINT_TYPE <> 'R'\r\n" + "    AND c.OWNER = ?\r\n"
                    + "    AND c.TABLE_NAME = ?");
                // 1- owner
                // 2-table name
                // 3-owner
                // 4-table name

                dbStat = session.prepareStatement(sql.toString());
                dbStat.setString(1, DamengSchema.this.getName());
                dbStat.setString(2, forTable.getName());
                dbStat.setString(3, DamengSchema.this.getName());
                dbStat.setString(4, forTable.getName());

            } else if (owner.getDataSource().isAtLeastV10() && forTable != null && !useSimpleConnection) {

                sql.append("SELECT\r\n" + "    c.TABLE_NAME,\r\n" + "    c.CONSTRAINT_NAME,\r\n"
                    + "    c.CONSTRAINT_TYPE,\r\n" + "    c.STATUS,\r\n" + "    c.SEARCH_CONDITION,\r\n"
                    + "    (\r\n"
                    +
                    "        SELECT LTRIM(MAX(SYS_CONNECT_BY_PATH(cname || ':' || NVL(p,1),','))    KEEP (DENSE_RANK LAST ORDER BY curr),',') \r\n"
                    + "        FROM   (SELECT \r\n"
                    + "                       col.CONSTRAINT_NAME cn,col.POSITION p,col.COLUMN_NAME cname,\r\n"
                    + "                       ROW_NUMBER() OVER (PARTITION BY col.CONSTRAINT_NAME ORDER BY col.POSITION) AS curr,\r\n"
                    + "                       ROW_NUMBER() OVER (PARTITION BY col.CONSTRAINT_NAME ORDER BY col.POSITION) -1 AS prev\r\n"
                    + "                FROM   "
                    + DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(), getDataSource(),
                    "CONS_COLUMNS")
                    + " col \r\n" + "                WHERE  col.OWNER =? AND col.TABLE_NAME = ? \r\n"
                    +
                    "                ) WHERE cn = c.CONSTRAINT_NAME  GROUP BY cn CONNECT BY prev = PRIOR curr AND cn = PRIOR cn START WITH curr = 1      \r\n"
                    + "        ) COLUMN_NAMES_NUMS\r\n" + "FROM\r\n" + "    "
                    + DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(), getDataSource(),
                    "CONSTRAINTS")
                    + " c\r\n" + "WHERE\r\n" + "    c.CONSTRAINT_TYPE <> 'R'\r\n" + "    AND c.OWNER = ?\r\n"
                    + "    AND c.TABLE_NAME = ?");
                // 1- owner
                // 2-table name
                // 3-owner
                // 4-table name

                dbStat = session.prepareStatement(sql.toString());
                dbStat.setString(1, DamengSchema.this.getName());
                dbStat.setString(2, forTable.getName());
                dbStat.setString(3, DamengSchema.this.getName());
                dbStat.setString(4, forTable.getName());

            } else {
                sql.append("SELECT ").append(DamengUtils.getSysCatalogHint(owner.getDataSource())).append("\n"
                    + "c.TABLE_NAME, c.CONSTRAINT_NAME,c.CONSTRAINT_TYPE,c.STATUS,c.SEARCH_CONDITION,"
                    + "col.COLUMN_NAME,col.POSITION\n" + "FROM "
                    + DamengUtils
                    .getAdminAllViewPrefix(session.getProgressMonitor(), getDataSource(), "CONSTRAINTS")
                    + " c, "
                    + DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(), getDataSource(),
                    "CONS_COLUMNS")
                    + " col\n"
                    + "WHERE c.CONSTRAINT_TYPE<>'R' AND c.OWNER=? AND c.OWNER=col.OWNER AND c.CONSTRAINT_NAME=col.CONSTRAINT_NAME");
                if (forTable != null) {
                    sql.append(" AND c.TABLE_NAME=?");
                }
                sql.append("\nORDER BY c.CONSTRAINT_NAME,col.POSITION");

                dbStat = session.prepareStatement(sql.toString());
                dbStat.setString(1, DamengSchema.this.getName());
                if (forTable != null) {
                    dbStat.setString(2, forTable.getName());
                }
            }
            return dbStat;
        }

        @Nullable
        @Override
        protected DamengTableConstraint fetchObject(JDBCSession session, DamengSchema owner, DamengTableBase parent,
                                                    String indexName, JDBCResultSet dbResult) throws SQLException, DBException {
            return new DamengTableConstraint(parent, dbResult);
        }

        @Nullable
        @Override
        protected DamengTableConstraintColumn[] fetchObjectRow(JDBCSession session, DamengTableBase parent,
                                                               DamengTableConstraint object, JDBCResultSet dbResult)
            throws SQLException, DBException {
            // resultset has field COLUMN_NAMES_NUMS - special query was used
            if (JDBCUtils.safeGetString(dbResult, "COLUMN_NAMES_NUMS") != null) {

                List<SpecialPosition> positions = parsePositions(
                    JDBCUtils.safeGetString(dbResult, "COLUMN_NAMES_NUMS"));

                DamengTableConstraintColumn[] result = new DamengTableConstraintColumn[positions.size()];

                for (int idx = 0; idx < positions.size(); idx++) {

                    final DamengTableColumn column = getTableColumn(session, parent, dbResult,
                        positions.get(idx).getColumn());

                    if (column == null) {
                        continue;
                    }

                    result[idx] = new DamengTableConstraintColumn(object, column, positions.get(idx).getPos());
                }

                return result;

            } else {

                final DamengTableColumn tableColumn = getTableColumn(session, parent, dbResult,
                    JDBCUtils.safeGetStringTrimmed(dbResult, "COLUMN_NAME"));
                return tableColumn == null ? null
                    : new DamengTableConstraintColumn[] {new DamengTableConstraintColumn(object, tableColumn,
                    JDBCUtils.safeGetInt(dbResult, "POSITION"))};
            }
        }

        @Override
        protected void cacheChildren(DBRProgressMonitor monitor, DamengTableConstraint constraint,
                                     List<DamengTableConstraintColumn> rows) {
            constraint.setColumns(rows);
        }
    }

    class SpecialPosition {

        private final String column;

        private final int pos;

        public SpecialPosition(String value) {

            String data[] = value.split(":");

            this.column = data[0];

            this.pos = data.length == 1 ? 0 : Integer.valueOf(data[1]);

        }

        public SpecialPosition(String column, int pos) {
            this.column = column;
            this.pos = pos;
        }

        public String getColumn() {
            return column;
        }

        public int getPos() {
            return pos;
        }

    }

    class ForeignKeyCache
        extends JDBCCompositeCache<DamengSchema, DamengTable, DamengTableForeignKey, DamengTableForeignKeyColumn> {

        ForeignKeyCache() {
            super(tableCache, DamengTable.class, DamengConstants.COL_TABLE_NAME, DamengConstants.COL_CONSTRAINT_NAME);

        }

        @Override
        protected void loadObjects(DBRProgressMonitor monitor, DamengSchema schema, DamengTable forParent)
            throws DBException {

            // Cache schema constraints if not table specified
            if (forParent == null) {
                constraintCache.getAllObjects(monitor, schema);
            }
            super.loadObjects(monitor, schema, forParent);
        }

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(JDBCSession session, DamengSchema owner, DamengTable forTable)
            throws SQLException {
            boolean useSimpleConnection = CommonUtils
                .toBoolean(session.getDataSource().getContainer().getConnectionConfiguration()
                    .getProviderProperty(DamengConstants.PROP_METADATA_USE_SIMPLE_CONSTRAINTS));

            StringBuilder sql = new StringBuilder(500);
            JDBCPreparedStatement dbStat;
            String constraintsView = DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(), getDataSource(),
                "CONSTRAINTS");
            if (owner.getDataSource().isAtLeastV11() && forTable != null && !useSimpleConnection) {
                sql.append("SELECT \r\n" + "    c.TABLE_NAME,\r\n" + "    c.CONSTRAINT_NAME,\r\n"
                    + "    c.CONSTRAINT_TYPE,\r\n" + "    c.STATUS,\r\n" + "    c.R_OWNER,\r\n"
                    + "    c.R_CONSTRAINT_NAME,\r\n" + "    rc.TABLE_NAME AS R_TABLE_NAME,\r\n"
                    + "    c.DELETE_RULE,\r\n" + "    (\r\n"
                    + "      SELECT LISTAGG(COLUMN_NAME || ':' || POSITION,',') WITHIN GROUP (ORDER BY \"POSITION\") \r\n"
                    + "      FROM ALL_CONS_COLUMNS col\r\n"
                    +
                    "      WHERE col.OWNER =? AND col.TABLE_NAME = ? AND col.CONSTRAINT_NAME = c.CONSTRAINT_NAME GROUP BY CONSTRAINT_NAME \r\n"
                    + "    ) COLUMN_NAMES_NUMS\r\nFROM " + constraintsView + " c\r\n" + "LEFT JOIN "
                    + constraintsView + " rc\r\n"
                    + "ON rc.OWNER = c.r_OWNER AND rc.CONSTRAINT_NAME = c.R_CONSTRAINT_NAME AND rc.CONSTRAINT_TYPE='P'\r\n"
                    + "WHERE c.OWNER = ? AND c.TABLE_NAME = ? AND c.CONSTRAINT_TYPE = 'R'");
                // 1- owner
                // 2-table name
                // 3-owner
                // 4-table name

                dbStat = session.prepareStatement(sql.toString());
                dbStat.setString(1, DamengSchema.this.getName());
                dbStat.setString(2, forTable.getName());
                dbStat.setString(3, DamengSchema.this.getName());
                dbStat.setString(4, forTable.getName());

            } else {
                String consColumnsView = DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(),
                    getDataSource(), "CONS_COLUMNS");

                if (owner.getDataSource().isAtLeastV10() && forTable != null && !useSimpleConnection) {
                    sql.append("SELECT c.TABLE_NAME,c.CONSTRAINT_NAME,c.CONSTRAINT_TYPE,\r\n"
                        + "    c.STATUS,c.R_OWNER,c.R_CONSTRAINT_NAME,\r\n" + "    (SELECT rc.TABLE_NAME FROM "
                        + constraintsView
                        + " rc WHERE rc.OWNER = c.r_OWNER AND rc.CONSTRAINT_NAME = c.R_CONSTRAINT_NAME) AS R_TABLE_NAME,\r\n"
                        + "    c.DELETE_RULE,\r\n" + "    (\r\n"
                        +
                        "        SELECT LTRIM(MAX(SYS_CONNECT_BY_PATH(cname || ':' || p,','))    KEEP (DENSE_RANK LAST ORDER BY curr),',') \r\n"
                        + "        FROM   (SELECT \r\n"
                        + "                       col.CONSTRAINT_NAME cn,col.POSITION p,col.COLUMN_NAME cname,\r\n"
                        + "                       ROW_NUMBER() OVER (PARTITION BY col.CONSTRAINT_NAME ORDER BY col.POSITION) AS curr,\r\n"
                        + "                       ROW_NUMBER() OVER (PARTITION BY col.CONSTRAINT_NAME ORDER BY col.POSITION) -1 AS prev\r\n"
                        + "                FROM   " + consColumnsView + " col \r\n"
                        + "                WHERE  col.OWNER =? AND col.TABLE_NAME = ? \r\n"
                        +
                        "                )  WHERE cn = c.CONSTRAINT_NAME GROUP BY cn CONNECT BY prev = PRIOR curr AND cn = PRIOR cn START WITH curr = 1      \r\n"
                        + "        ) COLUMN_NAMES_NUMS\r\n" + "FROM " + constraintsView + " c\r\n"
                        + "WHERE c.OWNER = ? AND c.TABLE_NAME = ? AND c.CONSTRAINT_TYPE = 'R'");
                    // 1- owner
                    // 2-table name
                    // 3-owner
                    // 4-table name

                    dbStat = session.prepareStatement(sql.toString());
                    dbStat.setString(1, DamengSchema.this.getName());
                    dbStat.setString(2, forTable.getName());
                    dbStat.setString(3, DamengSchema.this.getName());
                    dbStat.setString(4, forTable.getName());

                } else {

                    sql.append("SELECT " + DamengUtils.getSysCatalogHint(owner.getDataSource()) + " \r\n"
                        +
                        "c.TABLE_NAME, c.CONSTRAINT_NAME,c.CONSTRAINT_TYPE,c.STATUS,c.R_OWNER,c.R_CONSTRAINT_NAME,rc.TABLE_NAME as R_TABLE_NAME,c.DELETE_RULE, \n"
                        + "col.COLUMN_NAME,col.POSITION\r\n" + "FROM " + constraintsView + " c, " + consColumnsView
                        + " col, " + constraintsView + " rc\n" + "WHERE c.CONSTRAINT_TYPE='R' AND c.OWNER=?\n"
                        + "AND c.OWNER=col.OWNER AND c.CONSTRAINT_NAME=col.CONSTRAINT_NAME\n"
                        + "AND rc.OWNER=c.r_OWNER AND rc.CONSTRAINT_NAME=c.R_CONSTRAINT_NAME");
                    if (forTable != null) {
                        sql.append(" AND c.TABLE_NAME=?");
                    }
                    sql.append("\r\nORDER BY c.CONSTRAINT_NAME,col.POSITION");

                    dbStat = session.prepareStatement(sql.toString());
                    dbStat.setString(1, DamengSchema.this.getName());
                    if (forTable != null) {
                        dbStat.setString(2, forTable.getName());
                    }
                }
            }
            return dbStat;
        }

        @Nullable
        @Override
        protected DamengTableForeignKey fetchObject(JDBCSession session, DamengSchema owner, DamengTable parent,
                                                    String indexName, JDBCResultSet dbResult) throws SQLException, DBException {
            return new DamengTableForeignKey(session.getProgressMonitor(), parent, dbResult);
        }

        @Nullable
        @Override
        protected DamengTableForeignKeyColumn[] fetchObjectRow(JDBCSession session, DamengTable parent,
                                                               DamengTableForeignKey object, JDBCResultSet dbResult)
            throws SQLException, DBException {

            // resultset has field COLUMN_NAMES_NUMS - special query was used
            if (JDBCUtils.safeGetString(dbResult, "COLUMN_NAMES_NUMS") != null) {

                List<SpecialPosition> positions = parsePositions(
                    JDBCUtils.safeGetString(dbResult, "COLUMN_NAMES_NUMS"));

                DamengTableForeignKeyColumn[] result = new DamengTableForeignKeyColumn[positions.size()];

                for (int idx = 0; idx < positions.size(); idx++) {

                    DamengTableColumn column = getTableColumn(session, parent, dbResult,
                        positions.get(idx).getColumn());

                    if (column == null) {
                        continue;
                    }

                    result[idx] = new DamengTableForeignKeyColumn(object, column, positions.get(idx).getPos());
                }

                return result;

            } else {

                DamengTableColumn column = getTableColumn(session, parent, dbResult,
                    JDBCUtils.safeGetStringTrimmed(dbResult, "COLUMN_NAME"));

                if (column == null) {
                    return null;
                }

                return new DamengTableForeignKeyColumn[] {
                    new DamengTableForeignKeyColumn(object, column, JDBCUtils.safeGetInt(dbResult, "POSITION"))};
            }
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        protected void cacheChildren(DBRProgressMonitor monitor, DamengTableForeignKey foreignKey,
                                     List<DamengTableForeignKeyColumn> rows) {
            foreignKey.setColumns((List) rows);
        }
    }

    /**
     * Index cache implementation
     */
    class IndexCache
        extends JDBCCompositeCache<DamengSchema, DamengTablePhysical, DamengTableIndex, DamengTableIndexColumn> {
        IndexCache() {
            super(tableCache, DamengTablePhysical.class, "TABLE_NAME", "INDEX_NAME");
        }

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(JDBCSession session, DamengSchema owner,
                                                        DamengTablePhysical forTable) throws SQLException {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ").append(DamengUtils.getSysCatalogHint(owner.getDataSource())).append(
                " distinct IND_OBJ.NAME, IND_OBJ.ID, INDS.ISUNIQUE, INDS.XTYPE, INDS.GROUPID, INDS.TYPE$, INDS.INIT_EXTENTS, INDS.BATCH_ALLOC, INDS.MIN_EXTENTS, FBI_DEF(IND_OBJ.ID), IND_OBJ.CRTDATE, SCH_OBJ.ID, SCH_OBJ.NAME, TAB_OBJ.ID, TAB_OBJ.NAME TABLE_NAME, INDEX_USED_PAGES(IND_OBJ.ID)*(PAGE/1024),IND_OBJ.VALID, INDEX_USED_SPACE(IND_OBJ.ID)*(PAGE/1024),\n"
                    +
                    "(select MONITORING FROM V$OBJECT_USAGE where INDEX_NAME=IND_OBJ.NAME and SCH_NAME = SCH_OBJ.NAME) MONITORING, IND_OBJ.INFO7, i.TABLESPACE_NAME, ic.INDEX_NAME, ic.COLUMN_NAME, ic.COLUMN_POSITION, ic.DESCEND\n"
                    +
                    "from (select * from SYSINDEXES where ROOTFILE != -1 or (XTYPE & 0x1000) = 0x1000 or (XTYPE & 0x2000) = 0x2000 or (XTYPE & 0x08) = 0x08 or (FLAG & 0x08) = 0x08 or (XTYPE & 0x8000) = 0x8000 or (XTYPE & 0x40) = 0x40) INDS, SYSCOLUMNS COLS, ALL_INDEXES i, \n"
                    +
                    "(select distinct IND_OBJ_INNER.ID, IND_OBJ_INNER.NAME, IND_OBJ_INNER.CRTDATE, IND_OBJ_INNER.PID, IND_OBJ_INNER.VALID, IND_OBJ_INNER.INFO7 from SYSOBJECTS IND_OBJ_INNER where IND_OBJ_INNER.SUBTYPE$ = 'INDEX') IND_OBJ, (select ID, NAME, SCHID from SYSOBJECTS where TYPE$='SCHOBJ' and SUBTYPE$ like '_TAB' "
                    + ((forTable == null) ? " " : " and  NAME = ? ")
                    +
                    "and SCHID = ?) TAB_OBJ, (select ID, NAME from SYSOBJECTS where TYPE$='SCH' and  ID = ?) SCH_OBJ, ALL_IND_COLUMNS ic where INDS.ID=IND_OBJ.ID and IND_OBJ.PID=TAB_OBJ.ID and TAB_OBJ.SCHID=SCH_OBJ.ID  and i.owner = SCH_OBJ.name and i.index_name = IND_OBJ.NAME and ic.index_owner = SCH_OBJ.NAME AND ic.index_name = IND_OBJ.name "
                    +
                    "and COLS.ID = IND_OBJ.PID and (SF_COL_IS_IDX_KEY(INDS.KEYNUM, INDS.KEYINFO, COLS.COLID)=1 or (INDS.XTYPE & 0x1000) = 0x1000 or (INDS.XTYPE & 0x2000) = 0x2000 or (XTYPE & 0x08) = 0x08) ORDER BY IND_OBJ.NAME\n");
            JDBCPreparedStatement dbStat = session.prepareStatement(sql.toString());
            if (forTable == null) {
                dbStat.setLong(1, DamengSchema.this.getId());
                dbStat.setLong(2, DamengSchema.this.getId());
            } else {
                dbStat.setString(1, forTable.getName());
                dbStat.setLong(2, DamengSchema.this.getId());
                dbStat.setLong(3, DamengSchema.this.getId());
            }
            return dbStat;
        }

        @Nullable
        @Override
        protected DamengTableIndex fetchObject(JDBCSession session, DamengSchema owner, DamengTablePhysical parent,
                                               String indexName, JDBCResultSet dbResult) throws SQLException, DBException {
            return new DamengTableIndex(owner, parent, indexName, dbResult);
        }

        @Nullable
        @Override
        protected DamengTableIndexColumn[] fetchObjectRow(JDBCSession session, DamengTablePhysical parent,
                                                          DamengTableIndex object, JDBCResultSet dbResult)
            throws SQLException, DBException {
            String columnName = JDBCUtils.safeGetStringTrimmed(dbResult, "COLUMN_NAME");
            int ordinalPosition = JDBCUtils.safeGetInt(dbResult, "COLUMN_POSITION");
            boolean isAscending = "ASC".equals(JDBCUtils.safeGetStringTrimmed(dbResult, "DESCEND"));
            DamengTableColumn tableColumn = columnName == null ? null
                : parent.getAttribute(session.getProgressMonitor(), columnName);
            if (tableColumn == null) {
                log.debug("Column '" + columnName + "' not found in table '" + parent.getName() + "' for index '"
                    + object.getName() + "'");
                return null;
            }

            return new DamengTableIndexColumn[] {
                new DamengTableIndexColumn(object, tableColumn, ordinalPosition, isAscending)};
        }

        @Override
        protected void cacheChildren(DBRProgressMonitor monitor, DamengTableIndex index,
                                     List<DamengTableIndexColumn> rows) {
            index.setColumns(rows);
        }
    }

    class ViewTriggerCache
        extends JDBCCompositeCache<DamengSchema, DamengView, DamengViewTrigger, DamengTriggerColumn> {
        protected ViewTriggerCache() {
            super(tableCache, DamengView.class, "TABLE_NAME", "TRIGGER_NAME");
        }

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(JDBCSession session, DamengSchema schema, DamengView view)
            throws SQLException {
            final JDBCPreparedStatement dbStmt = session.prepareStatement("SELECT t.*, s.crtdate\n" + "FROM "
                + DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(), schema.getDataSource(),
                "TRIGGERS t")
                + " , SYSOBJECTS s WHERE OWNER=? AND TRIM(BASE_OBJECT_TYPE) IN ('VIEW') AND s.name = t.trigger_name "
                + (view == null ? "" : " AND TABLE_NAME = '" + view.getName() + "'") + " ORDER BY TRIGGER_NAME");
            dbStmt.setString(1, schema.getName());
            return dbStmt;
        }

        @Nullable
        @Override
        protected DamengViewTrigger fetchObject(JDBCSession session, DamengSchema schema, DamengView view,
                                                String childName, JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengViewTrigger(view, resultSet);
        }

        @Nullable
        @Override
        protected DamengTriggerColumn[] fetchObjectRow(JDBCSession session, DamengView view, DamengViewTrigger trigger,
                                                       JDBCResultSet resultSet) throws DBException {
            return null;
        }

        @Override
        protected void cacheChildren(DBRProgressMonitor monitor, DamengViewTrigger trigger,
                                     List<DamengTriggerColumn> columns) {
            trigger.setColumns(columns);
        }

        @Override
        protected boolean isEmptyObjectRowsAllowed() {
            return true;
        }
    }

    class TableTriggerCache
        extends JDBCCompositeCache<DamengSchema, DamengTableBase, DamengTableTrigger, DamengTriggerColumn> {
        protected TableTriggerCache() {
            super(tableCache, DamengTableBase.class, "TABLE_NAME", "TRIGGER_NAME");
        }

        @NotNull
        @Override

        protected JDBCStatement prepareObjectsStatement(JDBCSession session, DamengSchema schema, DamengTableBase table)
            throws SQLException {
            final JDBCPreparedStatement dbStmt = session.prepareStatement("SELECT"
                + DamengUtils.getSysCatalogHint(schema.getDataSource())
                + " t.*, s.CRTDATE, c.*, c.COLUMN_NAME AS TRIGGER_COLUMN_NAME" + "\nFROM "
                + DamengUtils
                .getAdminAllViewPrefix(session.getProgressMonitor(), schema.getDataSource(), "TRIGGERS")
                + " t, "
                + DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(), schema.getDataSource(),
                "TRIGGER_COLS")
                + " c" + ", SYSOBJECTS s " + "\nWHERE t.TABLE_OWNER=?"
                + (table == null ? "" : " AND t.TABLE_NAME=?") + " AND t.BASE_OBJECT_TYPE="
                + (table instanceof DamengView ? "'VIEW'" : "'TABLE'")
                + " AND t.TABLE_OWNER=c.TABLE_OWNER(+) AND t.TABLE_NAME=c.TABLE_NAME(+)"
                + " AND t.OWNER=c.TRIGGER_OWNER(+) AND t.TRIGGER_NAME=c.TRIGGER_NAME(+)"
                + " AND s.NAME = t.TRIGGER_NAME " + "\nORDER BY t.TRIGGER_NAME");

            dbStmt.setString(1, schema.getName());
            if (table != null) {
                dbStmt.setString(2, table.getName());
            }
            return dbStmt;
        }

        @Nullable
        @Override
        protected DamengTableTrigger fetchObject(JDBCSession session, DamengSchema schema, DamengTableBase table,
                                                 String childName, JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengTableTrigger(table, resultSet);
        }

        @Nullable
        @Override
        protected DamengTriggerColumn[] fetchObjectRow(JDBCSession session, DamengTableBase table,
                                                       DamengTableTrigger trigger, JDBCResultSet resultSet) throws DBException {
            final DamengTableBase refTable = DamengTableBase.findTable(session.getProgressMonitor(),
                table.getDataSource(), JDBCUtils.safeGetString(resultSet, "TABLE_OWNER"),
                JDBCUtils.safeGetString(resultSet, "TABLE_NAME"));
            if (refTable != null) {
                final String columnName = JDBCUtils.safeGetString(resultSet, "TRIGGER_COLUMN_NAME");
                if (columnName == null) {
                    return null;
                }
                final DamengTableColumn tableColumn = refTable.getAttribute(session.getProgressMonitor(), columnName);
                if (tableColumn == null) {
                    log.debug("Column '" + columnName + "' not found in table '"
                        + refTable.getFullyQualifiedName(DBPEvaluationContext.DDL) + "' for trigger '"
                        + trigger.getName() + "'");
                    return null;
                }
                return new DamengTriggerColumn[] {
                    new DamengTriggerColumn(session.getProgressMonitor(), trigger, tableColumn, resultSet)};
            }
            return null;
        }

        @Override
        protected void cacheChildren(DBRProgressMonitor monitor, DamengTableTrigger trigger,
                                     List<DamengTriggerColumn> columns) {
            trigger.setColumns(columns);
        }

        @Override
        protected boolean isEmptyObjectRowsAllowed() {
            return true;
        }
    }

}
