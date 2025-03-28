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

import java.sql.SQLException;
import java.sql.Struct;
import java.util.Map;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCStructImpl;
import org.jkiss.dbeaver.model.impl.jdbc.data.JDBCComposite;
import org.jkiss.dbeaver.model.impl.jdbc.data.JDBCCompositeDynamic;
import org.jkiss.dbeaver.model.impl.jdbc.data.JDBCCompositeMap;
import org.jkiss.dbeaver.model.impl.jdbc.data.JDBCCompositeStatic;
import org.jkiss.dbeaver.model.impl.jdbc.data.JDBCCompositeUnknown;
import org.jkiss.dbeaver.model.impl.jdbc.data.handlers.JDBCStructValueHandler;
import org.jkiss.dbeaver.model.struct.DBSDataType;
import org.jkiss.dbeaver.model.struct.DBSTypedObject;

/**
 * Object type support
 */
public class DamengObjectValueHandler extends JDBCStructValueHandler {

    public static final DamengObjectValueHandler INSTANCE = new DamengObjectValueHandler();

    private static final Log log = Log.getLog(DamengObjectValueHandler.class);

    @Override
    public Object getValueFromObject(@NotNull DBCSession session, @NotNull DBSTypedObject type, Object object,
                                     boolean copy, boolean validateValue) throws DBCException {

        if (object instanceof JDBCComposite) {
            return copy ? ((JDBCComposite) object).cloneValue(session.getProgressMonitor()) : object;
        }

        String typeName;
        try {
            if (object instanceof Struct) {
                typeName = ((Struct) object).getSQLTypeName();
            } else {
                typeName = type.getTypeName();
            }
        } catch (SQLException e) {
            throw new DBCException(e, session.getExecutionContext());
        }
        DBSDataType dataType = null;
        try {
            dataType = DBUtils.resolveDataType(session.getProgressMonitor(), session.getDataSource(), typeName);
        } catch (DBException e) {
            log.debug("Error resolving data type '" + typeName + "'", e);
        }
        if (dataType == null) {
            if (object instanceof Struct) {
                return new JDBCCompositeDynamic(session, (Struct) object, null);
            } else {
                return new JDBCCompositeUnknown(session, object);
            }
        }
        if (object == null) {
            return new JDBCCompositeStatic(session, dataType, new JDBCStructImpl(dataType.getTypeName(), null, ""));
        } else if (object instanceof Struct) {
            return new DamengTopoStatic(session, dataType, (Struct) object);
        } else if (object instanceof Map) {
            return new JDBCCompositeMap(session, dataType, (Map<?, ?>) object);
        } else {
            return new JDBCCompositeUnknown(session, object);
        }
    }

}
