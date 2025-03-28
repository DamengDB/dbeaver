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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBConstants;
import org.jkiss.dbeaver.model.DBIcon;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBPImage;
import org.jkiss.dbeaver.model.DBPImageProvider;
import org.jkiss.dbeaver.model.DBPNamedObject;
import org.jkiss.dbeaver.model.DBPObjectStatistics;
import org.jkiss.dbeaver.model.DBPReferentialIntegrityController;
import org.jkiss.dbeaver.model.DBPScriptObject;
import org.jkiss.dbeaver.model.DBPScriptObjectExt2;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.data.DBDPseudoAttribute;
import org.jkiss.dbeaver.model.data.DBDPseudoAttributeContainer;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCStatement;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.LazyProperty;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.meta.PropertyGroup;
import org.jkiss.dbeaver.model.preferences.DBPPropertySource;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.utils.ArrayUtils;
import org.jkiss.utils.CommonUtils;

/**
 * DamengTable
 */
public class DamengTable extends DamengTablePhysical implements DBPScriptObject, DBDPseudoAttributeContainer,
    DBPObjectStatistics, DBPImageProvider, DBPReferentialIntegrityController, DBPScriptObjectExt2 {
	
    private static final Log log = Log.getLog(DamengTable.class);

    private static final CharSequence TABLE_NAME_PLACEHOLDER = "%table_name%";

    private static final CharSequence FOREIGN_KEY_NAME_PLACEHOLDER = "%foreign_key_name%";

    private static final String DISABLE_REFERENTIAL_INTEGRITY_STATEMENT = "ALTER TABLE " + TABLE_NAME_PLACEHOLDER
        + " MODIFY CONSTRAINT " + FOREIGN_KEY_NAME_PLACEHOLDER + " DISABLE";

    private static final String ENABLE_REFERENTIAL_INTEGRITY_STATEMENT = "ALTER TABLE " + TABLE_NAME_PLACEHOLDER
        + " MODIFY CONSTRAINT " + FOREIGN_KEY_NAME_PLACEHOLDER + " ENABLE";

    private static final String[] supportedOptions = new String[] {DBPScriptObject.OPTION_DDL_SKIP_FOREIGN_KEYS,
        DBPScriptObject.OPTION_DDL_ONLY_FOREIGN_KEYS};
    private final AdditionalInfo additionalInfo = new AdditionalInfo();
    private DamengDataType tableType;
    private String iotType;
    private String iotName;
    private boolean temporary;
    private boolean secondary;
    private boolean nested;
    private transient volatile Long tableSize;

    public DamengTable(DamengSchema schema, String name) {
        super(schema, name);
    }

    public DamengTable(DBRProgressMonitor monitor, DamengSchema schema, ResultSet dbResult) {
        super(schema, dbResult);
        String typeOwner = JDBCUtils.safeGetString(dbResult, "TABLE_TYPE_OWNER");
        if (!CommonUtils.isEmpty(typeOwner)) {
            tableType = DamengDataType.resolveDataType(monitor, schema.getDataSource(), typeOwner,
                JDBCUtils.safeGetString(dbResult, "TABLE_TYPE"));
        }
        this.iotType = JDBCUtils.safeGetString(dbResult, "IOT_TYPE");
        this.iotName = JDBCUtils.safeGetString(dbResult, "IOT_NAME");
        this.temporary = JDBCUtils.safeGetBoolean(dbResult, DamengConstants.COLUMN_TEMPORARY,
            DamengConstants.RESULT_YES_VALUE);
        this.secondary = JDBCUtils.safeGetBoolean(dbResult, "SECONDARY", DamengConstants.RESULT_YES_VALUE);
        this.nested = JDBCUtils.safeGetBoolean(dbResult, "NESTED", DamengConstants.RESULT_YES_VALUE);
        if (!CommonUtils.isEmpty(iotName)) {
            // this.setName(iotName);
        }
    }

    @Override
    public TableAdditionalInfo getAdditionalInfo() {
        return additionalInfo;
    }

    @PropertyGroup()
    @LazyProperty(cacheValidator = AdditionalInfoValidator.class)
    public AdditionalInfo getAdditionalInfo(DBRProgressMonitor monitor) throws DBException {
        synchronized (additionalInfo) {
            if (!additionalInfo.loaded && monitor != null) {
                loadAdditionalInfo(monitor);
            }
            return additionalInfo;
        }
    }

    @Override
    public boolean hasStatistics() {
        return tableSize != null;
    }

    @Override
    public long getStatObjectSize() {
        return tableSize == null ? 0 : tableSize;
    }

    @Nullable
    @Override
    public DBPPropertySource getStatProperties() {
        return null;
    }

    public Long getTableSize(DBRProgressMonitor monitor) throws DBCException {
        if (tableSize == null) {
            loadSize(monitor);
        }
        return tableSize;
    }

    public void setTableSize(Long tableSize) {
        this.tableSize = tableSize;
    }

    private void loadSize(DBRProgressMonitor monitor) throws DBCException {
        tableSize = null;
        try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Load table status")) {
            boolean hasDBA = getDataSource().isViewAvailable(monitor, DamengConstants.SCHEMA_SYS, "DBA_SEGMENTS");
            try (JDBCPreparedStatement dbStat = session.prepareStatement("SELECT SUM(bytes) TABLE_SIZE\n" + "FROM "
                + DamengUtils.getSysSchemaPrefix(getDataSource()) + (hasDBA ? "DBA_SEGMENTS" : "USER_SEGMENTS")
                + " s\n" + "WHERE S.SEGMENT_TYPE='TABLE' AND s.SEGMENT_NAME = ?"
                + (hasDBA ? " AND s.OWNER = ?" : ""))) {
                dbStat.setString(1, getName());
                if (hasDBA) {
                    dbStat.setString(2, getSchema().getName());
                }
                try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                    if (dbResult.next()) {
                        fetchTableSize(dbResult);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error reading table statistics", e);
        } finally {
            if (tableSize == null) {
                tableSize = 0L;
            }
        }
    }

    void fetchTableSize(JDBCResultSet dbResult) throws SQLException {
        tableSize = dbResult.getLong("TABLE_SIZE");
    }

    @Override
    protected String getTableTypeName() {
        return "TABLE";
    }

    @Override
    public boolean isView() {
        return false;
    }

    @Property(viewable = false, order = 5)
    public DamengDataType getTableType() {
        return tableType;
    }

    @Property(viewable = false, order = 10)
    public boolean isTemporary() {
        return temporary;
    }

    @Property(viewable = false, order = 11)
    public boolean isSecondary() {
        return secondary;
    }

    @Property(viewable = false, order = 12)
    public boolean isNested() {
        return nested;
    }

    @Override
    public DamengTableColumn getAttribute(@NotNull DBRProgressMonitor monitor, @NotNull String attributeName)
        throws DBException {
        return super.getAttribute(monitor, attributeName);
    }

    @Nullable
    private DamengTableColumn getXMLColumn(DBRProgressMonitor monitor) throws DBException {
        for (DamengTableColumn col : CommonUtils.safeCollection(getAttributes(monitor))) {
            if (col.getDataType() == tableType) {
                return col;
            }
        }
        return null;
    }

    @Override
    public Collection<DamengTableForeignKey> getReferences(@NotNull DBRProgressMonitor monitor) throws DBException {
        List<DamengTableForeignKey> refs = new ArrayList<>();
        // This is dummy implementation
        // Get references from this schema only
        final Collection<DamengTableForeignKey> allForeignKeys = getContainer().foreignKeyCache.getObjects(monitor,
            getContainer(), null);
        for (DamengTableForeignKey constraint : allForeignKeys) {
            if (constraint.getReferencedTable() == this) {
                refs.add(constraint);
            }
        }
        return refs;
    }

    @Override
    @Association
    public Collection<DamengTableForeignKey> getAssociations(@NotNull DBRProgressMonitor monitor) throws DBException {
        return getContainer().foreignKeyCache.getObjects(monitor, getContainer(), this);
    }

    @Override
    public DBSObject refreshObject(@NotNull DBRProgressMonitor monitor) throws DBException {
        getContainer().foreignKeyCache.clearObjectCache(this);
        if (tableSize != null) {
            tableSize = null;
            getTableSize(monitor);
        }
        additionalInfo.loaded = false;
        return super.refreshObject(monitor);
    }

    @Override
    public DBDPseudoAttribute[] getPseudoAttributes() throws DBException {
        if (CommonUtils.isEmpty(this.iotType)
            && getDataSource().getContainer().getPreferenceStore().getBoolean(DamengConstants.PREF_SUPPORT_ROWID)) {
            // IOT tables have index id instead of ROWID
            return new DBDPseudoAttribute[] {DamengConstants.PSEUDO_ATTR_ROWID};
        } else {
            return null;
        }
    }

    @Override
    protected void appendSelectSource(DBRProgressMonitor monitor, StringBuilder query, String tableAlias,
                                      DBDPseudoAttribute rowIdAttribute) throws DBCException {
        if (tableType != null && tableType.getName().equals(DamengConstants.TYPE_NAME_XML)) {
            try {
                DamengTableColumn xmlColumn = getXMLColumn(monitor);
                if (xmlColumn != null) {
                    query.append("XMLType(").append(tableAlias).append(".").append(xmlColumn.getName())
                        .append(".getClobval()) as ").append(xmlColumn.getName());
                    if (rowIdAttribute != null) {
                        query.append(",").append(rowIdAttribute.translateExpression(tableAlias));
                    }
                    return;
                }
            } catch (DBException e) {
                log.warn(e);
            }
        }
        super.appendSelectSource(monitor, query, tableAlias, rowIdAttribute);
    }

    @Override
    public String getObjectDefinitionText(DBRProgressMonitor monitor, Map<String, Object> options) throws DBException {
        return getDDL(monitor, DamengDDLFormat.getCurrentFormat(getDataSource()), options);
    }

    @Nullable
    @Override
    public DBPImage getObjectImage() {
        if (CommonUtils.isEmpty(iotType)) {
            return DBIcon.TREE_TABLE;
        } else {
            return DBIcon.TREE_TABLE_INDEX;
        }
    }

    private void loadAdditionalInfo(DBRProgressMonitor monitor) throws DBException {
        if (!isPersisted()) {
            additionalInfo.loaded = true;
            return;
        }
        try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Load table status")) {
            try (JDBCPreparedStatement dbStat = session.prepareStatement(
                "SELECT TABLE_USED_PAGES(?, ?)*(PAGE/1024) TABLE_USED, TABLE_USED_SPACE(?,?)*(PAGE/1024) OCCUPY_SPACE;\n"
                    + "")) {
                dbStat.setString(1, getContainer().getName());
                dbStat.setString(2, getName());
                dbStat.setString(3, getContainer().getName());
                dbStat.setString(4, getName());
                try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                    if (dbResult.next()) {
                        additionalInfo.tableUsed = JDBCUtils.safeGetLong(dbResult, "TABLE_USED");
                        additionalInfo.occupySpace = JDBCUtils.safeGetLong(dbResult, "OCCUPY_SPACE");

                    } else {
                        log.warn("Cannot find table '" + getFullyQualifiedName(DBPEvaluationContext.UI) + "' metadata");
                    }
                    additionalInfo.loaded = true;
                }
            } catch (SQLException e) {
                throw new DBCException(e, session.getExecutionContext());
            }
        }

    }

    @Override
    public void enableReferentialIntegrity(@NotNull DBRProgressMonitor monitor, boolean enable) throws DBException {
        Collection<DamengTableForeignKey> foreignKeys = getAssociations(monitor);
        if (CommonUtils.isEmpty(foreignKeys)) {
            return;
        }

        String template;
        if (enable) {
            template = ENABLE_REFERENTIAL_INTEGRITY_STATEMENT;
        } else {
            template = DISABLE_REFERENTIAL_INTEGRITY_STATEMENT;
        }
        template = template.replace(TABLE_NAME_PLACEHOLDER, getFullyQualifiedName(DBPEvaluationContext.DDL));

        try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Changing referential integrity")) {
            try (JDBCStatement statement = session.createStatement()) {
                for (DBPNamedObject fk : foreignKeys) {
                    String sql = template.replace(FOREIGN_KEY_NAME_PLACEHOLDER, fk.getName());
                    statement.executeUpdate(sql);
                }
            } catch (SQLException e) {
                throw new DBException("Unable to change referential integrity", e);
            }
        }
    }

    @Override
    public boolean supportsChangingReferentialIntegrity(@NotNull DBRProgressMonitor monitor) throws DBException {
        return !CommonUtils.isEmpty(getAssociations(monitor));
    }

    @Nullable
    @Override
    public String getChangeReferentialIntegrityStatement(@NotNull DBRProgressMonitor monitor, boolean enable)
        throws DBException {
        if (!supportsChangingReferentialIntegrity(monitor)) {
            return null;
        }
        if (enable) {
            return ENABLE_REFERENTIAL_INTEGRITY_STATEMENT;
        }
        return DISABLE_REFERENTIAL_INTEGRITY_STATEMENT;
    }

    @Override
    public boolean supportsObjectDefinitionOption(String option) {
        return ArrayUtils.contains(supportedOptions, option);
    }

    @Override
    public DBDPseudoAttribute[] getAllPseudoAttributes(DBRProgressMonitor arg0) throws DBException {
        // TODO Auto-generated method stub
        return null;
    }

    public class AdditionalInfo extends TableAdditionalInfo {
        private Long tableUsed;

        private Long occupySpace;

        @Property(category = DBConstants.CAT_STATISTICS, order = 31)
        public String getTableUsed() {
            return tableUsed + "KB";
        }

        @Property(category = DBConstants.CAT_STATISTICS, order = 32)
        public String getOccupySpace() {
            return occupySpace + "KB";
        }
    }
    
}
