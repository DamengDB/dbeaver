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

import java.sql.SQLException;
import java.util.Collection;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.DBPRefreshableObject;
import org.jkiss.dbeaver.model.DBPSaveableObject;
import org.jkiss.dbeaver.model.access.DBAUser;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCStatement;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCObjectCache;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * DamengGrantee
 */
public abstract class DamengGrantee extends DamengGlobalObject
    implements DBAUser, DBPSaveableObject, DBPRefreshableObject {
	
    final RolePrivCache rolePrivCache = new RolePrivCache();
    private final SystemPrivCache systemPrivCache = new SystemPrivCache();
    private final ObjectPrivCache objectPrivCache = new ObjectPrivCache();

    public DamengGrantee(DamengDataSource dataSource) {
        super(dataSource, true);
    }

    abstract long getId();

    @Association
    public Collection<DamengPrivRole> getRolePrivs(DBRProgressMonitor monitor) throws DBException {
        return rolePrivCache.getAllObjects(monitor, this);
    }

    @Association
    public Collection<DamengPrivSystem> getSystemPrivs(DBRProgressMonitor monitor) throws DBException {
        return systemPrivCache.getAllObjects(monitor, this);
    }

    @Association
    public Collection<DamengPrivObject> getObjectPrivs(DBRProgressMonitor monitor) throws DBException {
        return objectPrivCache.getAllObjects(monitor, this);
    }

    @Nullable
    @Override
    public DBSObject refreshObject(@NotNull DBRProgressMonitor monitor) throws DBException {
        rolePrivCache.clearCache();
        systemPrivCache.clearCache();
        objectPrivCache.clearCache();

        return this;
    }

    static class RolePrivCache extends JDBCObjectCache<DamengGrantee, DamengPrivRole> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengGrantee owner)
            throws SQLException {
            final JDBCPreparedStatement dbStat = session
                .prepareStatement("SELECT * FROM DBA_ROLE_PRIVS WHERE GRANTEE=? ORDER BY GRANTED_ROLE");
            dbStat.setString(1, owner.getName());
            return dbStat;
        }

        @Override
        protected DamengPrivRole fetchObject(@NotNull JDBCSession session, @NotNull DamengGrantee owner,
                                             @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengPrivRole(owner, resultSet);
        }
    }

    static class SystemPrivCache extends JDBCObjectCache<DamengGrantee, DamengPrivSystem> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengGrantee owner)
            throws SQLException {
            final JDBCPreparedStatement dbStat = session
                .prepareStatement("SELECT * FROM DBA_SYS_PRIVS WHERE GRANTEE=? ORDER BY PRIVILEGE");
            dbStat.setString(1, owner.getName());
            return dbStat;
        }

        @Override
        protected DamengPrivSystem fetchObject(@NotNull JDBCSession session, @NotNull DamengGrantee owner,
                                               @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengPrivSystem(owner, resultSet);
        }
    }

    static class ObjectPrivCache extends JDBCObjectCache<DamengGrantee, DamengPrivObject> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengGrantee owner)
            throws SQLException {
            final JDBCPreparedStatement dbStat = session.prepareStatement(
                "select SF_GET_OBJ_FULL_NAME(OBJID, COLID) TABLE_NAME, SF_GET_SYS_PRIV(PRIVID) PRIVILEGE,\r\n"
                    + "(select name from SYSOBJECTS where ID = GRANTS.GRANTOR) GRANTOR_NAME, GRANTABLE, \r\n"
                    + "(select TYPE$ from SYSOBJECTS where ID = GRANTS.OBJID) TYPE, \r\n"
                    +
                    "(select DECODE(SUBTYPE$,'PROC',DECODE(INFO1 & 0X01,0,'FUNCTION',1,'PROCEDURE'),SUBTYPE$) from SYSOBJECTS where ID = GRANTS.OBJID) SUB_TYPE\r\n"
                    + "from SYSGRANTS GRANTS where (OBJID != -1 OR COLID != -1) and PRIVID != -1 and URID = ?");
            dbStat.setString(1, Long.toString(owner.getId()));
            return dbStat;
        }

        @Override
        protected DamengPrivObject fetchObject(@NotNull JDBCSession session, @NotNull DamengGrantee owner,
                                               @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengPrivObject(owner, resultSet);
        }
    }
    
}