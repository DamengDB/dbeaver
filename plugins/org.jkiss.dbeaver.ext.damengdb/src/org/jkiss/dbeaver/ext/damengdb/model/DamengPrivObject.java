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

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.access.DBAPrivilege;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

/**
 * DamengPrivObject
 */
public class DamengPrivObject extends DamengObject<DamengGrantee> implements DBAPrivilege {
    
	private String objectType;

    private String privilege;

    private String grantor;

    private boolean grantable;

    public DamengPrivObject(DamengGrantee grantee, ResultSet resultSet) {
        super(grantee, JDBCUtils.safeGetString(resultSet, DamengConstants.COL_TABLE_NAME), true);
        String subType = JDBCUtils.safeGetString(resultSet, DamengConstants.COLUMN_SUB_TYPE);
        if (subType != null && subType.equalsIgnoreCase("PKG")) {
            this.objectType = "PACKAGE";
        } else if (subType != null && subType.equalsIgnoreCase("TRIG")) {
            this.objectType = "TRIGGER";
        } else {
            this.objectType = subType;
        }
        this.privilege = JDBCUtils.safeGetString(resultSet, "PRIVILEGE");
        this.grantor = JDBCUtils.safeGetString(resultSet, "GRANTOR_NAME");
        this.grantable = JDBCUtils.safeGetBoolean(resultSet, "GRANTABLE", DamengConstants.RESULT_YES_VALUE);
    }

    @NotNull
    @Override
    public String getName() {
        return super.getName();
    }

    @Property(order = 4, viewable = true)
    public String getObjectType() {
        return objectType;
    }

    @Property(order = 5, viewable = true, supportsPreview = true)
    public String getObject(DBRProgressMonitor monitor) throws DBException {
        return name;
    }

    @Property(viewable = true, order = 10)
    public String getPrivilege() {
        return privilege;
    }

    @Property(viewable = true, order = 11)
    public String getGrantor() {
        return grantor;
    }

    @Property(viewable = true, order = 12)
    public boolean isGrantable() {
        return grantable;
    }
    
}
