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

import org.jkiss.dbeaver.DBDatabaseException;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.damengdb.model.DamengDataSource;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.admin.locks.DBAServerLockManager;
import org.jkiss.dbeaver.model.exec.DBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.admin.locks.LockGraphManager;

import java.sql.SQLException;
import java.util.*;

public class DamengLockManager extends LockGraphManager implements DBAServerLockManager<DamengLock, DamengLockItem> {
    
	public static final String sidHold = "hsid";

    public static final String sidWait = "wsid";

    private static final String LOCK_QUERY =
        "select w.sess_id waiting_session, w.thrd_id waiting_tid, w.user_name waiting_user, h.sess_id holding_session, h.thrd_id holding_tid, h.user_name holding_user, s.name TABLE_NAME,o.name OWNER_NAME, w.create_time create_time,l.ltype, l.lmode, l.row_idx, l.blocked from v$lock l, sysobjects s, sysobjects o , v$sessions w, v$sessions h\n"
            + "where l.blocked = 1 and l.table_id = s.id and o.id = s.schid and w.trx_id = l.trx_id and h.trx_id = l.tid \n"
            + "union \n"
            +
            "select w.sess_id waiting_session, w.thrd_id waiting_tid, w.user_name waiting_user, nvl(h.sess_id,0) holding_session, nvl(h.thrd_id,0) holding_tid, nvl(h.user_name,'-') holding_user, nvl(s.name,'-') TABLE_NAME, nvl(o.name,'-') OWNER_NAME,  w.create_time create_time, nvl(l.ltype,'-'), nvl(l.lmode,'-'), nvl(l.row_idx,0), nvl(l.blocked,0) \n"
            +
            "from v$sessions w left join v$lock l on l.blocked = 1 and l.trx_id = w.trx_id  left join v$sessions h on h.trx_id in (select tid from v$lock l where l.blocked = 1 and l.trx_id = w.trx_id) left join sysobjects s on s.id in (select table_id from v$lock where blocked = 1 and w.trx_id = trx_id) left join sysobjects o on o.id = s.schid where  w.trx_id IN (SELECT tid FROM v$lock where blocked = 1)\n";

    private static final String LOCK_ITEM_QUERY = "select * from v$lock where trx_id = (select trx_id from v$sessions where sess_id = ?)";

    private final DamengDataSource dataSource;

    public DamengLockManager(DamengDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public DBPDataSource getDataSource() {
        return dataSource;
    }

    @Override
    public Map<Object, DamengLock> getLocks(DBCSession session, Map<String, Object> options) throws DBException {
        try {
            Map<Object, DamengLock> locks = new HashMap<>(10);

            String sql = LOCK_QUERY;

            try (JDBCPreparedStatement dbStat = ((JDBCSession) session).prepareStatement(sql)) {
                try (JDBCResultSet dbResult = dbStat.executeQuery()) {

                    while (dbResult.next()) {
                        DamengLock l = new DamengLock(dbResult, dataSource);
                        locks.put(l.getId(), l);
                    }
                }
            }

            super.buildGraphs(locks);
            return locks;

        } catch (SQLException e) {
            throw new DBDatabaseException(e, session.getDataSource());
        }
    }

    @Override
    public void alterSession(DBCSession session, DamengLock lock, Map<String, Object> options) throws DBException {
        try {
            String sql = "SP_CLOSE_SESSION(" + lock.getWait_sid() + ")";
            try (JDBCPreparedStatement dbStat = ((JDBCSession) session).prepareStatement(sql)) {
                dbStat.execute();
            }
        } catch (SQLException e) {
            throw new DBDatabaseException(e, session.getDataSource());
        }
    }

    @Override
    public Class<DamengLock> getLocksType() {
        return DamengLock.class;
    }

    @Override
    public Collection<DamengLockItem> getLockItems(DBCSession session, Map<String, Object> options) throws DBException {
        try {

            List<DamengLockItem> locks = new ArrayList<>();

            String sql = LOCK_ITEM_QUERY;

            try (JDBCPreparedStatement dbStat = ((JDBCSession) session).prepareStatement(sql)) {

                String otype = (String) options.get(LockGraphManager.keyType);

                switch (otype) {

                    case LockGraphManager.typeWait:
                        dbStat.setLong(1, (Long) options.get(sidWait));
                        break;

                    case LockGraphManager.typeHold:
                        dbStat.setLong(1, (Long) options.get(sidHold));
                        break;

                    default:
                        return locks;
                }

                try (JDBCResultSet dbResult = dbStat.executeQuery()) {

                    while (dbResult.next()) {
                        locks.add(new DamengLockItem(dbResult));
                    }
                }
            }

            return locks;

        } catch (SQLException e) {
            throw new DBDatabaseException(e, session.getDataSource());
        }
    }
    
}
