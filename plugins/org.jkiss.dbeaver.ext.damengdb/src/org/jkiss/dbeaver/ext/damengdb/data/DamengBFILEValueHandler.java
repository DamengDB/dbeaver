/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp and others
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
package org.jkiss.dbeaver.ext.damengdb.data;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.model.data.DBDContent;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.impl.jdbc.data.handlers.JDBCContentValueHandler;
import org.jkiss.dbeaver.model.struct.DBSTypedObject;

import java.sql.SQLException;

/**
 * BFILE type support
 */
public class DamengBFILEValueHandler extends JDBCContentValueHandler {
	
    public static final DamengBFILEValueHandler INSTANCE = new DamengBFILEValueHandler();

    @Override
    protected DBDContent fetchColumnValue(DBCSession session, JDBCResultSet resultSet, DBSTypedObject type, int index)
        throws DBCException, SQLException {
        Object object;

        try {
            object = resultSet.getObject(index);
        } catch (SQLException e) {
            object = null;
        }

        if (object == null) {
            return new DamengContentBFILE(session.getExecutionContext(), null);
        } else {
            return new DamengContentBFILE(session.getExecutionContext(), object);
        }
    }

    @Override
    public DBDContent getValueFromObject(@NotNull DBCSession session, @NotNull DBSTypedObject type, Object object,
                                         boolean copy, boolean validateValue) throws DBCException {
        if (object == null) {
            return new DamengContentBFILE(session.getExecutionContext(), null);
        } else if (object instanceof DamengContentBFILE) {
            return copy ? (DamengContentBFILE) ((DamengContentBFILE) object).cloneValue(session.getProgressMonitor())
                : (DamengContentBFILE) object;
        }
        return super.getValueFromObject(session, type, object, copy, validateValue);
    }

}
