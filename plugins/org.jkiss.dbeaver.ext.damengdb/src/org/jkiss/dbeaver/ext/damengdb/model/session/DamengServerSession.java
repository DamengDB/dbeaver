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

import java.sql.ResultSet;
import java.sql.Timestamp;

import org.jkiss.dbeaver.model.admin.sessions.AbstractServerSession;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Property;

/**
 * Session
 */
public class DamengServerSession extends AbstractServerSession {
	
    public static final String CAT_SESSION = "Session";

    public static final String CAT_SQL = "SQL";

    public static final String CAT_PROCESS = "Process";

    public static final String CAT_IO = "IO";

    public static final String CAT_WAIT = "Wait";
    private final long sid;
    private final String action;
    private final String clientInfo;
    private final String thread;
    private String instName;
    private long serial;
    private String user;
    private String schema;
    private String type;
    private String state;
    private String sql;
    private String sqlId;
    private Timestamp createTime;
    private String connType;
    private String remoteHost;
    private String remoteIP;
    private String remoteProgram;
    private String runStatus;
    private String module;

    public DamengServerSession(ResultSet dbResult) {
        this.instName = JDBCUtils.safeGetString(dbResult, "INSTANCE_NAME");
        this.sid = JDBCUtils.safeGetLong(dbResult, "SESS_ID");
        this.serial = JDBCUtils.safeGetLong(dbResult, "SESS_SEQ");
        this.user = JDBCUtils.safeGetString(dbResult, "USER_NAME");
        this.schema = JDBCUtils.safeGetString(dbResult, "CURR_SCH");
        this.type = JDBCUtils.safeGetString(dbResult, "CLNT_TYPE");
        this.state = JDBCUtils.safeGetString(dbResult, "STATE");
        this.sql = JDBCUtils.safeGetString(dbResult, "SQL_TEXT");
        this.sqlId = JDBCUtils.safeGetString(dbResult, "SQL_ID");
        this.createTime = JDBCUtils.safeGetTimestamp(dbResult, "CREATE_TIME");

        this.connType = JDBCUtils.safeGetString(dbResult, "CONN_TYPE");
        this.remoteHost = JDBCUtils.safeGetString(dbResult, "CLNT_HOST");
        this.remoteIP = JDBCUtils.safeGetString(dbResult, "CLNT_IP");
        this.remoteProgram = JDBCUtils.safeGetString(dbResult, "APPNAME");
        this.runStatus = JDBCUtils.safeGetString(dbResult, "RUN_STATUS");
        this.module = JDBCUtils.safeGetString(dbResult, "MODULE");
        this.action = JDBCUtils.safeGetString(dbResult, "ACTION");
        this.clientInfo = JDBCUtils.safeGetString(dbResult, "CLIENT_INFO");
        this.thread = JDBCUtils.safeGetString(dbResult, "THRD_ID");
    }

    @Property(category = CAT_SESSION, viewable = false, order = 1)
    public String getInstName() {
        return instName;
    }

    @Property(category = CAT_SESSION, viewable = true, order = 2)
    public long getSid() {
        return sid;
    }

    @Property(category = CAT_SESSION, viewable = false, order = 3)
    public long getSerial() {
        return serial;
    }

    @Property(category = CAT_SESSION, viewable = true, order = 4)
    public String getUser() {
        return user;
    }

    @Property(category = CAT_SESSION, viewable = true, order = 5)
    public String getSchema() {
        return schema;
    }

    @Property(category = CAT_SESSION, viewable = true, order = 6)
    public String getType() {
        return type;
    }

    @Property(category = CAT_SESSION, viewable = true, order = 8)
    public String getState() {
        return state;
    }

    @Property(category = CAT_SESSION, viewable = true, order = 9)
    public Timestamp getCreateTime() {
        return createTime;
    }

    @Property(category = CAT_SQL, order = 20)
    public String getSql() {
        return sql;
    }

    @Property(category = CAT_SQL, order = 21)
    public String getSqlId() {
        return sqlId;
    }

    @Property(category = CAT_PROCESS, viewable = true, order = 30)
    public String getConnType() {
        return connType;
    }

    @Property(category = CAT_PROCESS, viewable = true, order = 30)
    public String getRemoteHost() {
        return remoteHost;
    }

    @Property(category = CAT_PROCESS, viewable = true, order = 31)
    public String getRemoteIP() {
        return remoteIP;
    }

    @Property(category = CAT_PROCESS, viewable = true, order = 32)
    public String getRemoteProgram() {
        return remoteProgram;
    }

    @Property(category = CAT_PROCESS, viewable = true, order = 32)
    public String getRunStatus() {
        return runStatus;
    }

    @Property(category = CAT_PROCESS, viewable = false, order = 32)
    public String getModule() {
        return module;
    }

    @Property(category = CAT_PROCESS, viewable = false, order = 32)
    public String getAction() {
        return action;
    }

    @Property(category = CAT_PROCESS, viewable = false, order = 32)
    public String getClientInfo() {
        return clientInfo;
    }

    @Property(category = CAT_PROCESS, viewable = false, order = 32)
    public String getThread() {
        return thread;
    }

    @Override
    public String getActiveQuery() {
        return sql;
    }
    
}
