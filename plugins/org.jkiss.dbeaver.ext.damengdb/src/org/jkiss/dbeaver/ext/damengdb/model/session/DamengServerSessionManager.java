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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBDatabaseException;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.damengdb.internal.DamengMessages;
import org.jkiss.dbeaver.ext.damengdb.model.DamengConstants;
import org.jkiss.dbeaver.ext.damengdb.model.DamengDataSource;
import org.jkiss.dbeaver.ext.damengdb.model.plan.DamengExecutionPlan;
import org.jkiss.dbeaver.model.DBIcon;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPObject;
import org.jkiss.dbeaver.model.admin.sessions.AbstractServerSessionDetails;
import org.jkiss.dbeaver.model.admin.sessions.DBAServerSession;
import org.jkiss.dbeaver.model.admin.sessions.DBAServerSessionDetails;
import org.jkiss.dbeaver.model.admin.sessions.DBAServerSessionDetailsProvider;
import org.jkiss.dbeaver.model.admin.sessions.DBAServerSessionManager;
import org.jkiss.dbeaver.model.admin.sessions.DBAServerSessionManagerSQL;
import org.jkiss.dbeaver.model.exec.DBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.utils.CommonUtils;

/**
 * Dameng session manager
 */
public class DamengServerSessionManager implements DBAServerSessionManager<DamengServerSession>,
    DBAServerSessionManagerSQL, DBAServerSessionDetailsProvider {

    public static final String PROP_KILL_SESSION = "killSession";

    public static final String OPTION_SHOW_INACTIVE = "showInactive";

    private final DamengDataSource dataSource;

    public DamengServerSessionManager(DamengDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public DBPDataSource getDataSource() {
        return dataSource;
    }

    @Override
    public Collection<DamengServerSession> getSessions(DBCSession session, Map<String, Object> options)
        throws DBException {
        try {

            try (JDBCPreparedStatement dbStat = ((JDBCSession) session)
                .prepareStatement(generateSessionReadQuery(options))) {
                try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                    List<DamengServerSession> sessions = new ArrayList<>();
                    while (dbResult.next()) {
                        sessions.add(new DamengServerSession(dbResult));
                    }
                    return sessions;
                }
            }
        } catch (SQLException e) {
            throw new DBDatabaseException(e, session.getDataSource());
        }
    }

    @Override
    public void alterSession(@NotNull DBCSession session, @NotNull String sessionId,
                             @NotNull Map<String, Object> options) throws DBException {
        try {
            StringBuilder sql = new StringBuilder("SP_CLOSE_SESSION( ");
            sql.append(sessionId).append(")");
            try (JDBCPreparedStatement dbStat = ((JDBCSession) session).prepareStatement(sql.toString())) {
                dbStat.execute();
            }
        } catch (SQLException e) {
            throw new DBDatabaseException(e, session.getDataSource());
        }
    }

    @Override
    public List<DBAServerSessionDetails> getSessionDetails() {
        List<DBAServerSessionDetails> extDetails = new ArrayList<>();

        extDetails.add(new AbstractServerSessionDetails(DamengMessages.dameng_server_session_manager_details_name,
            DamengMessages.dameng_server_session_manager_details_description, DBIcon.TYPE_DATETIME) {
            @Override
            public List<DamengServerLongOp> getSessionDetails(DBCSession session, DBAServerSession serverSession)
                throws DBException {
                try {
                    try (JDBCPreparedStatement dbStat = ((JDBCSession) session)
                        .prepareStatement("select * from V$LONG_EXEC_SQLS where sessid = ?")) {
                        dbStat.setLong(1, ((DamengServerSession) serverSession).getSid());
                        try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                            List<DamengServerLongOp> longOps = new ArrayList<>();
                            while (dbResult.next()) {
                                longOps.add(new DamengServerLongOp(dbResult));
                            }
                            return longOps;
                        }
                    }
                } catch (SQLException e) {
                    throw new DBDatabaseException(e, session.getDataSource());
                }
            }

            @Override
            public Class<? extends DBPObject> getDetailsType() {
                return DamengServerLongOp.class;
            }
        });

        extDetails.add(
            new AbstractServerSessionDetails(DamengMessages.dameng_server_session_manager_display_exec_plan_name,
                DamengMessages.dameng_server_session_manager_display_exec_plan_description, DBIcon.TYPE_TEXT) {
                @Override
                public List<DamengServerExecutePlan> getSessionDetails(DBCSession session,
                                                                       DBAServerSession serverSession) throws DBException {
                    String query = ((DamengServerSession) serverSession).getSql().trim();
                    String sqlTemp = query.toUpperCase();
                    if (sqlTemp.contains(DamengConstants.EXPLAIN_KEYWORD)) {
                        int position = sqlTemp.indexOf(DamengConstants.EXPLAIN_KEYWORD);
                        if (position != -1) {
                            query = query.substring(DamengConstants.EXPLAIN_KEYWORD_LEN);
                        }
                    }

                    List<DamengServerExecutePlan> planItems = new ArrayList<>();
                    try {
                        planItems.add(new DamengServerExecutePlan(
                            DamengExecutionPlan.getExplainInfo(((JDBCSession) session).getOriginal(), query)));
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    return planItems;
                }

                @Override
                public Class<? extends DBPObject> getDetailsType() {
                    return DamengServerExecutePlan.class;
                }
            });

        return extDetails;
    }

    @Override
    public boolean canGenerateSessionReadQuery() {
        return true;
    }

    @Override
    public String generateSessionReadQuery(Map<String, Object> options) {
        StringBuilder sql = new StringBuilder();

        sql.append("SELECT  g.INSTANCE_NAME,s.* FROM V$SESSIONS s, GV$SESSIONS g WHERE g.SESS_ID = s.SESS_ID");
        if (!CommonUtils.getOption(options, OPTION_SHOW_INACTIVE)) {
            sql.append(" AND s.RUN_STATUS = 'RUNNING' ");
        }

        return sql.toString();
    }

    @Override
    public Map<String, Object> getTerminateOptions() {
        return Map.of(DamengServerSessionManager.PROP_KILL_SESSION, true);
    }
    
}
