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
package org.jkiss.dbeaver.ext.damengdb.model;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCStatement;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCObjectCache;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSEntityMethod;
import org.jkiss.dbeaver.model.struct.DBSParametrizedObject;
import org.jkiss.utils.CommonUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;

/**
 * Dameng data type attribute
 */
public class DamengDataTypeProc extends DamengDataTypeMember implements DBSEntityMethod, DBSParametrizedObject {

    private final ParameterCache parameterCache;
    private String methodType;
    private boolean flagFinal;
    private boolean flagInstantiable;
    private boolean flagOverriding;
    private DamengDataType resultType;
    private DamengDataTypeModifier resultTypeMod;

    public DamengDataTypeProc(DamengDataType dataType) {
        super(dataType);
        this.parameterCache = new ParameterCache();
    }

    public DamengDataTypeProc(DBRProgressMonitor monitor, DamengDataType dataType, ResultSet dbResult) {
        super(dataType, dbResult);
        this.name = JDBCUtils.safeGetString(dbResult, "METHOD_NAME");
        this.number = JDBCUtils.safeGetInt(dbResult, "METHOD_NO");

        this.methodType = JDBCUtils.safeGetString(dbResult, "METHOD_TYPE");

        this.flagFinal = JDBCUtils.safeGetBoolean(dbResult, "FINAL", DamengConstants.YES);
        this.flagInstantiable = JDBCUtils.safeGetBoolean(dbResult, "INSTANTIABLE", DamengConstants.YES);
        this.flagOverriding = JDBCUtils.safeGetBoolean(dbResult, "OVERRIDING", DamengConstants.YES);

        boolean hasParameters = JDBCUtils.safeGetInt(dbResult, "PARAMETERS") > 0;
        this.parameterCache = hasParameters ? new ParameterCache() : null;

        String resultTypeName = JDBCUtils.safeGetString(dbResult, "RESULT_TYPE_NAME");
        if (!CommonUtils.isEmpty(resultTypeName)) {
            this.resultType = DamengDataType.resolveDataType(monitor, getDataSource(),
                JDBCUtils.safeGetString(dbResult, "RESULT_TYPE_OWNER"), resultTypeName);
            this.resultTypeMod = DamengDataTypeModifier
                .resolveTypeModifier(JDBCUtils.safeGetString(dbResult, "RESULT_TYPE_MOD"));
        }
    }

    @Property(viewable = true, editable = true, order = 5)
    public String getMethodType() {
        return methodType;
    }

    @Property(id = "dataType", viewable = true, order = 6)
    public DamengDataType getResultType() {
        return resultType;
    }

    @Property(id = "dataTypeMod", viewable = true, order = 7)
    public DamengDataTypeModifier getResultTypeMod() {
        return resultTypeMod;
    }

    @Property(viewable = true, order = 8)
    public boolean isFinal() {
        return flagFinal;
    }

    @Property(viewable = true, order = 9)
    public boolean isInstantiable() {
        return flagInstantiable;
    }

    @Property(viewable = true, order = 10)
    public boolean isOverriding() {
        return flagOverriding;
    }

    @Association
    public Collection<DamengDataTypeMethodParameter> getParameters(DBRProgressMonitor monitor) throws DBException {
        return parameterCache == null ? null : parameterCache.getAllObjects(monitor, this);
    }

    private class ParameterCache extends JDBCObjectCache<DamengDataTypeProc, DamengDataTypeMethodParameter> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengDataTypeProc owner)
            throws SQLException {
            final JDBCPreparedStatement dbStat = session.prepareStatement(
                "SELECT PARAM_NAME,PARAM_NO,PARAM_MODE,PARAM_TYPE_OWNER,PARAM_TYPE_NAME,PARAM_TYPE_MOD "
                    + "FROM ALL_METHOD_PARAMS "
                    + "WHERE OWNER=? AND TYPE_NAME=? AND METHOD_NAME=? AND METHOD_NO=?");
            DamengDataType dataType = getOwnerType();
            if (dataType.getSchema() == null) {
                dbStat.setNull(1, Types.VARCHAR);
            } else {
                dbStat.setString(1, dataType.getSchema().getName());
            }
            dbStat.setString(2, dataType.getName());
            dbStat.setString(3, getName());
            dbStat.setInt(4, getNumber());
            return dbStat;
        }

        @Override
        protected @Nullable DamengDataTypeMethodParameter fetchObject(@NotNull JDBCSession arg0,
                                                                      @NotNull DamengDataTypeProc arg1, @NotNull JDBCResultSet arg2)
            throws SQLException, DBException {
            return null;
        }
    }
    
}
