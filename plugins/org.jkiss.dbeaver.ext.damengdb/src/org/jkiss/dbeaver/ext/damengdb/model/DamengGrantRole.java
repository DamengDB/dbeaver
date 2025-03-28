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

public class DamengGrantRole extends DamengDatabaseObject {
	
    // DBA
    public final static String ROLE_NAME_DBA = "DBA";
    public final static String ROLE_NAME_DBA_PUBLIC = "PUBLIC";
    public final static String ROLE_NAME_DBA_RESOURCE = "RESOURCE";
    public final static String ROLE_NAME_DBO_DB_OBJECT_ADMIN = "DB_OBJECT_ADMIN";
    public final static String ROLE_NAME_DBO_DB_OBJECT_OPER = "DB_OBJECT_OPER";
    public final static String ROLE_NAME_DBO_DB_OBJECT_PUBLIC = "DB_OBJECT_PUBLIC";
    // AUDIT
    public final static String ROLE_NAME_AUDIT = "DB_AUDIT_ADMIN";
    public final static String ROLE_NAME_AUDIT_OPER = "DB_AUDIT_OPER";
    public final static String ROLE_NAME_AUDIT_PUBLIC = "DB_AUDIT_PUBLIC";
    // POLICY
    public final static String ROLE_NAME_POLICY = "DB_POLICY_ADMIN";
    public final static String ROLE_NAME_POLICY_OPER = "DB_POLICY_OPER";
    public final static String ROLE_NAME_POLICY_PUBLIC = "DB_POLICY_PUBLIC";
    private static final long serialVersionUID = -1186732409138786552L;
    private int userType;

    public int getUserType() {
        return userType;
    }

    public void setUserType(int userType) {
        this.userType = userType;
    }

    @Override
    public void reset(DamengDatabaseObject dbobj) {
        super.reset(dbobj);

        DamengGrantRole role = (DamengGrantRole) dbobj;
        this.setUserType(role.getUserType());
    }
    
}
