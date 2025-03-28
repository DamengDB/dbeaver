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
import java.util.Iterator;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCStatement;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCObjectCache;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObjectContainer;
import org.jkiss.dbeaver.model.struct.rdb.DBSProcedure;
import org.jkiss.dbeaver.model.struct.rdb.DBSProcedureType;
import org.jkiss.utils.IntKeyMap;

/**
 * GenericProcedure
 */
public abstract class DamengProcedureBase<PARENT extends DBSObjectContainer> extends DamengObject<PARENT>
    implements DBSProcedure {
	
    static final Log log = Log.getLog(DamengProcedureBase.class);
    private final ArgumentsCache argumentsCache = new ArgumentsCache();
    private DBSProcedureType procedureType;

    public DamengProcedureBase(PARENT parent, String name, long objectId, DBSProcedureType procedureType) {
        super(parent, name, objectId, true);
        this.procedureType = procedureType;
    }

    // Procedure/Function/External Function is not distinguished by
    // getProcedureType
    @Override
    public DBSProcedureType getProcedureType() {
        return procedureType;
    }

    public void setProcedureType(DBSProcedureType procedureType) {
        this.procedureType = procedureType;
    }

    @Override
    public DBSObjectContainer getContainer() {
        return getParentObject();
    }

    public abstract DamengSchema getSchema();

    public abstract Integer getOverloadNumber();

    @Override
    public Collection<DamengProcedureArgument> getParameters(DBRProgressMonitor monitor) throws DBException {
        return argumentsCache.getAllObjects(monitor, this);
    }

    @Association
    public Collection<DamengDependencyGroup> getDependencies(DBRProgressMonitor monitor) {
        return DamengDependencyGroup.of(this);
    }

    @SuppressWarnings("rawtypes")
    static class ArgumentsCache extends JDBCObjectCache<DamengProcedureBase, DamengProcedureArgument> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session,
                                                        @NotNull DamengProcedureBase procedure) throws SQLException {
            JDBCPreparedStatement dbStat = session.prepareStatement("SELECT * FROM "
                + DamengUtils.getSysSchemaPrefix(procedure.getDataSource()) + "ALL_ARGUMENTS " + "WHERE "
                + (procedure.getObjectId() <= 0 ? "OWNER=? AND OBJECT_NAME=? AND PACKAGE_NAME=? " : "OBJECT_ID=? ")
                + (procedure.getOverloadNumber() != null ? "AND OVERLOAD=? " : "AND OVERLOAD IS NULL ")
                + "\nORDER BY SEQUENCE");
            int paramNum = 1;
            if (procedure.getObjectId() <= 0) {
                dbStat.setString(paramNum++, procedure.getSchema().getName());
                dbStat.setString(paramNum++, procedure.getName());
                dbStat.setString(paramNum++, procedure.getContainer().getName());
            } else {
                dbStat.setLong(paramNum++, procedure.getObjectId());
            }
            if (procedure.getOverloadNumber() != null) {
                dbStat.setInt(paramNum, procedure.getOverloadNumber());
            }
            return dbStat;
        }

        @Override
        protected DamengProcedureArgument fetchObject(@NotNull JDBCSession session,
                                                      @NotNull DamengProcedureBase procedure, @NotNull JDBCResultSet resultSet)
            throws SQLException, DBException {
            return new DamengProcedureArgument(session.getProgressMonitor(), procedure, resultSet);
        }

        @Override
        protected void invalidateObjects(DBRProgressMonitor monitor, DamengProcedureBase owner,
                                         Iterator<DamengProcedureArgument> objectIter) {
            IntKeyMap<DamengProcedureArgument> argStack = new IntKeyMap<>();
            while (objectIter.hasNext()) {
                DamengProcedureArgument argument = objectIter.next();
                final int curDataLevel = argument.getDataLevel();
                argStack.put(curDataLevel, argument);
                if (curDataLevel > 0) {
                    objectIter.remove();
                    DamengProcedureArgument parentArgument = argStack.get(curDataLevel - 1);
                    if (parentArgument == null) {
                        log.error("Broken arguments structure for '"
                            + argument.getParentObject().getFullyQualifiedName(DBPEvaluationContext.DDL)
                            + "' - no parent argument for argument " + argument.getSequence());
                    } else {
                        parentArgument.addAttribute(argument);
                    }
                }
            }
        }

    }

}
