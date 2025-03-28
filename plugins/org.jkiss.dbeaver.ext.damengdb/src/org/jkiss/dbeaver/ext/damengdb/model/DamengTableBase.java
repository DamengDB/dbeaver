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
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.damengdb.model.source.DamengStatefulObject;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCStatement;
import org.jkiss.dbeaver.model.impl.DBObjectNameCaseTransformer;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCObjectCache;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCStructCache;
import org.jkiss.dbeaver.model.impl.jdbc.struct.JDBCTable;
import org.jkiss.dbeaver.model.impl.jdbc.struct.JDBCTableColumn;
import org.jkiss.dbeaver.model.meta.*;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectState;
import org.jkiss.dbeaver.model.struct.cache.DBSObjectCache;
import org.jkiss.dbeaver.model.struct.rdb.DBSTableForeignKey;
import org.jkiss.dbeaver.model.struct.rdb.DBSTableIndex;
import org.jkiss.dbeaver.model.struct.rdb.DBSTrigger;
import org.jkiss.utils.CommonUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * DamengTable base
 */
public abstract class DamengTableBase extends JDBCTable<DamengDataSource, DamengSchema>
    implements DBPNamedObject2, DBPRefreshableObject, DamengStatefulObject, DBPObjectWithLazyDescription {
    
	private static final Log log = Log.getLog(DamengTableBase.class);
    private final TablePrivCache tablePrivCache = new TablePrivCache();
    protected boolean valid;
    private Date created;
    private Date lastDDLTime;
    private String comment;

    protected DamengTableBase(DamengSchema schema, String name, boolean persisted) {
        super(schema, name, persisted);
    }

    protected DamengTableBase(DamengSchema dmSchema, ResultSet dbResult) {
        super(dmSchema, true);
        setName(JDBCUtils.safeGetString(dbResult, DamengConstants.COLUMN_OBJECT_NAME));
        this.valid = DamengConstants.RESULT_STATUS_VALID
            .equals(JDBCUtils.safeGetString(dbResult, DamengConstants.COLUMN_STATUS));
        this.created = JDBCUtils.safeGetTimestamp(dbResult, DamengConstants.COLUMN_CREATED);
        this.lastDDLTime = JDBCUtils.safeGetTimestamp(dbResult, DamengConstants.COLUMN_LAST_DDL_TIME);
        // this.comment = JDBCUtils.safeGetString(dbResult, "COMMENTS");
    }

    protected DamengTableBase(@NotNull DamengSchema dmSchema, @NotNull String name) {
        // Table partition
        super(dmSchema, true);
        setName(name);
        this.valid = true;
    }

    public static DamengTableBase findTable(DBRProgressMonitor monitor, DamengDataSource dataSource, String ownerName,
                                            String tableName) throws DBException {
        DamengSchema refSchema = dataSource.getSchema(monitor, ownerName);
        if (refSchema == null) {
            log.warn("Referenced schema '" + ownerName + "' not found");
            return null;
        } else {
            DamengTableBase refTable = refSchema.tableCache.getObject(monitor, refSchema, tableName);
            if (refTable == null) {
                log.warn("Referenced table '" + tableName + "' not found in schema '" + ownerName + "'");
            }
            return refTable;
        }
    }

    public abstract TableAdditionalInfo getAdditionalInfo();

    protected abstract String getTableTypeName();

    @SuppressWarnings("rawtypes")
    @Override
    public JDBCStructCache<DamengSchema, ? extends JDBCTable, ? extends JDBCTableColumn> getCache() {
        return getContainer().tableCache;
    }

    @Override
    @NotNull
    public DamengSchema getSchema() {
        return super.getContainer();
    }

    @NotNull
    @Override
    @Property(viewable = true, editable = true, valueTransformer = DBObjectNameCaseTransformer.class, order = 1)
    public String getName() {
        return super.getName();
    }

    @Nullable
    @Override
    public String getDescription() {
        return getComment();
    }

    @Property(viewable = true, order = 13, visibleIf = DamengTableNotPartitionPropertyValidator.class)
    public Date getCreated() {
        return created;
    }

    @Property(viewable = true, order = 14, visibleIf = DamengTableNotPartitionPropertyValidator.class)
    public Date getLastDDLTime() {
        return lastDDLTime;
    }

    @NotNull
    @Override
    public String getFullyQualifiedName(DBPEvaluationContext context) {
        return DBUtils.getFullQualifiedName(getDataSource(), getContainer(), this);
    }

    @Property(viewable = true, editable = true, updatable = true, length = PropertyLength.MULTILINE, order = 100, visibleIf = DamengTableNotPartitionPropertyValidator.class)
    @LazyProperty(cacheValidator = CommentsValidator.class)
    public String getComment(DBRProgressMonitor monitor) {
        if (comment == null) {
            comment = "";
            if (isPersisted()) {
                try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Load table comments")) {
                    comment = queryTableComment(session);
                    if (comment == null) {
                        comment = "";
                    }
                } catch (Exception e) {
                    log.error("Can't fetch table '" + getName() + "' comment", e);
                }
            }
        }
        return comment;
    }

    @Nullable
    @Override
    public String getDescription(DBRProgressMonitor monitor) {
        return getComment(monitor);
    }

    @Association
    public Collection<DamengDependencyGroup> getDependencies(DBRProgressMonitor monitor) {
        return DamengDependencyGroup.of(this);
    }

    @Association
    public List<? extends DamengTableColumn> getCachedAttributes() {
        final DBSObjectCache<DamengTableBase, DamengTableColumn> childrenCache = getContainer().getTableCache()
            .getChildrenCache(this);
        if (childrenCache != null) {
            return childrenCache.getCachedObjects();
        }
        return Collections.emptyList();
    }

    protected String queryTableComment(JDBCSession session) throws SQLException {
        return JDBCUtils.queryString(session,
            "SELECT COMMENTS FROM "
                + DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(),
                (DamengDataSource) session.getDataSource(), "TAB_COMMENTS")
                + " " + "WHERE OWNER=? AND TABLE_NAME=? AND TABLE_TYPE=?",
            getSchema().getName(), getName(), getTableTypeName());
    }

    void loadColumnComments(DBRProgressMonitor monitor) {
        try {
            try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Load table column comments")) {
                try (JDBCPreparedStatement stat = session.prepareStatement("SELECT COLUMN_NAME,COMMENTS FROM "
                    + DamengUtils.getAdminAllViewPrefix(session.getProgressMonitor(),
                    (DamengDataSource) session.getDataSource(), "COL_COMMENTS")
                    + " cc " + "WHERE CC.SCHEMA_NAME=? AND cc.TABLE_NAME=?")) {
                    stat.setString(1, getSchema().getName());
                    stat.setString(2, getName());
                    try (JDBCResultSet resultSet = stat.executeQuery()) {
                        while (resultSet.next()) {
                            String colName = resultSet.getString(1);
                            String colComment = resultSet.getString(2);
                            DamengTableColumn col = getAttribute(monitor, colName);
                            if (col == null) {
                                log.warn("Column '" + colName + "' not found in table '"
                                    + getFullyQualifiedName(DBPEvaluationContext.DDL) + "'");
                            } else {
                                col.setComment(CommonUtils.notEmpty(colComment));
                            }
                        }
                    }
                }
            }
            for (DamengTableColumn col : CommonUtils.safeCollection(getAttributes(monitor))) {
                col.cacheComment();
            }
        } catch (Exception e) {
            log.warn("Error fetching table '" + getName() + "' column comments", e);
        }
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public List<DamengTableColumn> getAttributes(@NotNull DBRProgressMonitor monitor) throws DBException {
        return getContainer().tableCache.getChildren(monitor, getContainer(), this);
    }

    @Override
    public DamengTableColumn getAttribute(@NotNull DBRProgressMonitor monitor, @NotNull String attributeName)
        throws DBException {
        return getContainer().tableCache.getChild(monitor, getContainer(), this, attributeName);
    }

    @Override
    public DBSObject refreshObject(@NotNull DBRProgressMonitor monitor) throws DBException {
        getContainer().constraintCache.clearObjectCache(this);
        getContainer().tableTriggerCache.clearObjectCache(this);

        return getContainer().tableCache.refreshObject(monitor, getContainer(), this);
    }

    @Association
    @Override
    public List<? extends DBSTrigger> getTriggers(@NotNull DBRProgressMonitor monitor) throws DBException {
        return getSchema().tableTriggerCache.getObjects(monitor, getSchema(), this);
    }

    @Override
    public Collection<? extends DBSTableIndex> getIndexes(DBRProgressMonitor monitor) throws DBException {
        return null;
    }

    @Nullable
    @Override
    @Association
    public Collection<DamengTableConstraint> getConstraints(@NotNull DBRProgressMonitor monitor) throws DBException {
        return getContainer().constraintCache.getObjects(monitor, getContainer(), this);
    }

    public DamengTableConstraint getConstraint(DBRProgressMonitor monitor, String ukName) throws DBException {
        return getContainer().constraintCache.getObject(monitor, getContainer(), this, ukName);
    }

    public DBSTableForeignKey getForeignKey(DBRProgressMonitor monitor, String ukName) throws DBException {
        return DBUtils.findObject(getAssociations(monitor), ukName);
    }

    @Override
    public Collection<DamengTableForeignKey> getAssociations(@NotNull DBRProgressMonitor monitor) throws DBException {
        return null;
    }

    @Override
    public Collection<DamengTableForeignKey> getReferences(@NotNull DBRProgressMonitor monitor) throws DBException {
        return null;
    }

    public String getDDL(DBRProgressMonitor monitor, DamengDDLFormat ddlFormat, Map<String, Object> options)
        throws DBException {
        return DamengUtils.getDDL(monitor, getTableTypeName(), this, ddlFormat, options);
    }

    @NotNull
    @Override
    public DBSObjectState getObjectState() {
        return valid ? DBSObjectState.NORMAL : DBSObjectState.INVALID;
    }

    @Association
    public Collection<DamengPrivTable> getTablePrivs(DBRProgressMonitor monitor) throws DBException {
        return tablePrivCache.getAllObjects(monitor, this);
    }

    public static class TableAdditionalInfo {
        volatile boolean loaded = false;

        boolean isLoaded() {
            return loaded;
        }
    }

    public static class AdditionalInfoValidator implements IPropertyCacheValidator<DamengTableBase> {
        @Override
        public boolean isPropertyCached(DamengTableBase object, Object propertyId) {
            return object.getAdditionalInfo().isLoaded();
        }
    }

    public static class CommentsValidator implements IPropertyCacheValidator<DamengTableBase> {
        @Override
        public boolean isPropertyCached(DamengTableBase object, Object propertyId) {
            return object.comment != null;
        }
    }

    static class TablePrivCache extends JDBCObjectCache<DamengTableBase, DamengPrivTable> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session,
                                                        @NotNull DamengTableBase tableBase) throws SQLException {
            boolean hasDBA = tableBase.getDataSource().isViewAvailable(session.getProgressMonitor(),
                DamengConstants.SCHEMA_SYS, DamengConstants.VIEW_DBA_TAB_PRIVS);
            final JDBCPreparedStatement dbStat = session.prepareStatement("SELECT p.*\n" + "FROM "
                + (hasDBA ? "DBA_TAB_PRIVS p" : "ALL_TAB_PRIVS p") + "\n" + "WHERE p."
                + (hasDBA ? "OWNER" : "TABLE_SCHEMA") + "=? AND p.TABLE_NAME =? AND p.GRANTEE in \n"
                +
                "(select name from sysobjects where type$='UR' and subtype$='ROLE' and (info2 is null or info2!=1) and info1=0 ORDER BY NAME)");
            dbStat.setString(1, tableBase.getSchema().getName());
            dbStat.setString(2, tableBase.getName());
            return dbStat;
        }

        @Override
        protected DamengPrivTable fetchObject(@NotNull JDBCSession session, @NotNull DamengTableBase tableBase,
                                              @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengPrivTable(tableBase, resultSet);
        }
    }

}
