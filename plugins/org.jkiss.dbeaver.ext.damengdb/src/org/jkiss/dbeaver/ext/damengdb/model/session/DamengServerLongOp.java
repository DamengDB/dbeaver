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
package org.jkiss.dbeaver.ext.damengdb.model.session;

import org.jkiss.dbeaver.model.DBPObject;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Property;

import java.sql.ResultSet;
import java.util.Date;

/**
 * Session
 */
public class DamengServerLongOp implements DBPObject {
	
    private String sqlText;

    private long execTime;

    private int nRuns;

    private long trxId;

    private long serial;

    private Date finishTime;

    public DamengServerLongOp(ResultSet dbResult) {
        this.sqlText = JDBCUtils.safeGetString(dbResult, "SQL_TEXT");
        this.execTime = JDBCUtils.safeGetLong(dbResult, "EXEC_TIME");
        this.finishTime = JDBCUtils.safeGetTimestamp(dbResult, "FINISH_TIME");
        this.nRuns = JDBCUtils.safeGetInt(dbResult, "N_RUNS");
        this.trxId = JDBCUtils.safeGetLong(dbResult, "TRX_ID");
        this.serial = JDBCUtils.safeGetLong(dbResult, "SESS_SEQ");
    }

    @Property(viewable = true, order = 1)
    public String getSqlText() {
        return sqlText;
    }

    @Property(viewable = true, order = 2)
    public long getExecTime() {
        return execTime;
    }

    @Property(viewable = true, order = 3)
    public Date getFinishTime() {
        return finishTime;
    }

    @Property(viewable = true, order = 4)
    public int getnRuns() {
        return nRuns;
    }

    @Property(viewable = true, order = 5)
    public long getTrxId() {
        return trxId;
    }

    @Property(viewable = true, order = 6)
    public long getSerial() {
        return serial;
    }

    @Override
    public String toString() {
        return sqlText;
    }
    
}
