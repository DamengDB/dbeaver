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

public class DamengGrantUser extends DamengDatabaseObject {
	
    public final static String USER_NAME_SYSDBA = "SYSDBA";
    public final static String USER_NAME_SYSAUDITOR = "SYSAUDITOR";
    public final static String USER_NAME_SYSSSO = "SYSSSO";
    public final static String USER_NAME_SYSDBO = "SYSDBO";
    public final static String USER_NAME_SYS = "SYS";
    public final static int USER_TYPE_ALL = -1;
    public final static int USER_TYPE_DBA = 0;
    public final static int USER_TYPE_AUDIT = 1;
    public final static int USER_TYPE_POLICY = 2;
    public final static int USER_TYPE_DBO = 3;
    public final static int USER_TYPE_SYS = 4;
    public final static int USER_ROLEID_DBA = 67108864;
    public final static int USER_ROLEID_AUDIT = 67108867;
    public final static int USER_ROLEID_POLICY = 67108870;
    public final static int USER_ROLEID_DBO = 67108873;
    public final static int USER_ID_SYS = 50331648;
    public final static int USER_ID_SYSAUDITOR = 50331650;
    public final static int USER_ID_SYSDBA = 50331649;
    public final static int USER_ID_SYSSSO = 50331651;
    private static final long serialVersionUID = -7526445156495072259L;
    private int userType = USER_TYPE_DBA;

    private boolean locked = false;

    public DamengGrantUser() {
    }

    public DamengGrantUser(int userType) {
        this.userType = userType;
    }

    public int getUserType() {
        return userType;
    }

    public void setUserType(int userType) {
        this.userType = userType;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    @Override
    public void reset(DamengDatabaseObject dbobj) {
        super.reset(dbobj);

        DamengGrantUser user = (DamengGrantUser) dbobj;
        this.setUserType(user.getUserType());
        this.setLocked(user.isLocked());
    }
    
}
