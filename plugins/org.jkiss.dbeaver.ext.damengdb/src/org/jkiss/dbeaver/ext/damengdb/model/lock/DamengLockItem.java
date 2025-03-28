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
package org.jkiss.dbeaver.ext.damengdb.model.lock;

import org.jkiss.dbeaver.model.admin.locks.DBAServerLockItem;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Property;

import java.sql.ResultSet;

public class DamengLockItem implements DBAServerLockItem {
	
    private Long addr;

    private Long trx_id;

    private String lType;

    private String lMode;

    private int blocked;

    private Long table_id;

    private Long row_idx;

    private Long tid;

    private int ign_flag;

    private int hlck_ep;

    public DamengLockItem(ResultSet dbResult) {
        this.addr = JDBCUtils.safeGetLong(dbResult, "ADDR");
        this.trx_id = JDBCUtils.safeGetLong(dbResult, "TRX_ID");
        this.lType = JDBCUtils.safeGetString(dbResult, "LTYPE");
        this.lMode = JDBCUtils.safeGetString(dbResult, "LMODE");
        this.blocked = JDBCUtils.safeGetInt(dbResult, "BLOCKED");
        this.table_id = JDBCUtils.safeGetLong(dbResult, "TABLE_ID");
        this.row_idx = JDBCUtils.safeGetLong(dbResult, "ROW_IDX");
        this.tid = JDBCUtils.safeGetLong(dbResult, "TID");
        this.ign_flag = JDBCUtils.safeGetInt(dbResult, "IGN_FLAG");
        this.hlck_ep = JDBCUtils.safeGetInt(dbResult, "HLCK_EP");
    }

    @Property(viewable = true, order = 1)
    public Long getAddr() {
        return addr;
    }

    @Property(viewable = true, order = 2)
    public Long getTrx_id() {
        return trx_id;
    }

    @Property(viewable = true, order = 3)
    public String getLType() {
        return lType;
    }

    @Property(viewable = true, order = 4)
    public String getLMode() {
        return lMode;
    }

    @Property(viewable = true, order = 5)
    public Integer getBlocked() {
        return blocked;
    }

    @Property(viewable = true, order = 6)
    public Long getTable_id() {
        return table_id;
    }

    @Property(viewable = true, order = 7)
    public Long getRow_idx() {
        return row_idx;
    }

    @Property(viewable = true, order = 8)
    public Long getTid() {
        return tid;
    }

    @Property(viewable = true, order = 9)
    public int getIgn_flag() {
        return ign_flag;
    }

    @Property(viewable = true, order = 10)
    public int getHlck_ep() {
        return hlck_ep;
    }
    
}
