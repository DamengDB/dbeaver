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
import org.jkiss.dbeaver.model.data.DBDDataFilter;
import org.jkiss.dbeaver.model.data.DBDPseudoAttribute;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.rdb.DBSTable;
import org.jkiss.dbeaver.model.struct.rdb.DBSTablePartition;
import org.jkiss.utils.CommonUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dameng abstract partition
 */
public class DamengTablePartition extends DamengTablePhysical implements DBSTablePartition, DBPImageProvider {

    private static final Log log = Log.getLog(DamengTablePartition.class);
    private static final String CAT_PARTITIONING = "Partitioning";
    private DamengTablePhysical parent;
    private DamengTablePartition partitionParent;
    private int position;
    private String highValue;
    private long sampleSize;
    private Timestamp lastAnalyzed;
    private List<DamengTablePartition> subPartitions;

    DamengTablePartition(@NotNull DamengTablePhysical parent, @NotNull String name, @NotNull ResultSet dbResult,
                         @Nullable DamengTablePartition partitionParent) {
        super(parent.getSchema(), dbResult, name);
        this.parent = parent;
        this.partitionParent = partitionParent;
        this.highValue = JDBCUtils.safeGetString(dbResult, "HIGH_VALUE");
        this.position = partitionParent != null ? JDBCUtils.safeGetInt(dbResult, "SUBPARTITION_POSITION")
            : JDBCUtils.safeGetInt(dbResult, "PARTITION_POSITION");
        this.sampleSize = JDBCUtils.safeGetLong(dbResult, "SAMPLE_SIZE");
        this.lastAnalyzed = JDBCUtils.safeGetTimestamp(dbResult, "LAST_ANALYZED");
    }

    @Property(viewable = true, order = 10)
    public int getPosition() {
        return position;
    }

    @Property(viewable = true, order = 30)
    public String getHighValue() {
        return highValue;
    }

    @Property(viewable = true, order = 41)
    public long getSampleSize() {
        return sampleSize;
    }

    @Property(viewable = true, order = 42)
    public Timestamp getLastAnalyzed() {
        return lastAnalyzed;
    }

    @Association
    public List<DamengTablePartition> getSubPartitions(DBRProgressMonitor monitor) throws DBException {
        if (partitionParent != null) {
            return Collections.emptyList();
        }
        if (subPartitions == null) {
            readSubPartitions(monitor);
        }
        return subPartitions;
    }

    private List<DamengTablePartition> readSubPartitions(@NotNull DBRProgressMonitor monitor) throws DBException {
        subPartitions = new ArrayList<>();
        try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Read subpartitions")) {
            try (JDBCPreparedStatement dbStat = session
                .prepareStatement("SELECT * FROM " + DamengUtils.getSysSchemaPrefix(getDataSource())
                    + "ALL_TAB_SUBPARTITIONS " + "\nWHERE TABLE_OWNER=? AND TABLE_NAME=? AND PARTITION_NAME=? "
                    + "\nORDER BY SUBPARTITION_POSITION")) {
                dbStat.setString(1, parent.getSchema().getName());
                dbStat.setString(2, parent.getName());
                dbStat.setString(3, getName());
                try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                    while (dbResult.next()) {
                        String subpartitionName = JDBCUtils.safeGetString(dbResult, "SUBPARTITION_NAME");
                        if (CommonUtils.isEmpty(subpartitionName)) {
                            return null;
                        }
                        subPartitions.add(new DamengTablePartition(parent, subpartitionName, dbResult, this));
                    }
                }
            } catch (SQLException e) {
                throw new DBDatabaseException(e, getDataSource());
            }
        }
        return subPartitions;
    }

    @Nullable
    @Override
    public DBPImage getObjectImage() {
        return DBIcon.TREE_PARTITION;
    }

    @Override
    public TableAdditionalInfo getAdditionalInfo() {
        return new TableAdditionalInfo();
    }

    @Override
    protected String getTableTypeName() {
        return "TABLE PARTITION";
    }

    @Override
    public boolean isView() {
        return false;
    }

    @Override
    protected boolean needAliasInSelect(@Nullable DBDDataFilter dataFilter, @Nullable DBDPseudoAttribute rowIdAttribute,
                                        @NotNull DBPDataSource dataSource) {
        return false;
    }

    @NotNull
    @Override
    protected String getTableName() {
        return parent.getFullyQualifiedName(DBPEvaluationContext.DML);
    }

    @Override
    protected void appendExtraSelectParameters(@NotNull StringBuilder query) {
        query.append(" ").append(partitionParent != null ? "SUB" : "").append("PARTITION (")
            .append(DBUtils.getQuotedIdentifier(this)).append(")");
    }

    @Override
    public DBSTable getParentTable() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DBSTablePartition getPartitionParent() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isSubPartition() {
        // TODO Auto-generated method stub
        return false;
    }

    public enum PartitionType {
        NONE, RANGE, HASH, SYSTEM, LIST,
    }

    public static class PartitionInfoBase {
        private PartitionType partitionType;

        private PartitionType subpartitionType;

        private String partitionInterval;

        private long partitionCount;

        private Object partitionTablespace;

        public PartitionInfoBase(DBRProgressMonitor monitor, DamengDataSource dataSource, ResultSet dbResult) {
            this.partitionType = CommonUtils.valueOf(PartitionType.class,
                JDBCUtils.safeGetStringTrimmed(dbResult, "PARTITIONING_TYPE"));
            this.subpartitionType = CommonUtils.valueOf(PartitionType.class,
                JDBCUtils.safeGetStringTrimmed(dbResult, "SUBPARTITIONING_TYPE"));
            String partitionTablespaceName = JDBCUtils.safeGetStringTrimmed(dbResult, "DEF_TABLESPACE_NAME");
            this.partitionInterval = JDBCUtils.safeGetString(dbResult, "INTERVAL");
            this.partitionCount = JDBCUtils.safeGetLong(dbResult, "PARTITION_COUNT");
            if (dataSource.isAdmin() && CommonUtils.isNotEmpty(partitionTablespaceName)) {
                try {
                    this.partitionTablespace = dataSource.tablespaceCache.getObject(monitor, dataSource,
                        partitionTablespaceName);
                } catch (DBException e) {
                    log.debug("Can not find tablespace " + partitionTablespaceName, e);
                }
            }
        }

        @Property(category = CAT_PARTITIONING, order = 120)
        public PartitionType getPartitionType() {
            return partitionType;
        }

        @Property(category = CAT_PARTITIONING, order = 121)
        public PartitionType getSubpartitionType() {
            return subpartitionType;
        }

        @Property(category = CAT_PARTITIONING, order = 122)
        public String getPartitionInterval() {
            return partitionInterval;
        }

        @Property(category = CAT_PARTITIONING, order = 123)
        public long getPartitionCount() {
            return partitionCount;
        }

        @Property(category = CAT_PARTITIONING, order = 124)
        public Object getPartitionTablespace() {
            return partitionTablespace;
        }
    }
    
}
