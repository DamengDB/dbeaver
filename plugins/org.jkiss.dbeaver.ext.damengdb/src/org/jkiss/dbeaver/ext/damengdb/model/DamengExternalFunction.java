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
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.rdb.DBSProcedureType;

public class DamengExternalFunction extends DamengExternalStandalone {

    public DamengExternalFunction(DamengSchema schema, ResultSet dbResult) {
        super(schema, dbResult);
    }

    public DamengExternalFunction(DamengSchema dmSchema, String name, DBSProcedureType procedureType) {
        super(dmSchema, name, procedureType);
    }

    @Override
    public DBEPersistAction[] getCompileActions(DBRProgressMonitor monitor) {
        return null;
    }

    @Property(viewable = true, order = 4)
    @Override
    public String getObjectTypeStr() {
        switch (externalFunctionType) {
            case DamengConstants.JAVA_EXTERNAL_FUNCTION:
                return objectTypeStr + " ( Java )";

            case DamengConstants.C_EXTERNAL_FUNCTION:
                return objectTypeStr + " ( C )";

            case DamengConstants.PYTHON2_EXTERNAL_FUNCTION:
                return objectTypeStr + " ( Python2 )";

            case DamengConstants.PYTHON3_EXTERNAL_FUNCTION:
                return objectTypeStr + " ( Python3 )";

            default:
                return objectTypeStr;
        }

    }

    @Override
    public DBSObject refreshObject(@NotNull DBRProgressMonitor monitor) throws DBException {
        return getSchema().externalFunctionsCache.refreshObject(monitor, getSchema(), this);
    }
    
}
