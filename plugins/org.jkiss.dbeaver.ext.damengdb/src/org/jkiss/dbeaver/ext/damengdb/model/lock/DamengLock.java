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

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jkiss.dbeaver.ext.damengdb.model.DamengDataSource;
import org.jkiss.dbeaver.model.admin.locks.DBAServerLock;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Property;

public class DamengLock implements DBAServerLock {
	
    private long wait_sid;

    private long wait_tid;

    private long row_idx;

    private String lType;

    private String lMode;

    private int blocked;

    private String wait_user;

    private String oname;

    private String owner;

    private long hold_sid;

    private long hold_tid;

    private String hold_user;

    private DBAServerLock hold = null;

    private List<DBAServerLock> waiters = new ArrayList<>(0);

    private DamengDataSource dataSource;

    private Date created;

    public DamengLock(ResultSet dbResult, DamengDataSource dataSource) {
        this.wait_sid = JDBCUtils.safeGetLong(dbResult, "WAITING_SESSION");
        this.wait_tid = JDBCUtils.safeGetLong(dbResult, "WAITING_TID");
        this.lType = JDBCUtils.safeGetString(dbResult, "LTYPE");
        this.lMode = JDBCUtils.safeGetString(dbResult, "LMODE");
        this.blocked = JDBCUtils.safeGetInt(dbResult, "BLOCKED");
        this.row_idx = JDBCUtils.safeGetLong(dbResult, "ROW_IDX");
        this.hold_sid = JDBCUtils.safeGetLong(dbResult, "HOLDING_SESSION");
        this.hold_tid = JDBCUtils.safeGetLong(dbResult, "HOLDING_TID");
        this.oname = JDBCUtils.safeGetString(dbResult, "TABLE_NAME");
        this.owner = JDBCUtils.safeGetString(dbResult, "OWNER_NAME");
        this.wait_user = JDBCUtils.safeGetString(dbResult, "WAITING_USER");
        this.hold_user = JDBCUtils.safeGetString(dbResult, "HOLDING_USER");
        this.created = JDBCUtils.safeGetDate(dbResult, "CREATE_TIME");
        this.dataSource = dataSource;
    }

    @Override
    public String getTitle() {
        return String.valueOf(wait_sid);
    }

    @Override
    public DBAServerLock getHoldBy() {

        return hold;
    }

    @Override
    public void setHoldBy(DBAServerLock lock) {
        this.hold = lock;
    }

    public DBAServerLock getHold() {
        return hold;
    }

    @Override
    public Object getId() {
        return wait_sid;
    }

    @Override
    public List<DBAServerLock> waitThis() {
        return this.waiters;
    }

    @Override
    public Object getHoldID() {
        return hold_sid;
    }

    @Override
    public String toString() {
        return String.format("Wait %s - %d (%s) Hold - %d (%s)", oname, wait_sid, wait_user, hold_sid, hold_user);
    }

    @Property(viewable = true, order = 1)
    public long getWait_sid() {
        return wait_sid;
    }

    @Property(viewable = true, order = 2)
    public long getWait_tid() {
        return wait_tid;
    }

    @Property(viewable = true, order = 3)
    public String getWait_user() {
        return wait_user;
    }

    @Property(viewable = true, order = 4)
    public String getOname() {
        return oname;
    }

    @Property(viewable = true, order = 5)
    public String getOwner() {
        return owner;
    }

    @Property(viewable = true, order = 6)
    public long getRow_lock() {
        return row_idx;
    }

    @Property(viewable = true, order = 7)
    public long getHold_sid() {
        return hold_sid;
    }

    @Property(viewable = true, order = 8)
    public long getHold_tid() {
        return hold_tid;
    }

    @Property(viewable = true, order = 9)
    public String getHold_user() {
        return hold_user;
    }

    @Property(viewable = true, order = 10)
    public Date getCreated() {
        return created;
    }

    @Property(viewable = true, order = 11)
    public String getLType() {
        return lType;
    }

    @Property(viewable = true, order = 12)
    public String getLMode() {
        return lMode;
    }

    @Property(viewable = true, order = 13)
    public int getBlocked() {
        return blocked;
    }

    public DamengDataSource getDataSource() {
        return dataSource;
    }

}
