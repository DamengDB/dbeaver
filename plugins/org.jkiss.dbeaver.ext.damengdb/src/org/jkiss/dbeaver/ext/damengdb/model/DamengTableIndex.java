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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBDatabaseException;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.damengdb.internal.DamengMessages;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBPScriptObject;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.impl.jdbc.struct.JDBCTableIndex;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.LazyProperty;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObjectLazy;
import org.jkiss.dbeaver.model.struct.rdb.DBSIndexType;

/**
 * DamengTableIndex
 */
@SuppressWarnings("rawtypes")
public class DamengTableIndex extends JDBCTableIndex<DamengSchema, DamengTablePhysical>
    implements DBSObjectLazy, DBPScriptObject {
	
    private Object tablespace;

    private boolean nonUnique = true;

    private boolean nonClustered = true;

    private boolean nonSpatial = true;

    private boolean nonBitMap = true;

    private boolean nonArray = true;

    private boolean nonCluster = true;

    private boolean nonGlobal = true;

    private boolean nonBitmapJoin = true;

    private boolean nonFbi = true;

    private List<DamengTableIndexColumn> columns;

    private String indexDDL;

    public DamengTableIndex(DamengSchema schema, DamengTablePhysical table, String indexName, ResultSet dbResult) {
        super(schema, table, indexName, null, true);
        int xtype = JDBCUtils.safeGetInt(dbResult, "XTYPE");
        this.nonUnique = !"Y".equals(JDBCUtils.safeGetString(dbResult, "ISUNIQUE"));
        nonSpatial = !((xtype & 0x8000) == 0x8000);
        nonArray = !((xtype & 0x40) == 0x40);
        nonCluster = !((xtype & 0x01) == 0x00);
        nonGlobal = !((xtype & 0x08) != 0x00);
        if ((xtype & 0x2000) == 0x2000) {
            nonBitmapJoin = false;
        } else if ((xtype & 0x0002) == 0x0002) {
            nonFbi = false;
        } else {
            nonFbi = true;
        }
        nonBitMap = !("BM".equals(JDBCUtils.safeGetString(dbResult, "TYPE$")));

        this.tablespace = JDBCUtils.safeGetString(dbResult, "TABLESPACE_NAME");

        String indexType1 = "";
        String indexType2 = "";
        String indexType3 = "";
        String indexType4 = DamengMessages.dameng_index_property_dialog_local;
        String indexTypeString = DamengMessages.dameng_index_property_dialog_index_type_str;
        if (!nonBitmapJoin) {
            indexType1 = DamengMessages.dameng_index_property_dialog_uncluster_bitmap_join;
        } else if (!nonSpatial) {
            indexType1 = DamengMessages.dameng_index_property_dialog_spatial;
        } else if (!nonArray) {
            indexType1 = DamengMessages.dameng_index_property_dialog_array;
        } else {
            if (!nonCluster) {
                indexType1 = DamengMessages.dameng_index_property_dialog_cluster;
            } else {
                indexType1 = DamengMessages.dameng_index_property_dialog_uncluster;
            }

            if (isUnique()) {
                indexType2 = DamengMessages.dameng_index_property_dialog_unique;
            } else if (isBitMap()) {
                indexType2 = DamengMessages.dameng_index_property_dialog_bitmap;
            } else {
                indexType2 = DamengMessages.dameng_index_property_dialog_btree;
            }

            if (!nonFbi) {
                indexType3 = DamengMessages.dameng_index_property_dialog_function;
            }
        }

        if (!nonGlobal) {
            indexType4 = DamengMessages.dameng_index_property_dialog_global;
        }

        indexType = new DBSIndexType("OTHER", indexType1 + indexType2 + indexType3 + indexTypeString + indexType4);
        ;
    }

    public DamengTableIndex(DamengSchema schema, DamengTablePhysical parent, String name, boolean unique,
                            DBSIndexType indexType) {
        super(schema, parent, name, indexType, false);
        this.nonUnique = !unique;
        this.nonClustered = true;
        this.nonSpatial = true;
        this.nonBitMap = true;
    }

    @NotNull
    @Override
    public DamengDataSource getDataSource() {
        return getTable().getDataSource();
    }

    @Override
    @Property(viewable = true, order = 5)
    public boolean isUnique() {
        return !nonUnique;
    }

    public void setUnique(boolean unique) {
        this.nonUnique = !unique;
    }

    public boolean isClustered() {
        return !nonClustered;
    }

    public void setClustered(boolean clustered) {
        this.nonClustered = !clustered;
    }

    public boolean isSpatial() {
        return !nonSpatial;
    }

    public void setSpatial(boolean spatial) {
        this.nonSpatial = !spatial;
    }

    public boolean isBitMap() {
        return !nonBitMap;
    }

    public void setBitMap(boolean bitMap) {
        this.nonBitMap = !bitMap;
    }

    @Override
    public Object getLazyReference(Object propertyId) {
        return tablespace;
    }

    @SuppressWarnings("unchecked")
    @Property(viewable = true, order = 10)
    @LazyProperty(cacheValidator = DamengTablespace.TablespaceReferenceValidator.class)
    public Object getTablespace(DBRProgressMonitor monitor) throws DBException {
        return DamengTablespace.resolveTablespaceReference(monitor, this, null);
    }

    @Nullable
    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public List<DamengTableIndexColumn> getAttributeReferences(DBRProgressMonitor monitor) {
        return columns;
    }

    @Nullable
    @Association
    public DamengTableIndexColumn getColumn(String columnName) {
        return DBUtils.findObject(columns, columnName);
    }

    void setColumns(List<DamengTableIndexColumn> columns) {
        this.columns = columns;
    }

    public void addColumn(DamengTableIndexColumn column) {
        if (columns == null) {
            columns = new ArrayList<>();
        }
        columns.add(column);
    }

    @NotNull
    @Override
    public String getFullyQualifiedName(DBPEvaluationContext context) {
        return DBUtils.getFullQualifiedName(getDataSource(), getTable().getContainer(), this);
    }

    @Override
    public String toString() {
        return getFullyQualifiedName(DBPEvaluationContext.UI);
    }

    @Override
    @Property(hidden = true, editable = true, updatable = true, order = -1)
    public String getObjectDefinitionText(DBRProgressMonitor monitor, Map<String, Object> options) throws DBException {
        if (indexDDL == null && isPersisted()) {
            try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Read index definition")) {
                indexDDL = JDBCUtils.queryString(session,
                    "SELECT INDEXDEF((SELECT ID FROM SYSOBJECTS WHERE SUBTYPE$ = 'INDEX' AND SCHID = ? AND NAME = ?),1);",
                    getTable().getSchema().getId(), getName());
            } catch (SQLException e) {
                throw new DBDatabaseException(e, getDataSource());
            }
        }
        return indexDDL;
    }
    
}
