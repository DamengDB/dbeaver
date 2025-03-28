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

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCStatement;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCObjectCache;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSEntityMethod;

/**
 * Dameng data type attribute
 */
public class DamengDataTypeMethod extends DamengDataTypeMember implements DBSEntityMethod {
    
	static final Log log = Log.getLog(DamengProcedureBase.class);
    private DamengDataType resultType;
    @SuppressWarnings("unused")
    private DamengPackage dmPackage;
    private List<DamengSubParameter> paramList;
    private AttributeCache attributeCache = null;

    public DamengDataTypeMethod(DamengDataType dataType, String name, long objectId) {
        super(dataType);
    }

    public DamengDataTypeMethod(DBRProgressMonitor monitor, DamengDataType dataType, DamengSubProcedure procedure) {
        super(dataType);
        this.name = procedure.getName();
        this.paramList = procedure.getParamList();
        attributeCache = paramList.size() > 0 ? new AttributeCache() : null;
        for (int i = 0; i < paramList.size(); i++) {
            DamengSubParameter parameter = paramList.get(i);
            if (parameter.isRet()) {
                this.resultType = DamengDataType.resolveDataType(monitor, getDataSource(), null, parameter.getType());
            }
        }
    }

    public DamengDataTypeMethod(DBRProgressMonitor monitor, DamengPackage dmpackage, DamengSubProcedure procedure) {
        super(dmpackage);
        this.name = procedure.getName();
        this.paramList = procedure.getParamList();
        attributeCache = paramList.size() > 0 ? new AttributeCache() : null;
        for (int i = 0; i < paramList.size(); i++) {
            DamengSubParameter parameter = paramList.get(i);
            if (parameter.isRet()) {
                this.resultType = DamengDataType.resolveDataType(monitor, getDataSource(), null, parameter.getType());
            }
        }
    }

    public DamengDataTypeMethod(DBRProgressMonitor monitor, DamengDataType dataType, DamengSubFunction function) {
        super(dataType);
        this.name = function.getName();
        this.paramList = function.getParamList();
        attributeCache = paramList.size() > 0 ? new AttributeCache() : null;
        for (int i = 0; i < paramList.size(); i++) {
            DamengSubParameter parameter = paramList.get(i);
            if (parameter.isRet()) {
                this.resultType = DamengDataType.resolveDataType(monitor, getDataSource(), null, parameter.getType());
            }
        }
    }

    public DamengDataTypeMethod(DBRProgressMonitor monitor, DamengPackage dmPackage, DamengSubFunction function) {
        super(dmPackage);
        this.name = function.getName();
        this.paramList = function.getParamList();
        attributeCache = paramList.size() > 0 ? new AttributeCache() : null;
        for (int i = 0; i < paramList.size(); i++) {
            DamengSubParameter parameter = paramList.get(i);
            if (parameter.isRet()) {
                this.resultType = DamengDataType.resolveDataType(monitor, getDataSource(), null, parameter.getType());
            }
        }
    }

    @Property(id = "dataType", viewable = true, order = 6)
    public DamengDataType getResultType() {
        return resultType;
    }

    @Association
    public Collection<DamengDataTypeAttribute> getAttributes(DBRProgressMonitor monitor) throws DBException {
        return attributeCache == null ? null : attributeCache.getAllObjects(monitor, this);
    }

    private class AttributeCache extends JDBCObjectCache<DamengDataTypeMethod, DamengDataTypeAttribute> {
        private int number = 0;

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session,
                                                        @NotNull DamengDataTypeMethod owner) throws SQLException {
            final JDBCPreparedStatement dbStat = session.prepareStatement("select 1 from dual connect by level <= ?;");
            dbStat.setInt(1, paramList.size());
            return dbStat;
        }

        @Override
        protected DamengDataTypeAttribute fetchObject(@NotNull JDBCSession session, @NotNull DamengDataTypeMethod owner,
                                                      @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            DamengDataTypeAttribute dmdatatypeattribute;
            if (getParentObject() == null) {
                dmdatatypeattribute = new DamengDataTypeAttribute(session.getProgressMonitor(), getParentPackage(),
                    paramList.get(number), number);
            } else {
                dmdatatypeattribute = new DamengDataTypeAttribute(session.getProgressMonitor(), getParentObject(),
                    paramList.get(number), number);
            }

            number++;
            return dmdatatypeattribute;
        }
    }

}
