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

import java.io.Serializable;

@SuppressWarnings("serial")
public class DamengPrivilege implements Serializable {
	
    public final static int PRIV_ACTION_TYPE_GRANT = 1;
    public final static int PRIV_ACTION_TYPE_REVOKE = 2;
    private String id = DamengDatabaseObject.EMPTY;
    private String roleName = DamengDatabaseObject.EMPTY;
    private String privName = DamengDatabaseObject.EMPTY;
    private String userName = DamengDatabaseObject.EMPTY;
    private boolean beGrant = false;
    private boolean canGrant = false;
    // Modify the Privilege of the object which belong to the
    // Schema(eg:Table,View...),it is Grant or Revoke
    private int privActionType = PRIV_ACTION_TYPE_GRANT;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getPrivName() {
        return privName;
    }

    public void setPrivName(String privName) {
        this.privName = privName;
    }

    public boolean isBeGrant() {
        return beGrant;
    }

    public void setBeGrant(boolean beGrant) {
        this.beGrant = beGrant;
    }

    public boolean isCanGrant() {
        return canGrant;
    }

    public void setCanGrant(boolean canGrant) {
        this.canGrant = canGrant;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public int getPrivActionType() {
        return privActionType;
    }

    public void setPrivActionType(int privActionType) {
        this.privActionType = privActionType;
    }

    @Override
    public String toString() {
        return this.id;
    }

    public Object clone() {
        DamengPrivilege obj = new DamengPrivilege();
        obj.beGrant = this.beGrant;
        obj.canGrant = this.canGrant;
        obj.id = this.id != null ? new String(this.id) : null;
        obj.privName = this.privName != null ? new String(this.privName) : null;
        obj.roleName = this.roleName != null ? new String(this.roleName) : null;
        obj.userName = this.userName != null ? new String(this.userName) : null;

        return obj;
    }
}

class UserRole {
    public final static int TYPE_USER = 1;

    public final static int TYPE_ROLE = 2;

    private int type = TYPE_USER;

    private DamengGrantUser user;

    private DamengGrantRole role;

    public UserRole(int type, DamengGrantUser user, DamengGrantRole role) {
        this.type = type;
        this.user = user;
        this.role = role;
    }

    public int getType() {
        return type;
    }

    public DamengGrantUser getUser() {
        return user;
    }

    public DamengGrantRole getRole() {
        return role;
    }

    public String getFullName() {
        if (user != null) {
            return user.getFullName();
        } else {
            return role.getFullName();
        }
    }
    
}
