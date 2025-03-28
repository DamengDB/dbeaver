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

import java.sql.Blob;
import java.sql.SQLException;
import java.sql.Struct;
import java.sql.Types;

import org.jkiss.dbeaver.model.exec.DBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.data.handlers.JDBCStringValueHandler;
import org.jkiss.dbeaver.model.struct.DBSTypedObject;

import com.dameng.geotools.util.dm.DmGeo2Util;

/**
 * Dameng Raster geometry handler
 */
public class DamengRasterValueHandler extends JDBCStringValueHandler {

    public static final DamengRasterValueHandler INSTANCE = new DamengRasterValueHandler();

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
    public void bindParameter(JDBCSession session, JDBCPreparedStatement statement, DBSTypedObject paramType,
                              int paramIndex, Object value) throws SQLException {
        if (paramType.getTypeID() == Types.OTHER) {
            if (value == null) {
                statement.setNull(paramIndex, paramType.getTypeID());
            } else {
                statement.setObject(paramIndex, value.toString(), Types.OTHER);
            }
        } else {
            super.bindParameter(session, statement, paramType, paramIndex, value);
        }
    }

    @Override
    protected Object fetchColumnValue(DBCSession session, JDBCResultSet resultSet, DBSTypedObject type, int index)
        throws SQLException {
        Struct geoStruct = (Struct) resultSet.getObject(index);
        Object[] geoObj = geoStruct.getAttributes();
        Blob rSerBlob = (Blob) geoObj[0];
        int len = (int) rSerBlob.length();
        byte[] rserbyte = rSerBlob.getBytes(1, len);

        byte[] rastWkb = DmGeo2Util.rserToWkb(rserbyte);

        DamengObject dmObject = new DamengObject();
        dmObject.setType("raster");
        dmObject.setValue(bytesToHexString(rastWkb));
        return dmObject;
    }

}
