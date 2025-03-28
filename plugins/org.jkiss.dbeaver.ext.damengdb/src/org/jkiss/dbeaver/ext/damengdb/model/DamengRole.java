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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.damengdb.internal.DamengMessages;
import org.jkiss.dbeaver.model.access.DBARole;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCStatement;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCObjectCache;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * DamengRole
 */
public class DamengRole extends DamengGrantee implements DBARole {
	
    private final UserCache userCache = new UserCache();
    protected boolean valid;
    private long id;
    private String name;
    private String userType;
    private Date created;

    public DamengRole(DamengDataSource dataSource, ResultSet resultSet) {
        super(dataSource);
        this.id = JDBCUtils.safeGetLong(resultSet, "ID");
        this.name = JDBCUtils.safeGetString(resultSet, "NAME");
        this.valid = DamengConstants.RESULT_YES_VALUE
            .equals(JDBCUtils.safeGetString(resultSet, DamengConstants.RESULT_STATUS_VALID));
        this.created = JDBCUtils.safeGetTimestamp(resultSet, DamengConstants.CREATED);
        switch (JDBCUtils.safeGetInt(resultSet, "INFO1")) {
            case DamengUser.USER_TYPE_DBA:
                this.userType = DamengMessages.dameng_user_type_name_dba;
                break;
            case DamengUser.USER_TYPE_AUDIT:
                this.userType = DamengMessages.dameng_user_type_name_audit;
                break;
            case DamengUser.USER_TYPE_POLICY:
                this.userType = DamengMessages.dameng_user_type_name_policy;
                break;
            case DamengUser.USER_TYPE_DBO:
                this.userType = DamengMessages.dameng_user_type_name_dbo;
                break;
            case DamengUser.USER_TYPE_SYS:
                this.userType = DamengMessages.dameng_user_type_name_sys;
                break;
        }
    }

    public long getId() {
        return id;
    }

    @NotNull
    @Override
    @Property(viewable = true, order = 2)
    public String getName() {
        return name;
    }

    @Property(viewable = true, order = 3)
    public String getUserType() {
        return userType;
    }

    @Property(viewable = true, order = 4)
    public boolean getValid() {
        return valid;
    }

    @Property(viewable = true, order = 5)
    public Date getCreated() {
        return created;
    }

    @Association
    public Collection<DamengPrivUser> getUserPrivs(DBRProgressMonitor monitor) throws DBException {
        return userCache.getAllObjects(monitor, this);
    }

    @Nullable
    @Override
    public DBSObject refreshObject(@NotNull DBRProgressMonitor monitor) throws DBException {
        userCache.clearCache();
        return super.refreshObject(monitor);
    }

    static class UserCache extends JDBCObjectCache<DamengRole, DamengPrivUser> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengRole owner)
            throws SQLException {
            final JDBCPreparedStatement dbStat = session
                .prepareStatement("SELECT * FROM DBA_ROLE_PRIVS WHERE GRANTED_ROLE=? ORDER BY GRANTEE");
            dbStat.setString(1, owner.getName());
            return dbStat;
        }

        @Override
        protected DamengPrivUser fetchObject(@NotNull JDBCSession session, @NotNull DamengRole owner,
                                             @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengPrivUser(owner, resultSet);
        }
    }

}
