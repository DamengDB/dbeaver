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
package org.jkiss.dbeaver.ext.damengdb.data;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.ext.damengdb.model.DamengExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.data.handlers.JDBCStringValueHandler;
import org.jkiss.dbeaver.model.struct.DBSTypedObject;
import org.jkiss.utils.CommonUtils;

import com.dameng.geotools.util.dm.DmGeo2Util;

import java.sql.Blob;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Struct;

/**
 * Dameng Box
 */
public class DamengStringValueHandler extends JDBCStringValueHandler {

    public static final DamengStringValueHandler INSTANCE = new DamengStringValueHandler();

    private static Struct dmBox;

    protected static String bytesToHexString(byte[] bts) {
        StringBuilder stringBuilder = new StringBuilder("");
        for (int i = 0; i < bts.length; i++) {
            int temp = bts[i] & 0xFF;
            String htemp = Integer.toHexString(temp);
            if (htemp.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(htemp);
        }
        return stringBuilder.toString();
    }

    @Override
    protected Object fetchColumnValue(DBCSession session, JDBCResultSet resultSet, DBSTypedObject type, int index)
        throws SQLException {
        Struct geostruct = (Struct) resultSet.getObject(index);
        if (geostruct == null) {
            return null;
        }

        Object[] geoobj = geostruct.getAttributes();
        Blob gSerblob = (Blob) geoobj[0];
        int len = (int) gSerblob.length();
        byte[] gBox = gSerblob.getBytes(1, len);
        if (type.getTypeName().equalsIgnoreCase("SYSGEO2.ST_BOX2D")) {
            return DmGeo2Util.box2d(gBox).toString();

        } else {
            return DmGeo2Util.box3d(gBox).toString();

        }
    }

    @Override
    public void bindParameter(JDBCSession session, JDBCPreparedStatement statement, DBSTypedObject paramType,
                              int paramIndex, Object value) throws SQLException {
        if (value == null) {
            statement.setNull(paramIndex, paramType.getTypeID());
        } else if (value instanceof String) {
            if (paramType.getTypeName().equalsIgnoreCase("SYSGEO2.ST_BOX2D")) {
                statement.setObject(paramIndex, dmBox);
            } else if (paramType.getTypeName().equalsIgnoreCase("SYSGEO2.ST_BOX3D")) {
                statement.setObject(paramIndex, dmBox);
            }
        }
    }

    @Override
    public Object getValueFromObject(@NotNull DBCSession session, @NotNull DBSTypedObject type, Object object,
                                     boolean copy, boolean validateValue) throws DBCException {
        if (object == null) {
            return null;
        } else if (object instanceof String) {
            try {
                return makeBoxFromWKT(session, (String) object, type);
            } catch (Throwable e) {
                throw new DBCException(e.getMessage(), e);
            }
        } else {
            return null;
        }
    }

    protected String makeBoxFromWKT(DBCSession session, String wkt, DBSTypedObject paramType) throws Throwable {
        if (CommonUtils.isEmpty(wkt)) {
            dmBox = null;
            return null;
        }
        try {
            DBCExecutionContext executionContext = session.getExecutionContext();
            Connection connDm = ((DamengExecutionContext) executionContext).getConnection(null);
            Object[] attrs = new Object[1];
            Blob blob = connDm.createBlob();

            if (paramType.getTypeName().equalsIgnoreCase("SYSGEO2.ST_BOX2D")) {
                byte[] box2d = DmGeo2Util.box2d(wkt).toBytes();
                blob.setBytes(1, box2d);
                attrs[0] = blob;
                dmBox = connDm.createStruct("SYSGEO2.ST_BOX2D", attrs);
            } else if (paramType.getTypeName().equalsIgnoreCase("SYSGEO2.ST_BOX3D")) {
                byte[] box3d = DmGeo2Util.box3d(wkt).toBytes();
                blob.setBytes(1, box3d);
                attrs[0] = blob;
                dmBox = connDm.createStruct("SYSGEO2.ST_BOX3D", attrs);
            }

            return wkt;

        } catch (Exception e) {
            throw new DBCException("Error parsing geometry value from string", e);
        }
    }

}
