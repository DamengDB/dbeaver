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

import java.sql.Types;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.ext.damengdb.model.DamengConstants;
import org.jkiss.dbeaver.model.DBPDataKind;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.data.DBDFormatSettings;
import org.jkiss.dbeaver.model.data.DBDValueHandler;
import org.jkiss.dbeaver.model.data.DBDValueHandlerProvider;
import org.jkiss.dbeaver.model.struct.DBSTypedObject;

/**
 * dm data types provider
 */
public class DamengValueHandlerProvider implements DBDValueHandlerProvider {

    @Override
    public DBDValueHandler getValueHandler(@NotNull DBPDataSource dataSource, DBDFormatSettings preferences,
                                           DBSTypedObject typedObject) {
        final String typeName = typedObject.getTypeName();
        switch (typedObject.getTypeID()) {
            case Types.BLOB:
            case Types.BINARY:
                return DamengBLOBValueHandler.INSTANCE;
            case Types.CLOB:
            case Types.NCLOB:
            case Types.LONGNVARCHAR:
                return DamengCLOBValueHandler.INSTANCE;
            case Types.TIME_WITH_TIMEZONE:
            case Types.TIMESTAMP_WITH_TIMEZONE:
            case DamengConstants.DATA_TYPE_TIMESTAMP_WITH_TIMEZONE:
                return new DamengTimestampValueHandler(preferences, dataSource);
            case Types.STRUCT:
                if (typeName.startsWith("SYSGEO")) {
                    return DamengGeometryValueHandler.INSTANCE;
                } else if (typeName.startsWith("SYSRASTER")) {
                    return DamengRasterValueHandler.INSTANCE;
                } else {
                    return DamengObjectValueHandler.INSTANCE;
                }
        }

        switch (typeName) {
            case DamengConstants.TYPE_NAME_BFILE:
                return DamengBFILEValueHandler.INSTANCE;
        }

        if (typeName.contains(DamengConstants.TYPE_NAME_TIMESTAMP)
            || typedObject.getDataKind() == DBPDataKind.DATETIME) {
            return new DamengTimestampValueHandler(preferences, dataSource);
        } else if (typeName.startsWith("SYSGEO2")) {
            return DamengStringValueHandler.INSTANCE;
        } else {
            return null;
        }
    }

}