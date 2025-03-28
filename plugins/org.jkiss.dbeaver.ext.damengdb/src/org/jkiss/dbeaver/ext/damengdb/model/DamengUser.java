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
import java.sql.Timestamp;
import java.util.Collection;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.damengdb.internal.DamengMessages;
import org.jkiss.dbeaver.model.access.DBAUser;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.IPropertyCacheValidator;
import org.jkiss.dbeaver.model.meta.LazyProperty;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectLazy;

/**
 * DamengUser
 */
public class DamengUser extends DamengGrantee implements DBAUser, DBSObjectLazy<DamengDataSource> {
    
	public final static int USER_TYPE_DBA = 0;

    public final static int USER_TYPE_AUDIT = 1;

    public final static int USER_TYPE_POLICY = 2;

    public final static int USER_TYPE_DBO = 3;

    public final static int USER_TYPE_SYS = 4;

    public final static String[] passwordPolicys = new String[] {DamengMessages.dameng_sql_util_passwordPolicy1,
        DamengMessages.dameng_sql_util_passwordPolicy2, DamengMessages.dameng_sql_util_passwordPolicy3,
        DamengMessages.dameng_sql_util_passwordPolicy4, DamengMessages.dameng_sql_util_passwordPolicy5,
        DamengMessages.dameng_sql_util_passwordPolicy6};

    private long id;

    private String name;

    @SuppressWarnings("unused")
    private int userType;

    private int pwdPolicy;

    private boolean hasUserEncryptPassword;

    private int pwdMinLen;

    private Timestamp createDate;

    private String tableSpace;

    private boolean locked;

    private transient String password;

    public DamengUser(DamengDataSource dataSource) {
        super(dataSource);
    }

    public DamengUser(DamengDataSource dataSource, ResultSet resultSet) {
        super(dataSource);
        this.id = JDBCUtils.safeGetLong(resultSet, "ID");
        this.name = JDBCUtils.safeGetString(resultSet, "NAME");
        this.pwdPolicy = JDBCUtils.safeGetInt(resultSet, "PWD_POLICY");
        this.hasUserEncryptPassword = !JDBCUtils.safeGetString(resultSet, "ENCRYPT_KEY").isEmpty();
        this.tableSpace = JDBCUtils.safeGetString(resultSet, "TABLE_SPACE");
        if (this.tableSpace == null || this.tableSpace.length() == 0) {
            this.tableSpace = "<--DEFAULT-->";
        }
        this.pwdMinLen = JDBCUtils.safeGetInt(resultSet, "PARA_VALUE");
        this.userType = JDBCUtils.safeGetInt(resultSet, "INFO1");
        this.createDate = JDBCUtils.safeGetTimestamp(resultSet, "CRTDATE");
        this.locked = JDBCUtils.safeGetInt(resultSet, "LOCKED_STATUS") == 1 ? true : false;
    }

    @Property(order = 1)
    public long getId() {
        return id;
    }

    @NotNull
    @Override
    @Property(viewable = true, order = 2)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Property(viewable = true, order = 3)
    public Timestamp getCreateDate() {
        return createDate;
    }

    @Property(viewable = true, order = 4)
    public String getPwdPolicy() {
        StringBuilder policyStr = new StringBuilder(32);
        int policy = this.pwdPolicy;
        if ((policy & 0x0001) != 0) {
            policyStr.append(passwordPolicys[1]);
            policyStr.append("&");
        }
        if ((policy & 0x0002) != 0) {
            policyStr.append(passwordPolicys[2] + pwdMinLen);
            policyStr.append("&");
        }
        if ((policy & 0x0004) != 0) {
            policyStr.append(passwordPolicys[3]);
            policyStr.append("&");
        }
        if ((policy & 0x0008) != 0) {
            policyStr.append(passwordPolicys[4]);
            policyStr.append("&");
        }
        if ((policy & 0x0010) != 0) {
            policyStr.append(passwordPolicys[5]);
            policyStr.append("&");
        }
        if (policyStr.length() == 0) {
            return DamengMessages.dameng_sql_util__val_def;
        } else {
            return policyStr.deleteCharAt(policyStr.length() - 1).toString();
        }
    }

    @Property(viewable = true, order = 5)
    public boolean getHasUserEncryptPassword() {
        return hasUserEncryptPassword;
    }

    @Property(viewable = true, order = 6)
    public String getTableSpace() {
        return tableSpace;
    }

    @Property(viewable = true, order = 7)
    public boolean getLocked() {
        return locked;
    }

    @Property(order = 8)
    @LazyProperty(cacheValidator = DamengTablespace.TablespaceReferenceValidator.class)
    public Object getDefaultTablespace(DBRProgressMonitor monitor) throws DBException {
        return DamengTablespace.resolveTablespaceReference(monitor, this, "defaultTablespace");
    }

    @Property(order = 9)
    @LazyProperty(cacheValidator = DamengTablespace.TablespaceReferenceValidator.class)
    public Object getTempTablespace(DBRProgressMonitor monitor) throws DBException {
        return DamengTablespace.resolveTablespaceReference(monitor, this, "tempTablespace");
    }

    @Property(order = 10)
    @LazyProperty(cacheValidator = ProfileReferenceValidator.class)
    public Object getProfile(DBRProgressMonitor monitor) throws DBException {
        return DamengUtils.resolveLazyReference(monitor, getDataSource(), getDataSource().profileCache, this,
            "profile");
    }

    /**
     * Passwords are never read from database. It is used to create/alter
     * schema/user
     *
     * @return password or null
     */
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    @Association
    public Collection<DamengPrivRole> getRolePrivs(DBRProgressMonitor monitor) throws DBException {
        return rolePrivCache.getAllObjects(monitor, this);
    }

    @Nullable
    @Override
    public DBSObject refreshObject(@NotNull DBRProgressMonitor monitor) throws DBException {
        return super.refreshObject(monitor);
    }

    @Override
    public Object getLazyReference(Object propertyId) {
        return null;
    }

    public static class ProfileReferenceValidator implements IPropertyCacheValidator<DamengUser> {
        @Override
        public boolean isPropertyCached(DamengUser object, Object propertyId) {
            return object.getLazyReference(propertyId) instanceof DamengUserProfile
                || object.getLazyReference(propertyId) == null
                || object.getDataSource().profileCache.isFullyCached();
        }
    }
    
}
