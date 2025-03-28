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

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.utils.CommonUtils;

import java.sql.ResultSet;

/**
 * Dameng segments (objects)
 */
public class DamengSegment<PARENT extends DBSObject> extends DamengObject<PARENT> {

    private String segmentType;

    private String partitionName;

    private long bytes;

    private long blocks;

    private DamengSchema schema;

    private DamengDataFile file;

    protected DamengSegment(DBRProgressMonitor monitor, PARENT parent, ResultSet dbResult) throws DBException {
        super(parent, JDBCUtils.safeGetStringTrimmed(dbResult, "SEGMENT_NAME"), true);
        this.segmentType = JDBCUtils.safeGetStringTrimmed(dbResult, "SEGMENT_TYPE");
        this.partitionName = JDBCUtils.safeGetStringTrimmed(dbResult, "PARTITION_NAME");
        this.bytes = JDBCUtils.safeGetLong(dbResult, "BYTES");
        this.blocks = JDBCUtils.safeGetLong(dbResult, "BLOCKS");
        final long fileNo = JDBCUtils.safeGetInt(dbResult, "RELATIVE_FNO");
        final Object tablespace = getTablespace(monitor);
        if (tablespace instanceof DamengTablespace) {
            this.file = ((DamengTablespace) tablespace).getFile(monitor, fileNo);
        }
        if (getDataSource().isAdmin()) {
            String ownerName = JDBCUtils.safeGetStringTrimmed(dbResult, "OWNER");
            if (!CommonUtils.isEmpty(ownerName)) {
                schema = getDataSource().getSchema(monitor, ownerName);
            }
        }
    }

    public Object getTablespace(DBRProgressMonitor monitor) throws DBException {
        if (parent instanceof DamengTablespace) {
            return parent;
        } else if (parent instanceof DamengTablePartition) {
            return ((DamengTablePartition) parent).getTablespace(monitor);
        } else {
            return null;
        }
    }

    @Property(viewable = true, editable = true, order = 2)
    public DamengSchema getSchema() {
        return schema;
    }

    @Property(viewable = true, editable = true, order = 3)
    public String getSegmentType() {
        return segmentType;
    }

    @Property(viewable = true, editable = true, order = 4)
    public String getPartitionName() {
        return partitionName;
    }

    @Property(viewable = true, editable = true, order = 5)
    public long getBytes() {
        return bytes;
    }

    @Property(viewable = true, editable = true, order = 6)
    public long getBlocks() {
        return blocks;
    }

    @Property(order = 7)
    public DamengDataFile getFile() {
        return file;
    }
    
}
