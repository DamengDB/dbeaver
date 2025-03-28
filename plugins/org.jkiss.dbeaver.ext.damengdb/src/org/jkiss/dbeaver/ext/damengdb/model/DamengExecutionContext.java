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
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.dpi.DPIContainer;
import org.jkiss.dbeaver.model.connection.DBPConnectionBootstrap;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCExecutionContextDefaults;
import org.jkiss.dbeaver.model.exec.DBCExecutionPurpose;
import org.jkiss.dbeaver.model.exec.DBCFeatureNotSupportedException;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCExecutionContext;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCRemoteInstance;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.rdb.DBSCatalog;
import org.jkiss.utils.CommonUtils;

import java.sql.SQLException;

/**
 * DamengExecutionContext
 */
public class DamengExecutionContext extends JDBCExecutionContext
    implements DBCExecutionContextDefaults<DBSCatalog, DamengSchema> {
	
    private static final Log log = Log.getLog(DamengExecutionContext.class);

    private String activeSchemaName;

    DamengExecutionContext(@NotNull JDBCRemoteInstance instance, String purpose) {
        super(instance, purpose);
    }

    @DPIContainer
    @NotNull
    @Override
    public DamengDataSource getDataSource() {
        return (DamengDataSource) super.getDataSource();
    }

    @NotNull
    @Override
    public DamengExecutionContext getContextDefaults() {
        return this;
    }

    public String getActiveSchemaName() {
        return activeSchemaName;
    }

    @Override
    public DBSCatalog getDefaultCatalog() {
        return null;
    }

    @Override
    public DamengSchema getDefaultSchema() {
        try {
            return activeSchemaName == null ? null
                : getDataSource().getSchema(new VoidProgressMonitor(), activeSchemaName);
        } catch (Exception e) {
            log.error(e);
            return null;
        }
    }

    @Override
    public boolean supportsCatalogChange() {
        return false;
    }

    @Override
    public boolean supportsSchemaChange() {
        return true;
    }

    @Override
    public void setDefaultCatalog(DBRProgressMonitor monitor, DBSCatalog catalog, DamengSchema schema)
        throws DBCException {
        throw new DBCFeatureNotSupportedException();
    }

    @Override
    public void setDefaultSchema(DBRProgressMonitor monitor, DamengSchema schema) throws DBCException {
        final DamengSchema oldSelectedEntity = getDefaultSchema();
        if (schema == null || oldSelectedEntity == schema) {
            return;
        }
        setCurrentSchema(monitor, schema);
        activeSchemaName = schema.getName();

        // Send notifications
        DBUtils.fireObjectSelectionChange(oldSelectedEntity, schema, this);
    }

    @Override
    public boolean refreshDefaults(DBRProgressMonitor monitor, boolean useBootstrapSettings) throws DBException {
        // Check default active schema
        try (JDBCSession session = openSession(monitor, DBCExecutionPurpose.META, "Query active schema")) {
            if (useBootstrapSettings) {
                DBPConnectionBootstrap bootstrap = getBootstrapSettings();
                String bootstrapSchemaName = bootstrap.getDefaultSchemaName();
                if (!CommonUtils.isEmpty(bootstrapSchemaName) && !bootstrapSchemaName.equals(activeSchemaName)) {
                    setCurrentSchema(monitor, bootstrap.getDefaultSchemaName());
                }
            }
            // Get active schema
            this.activeSchemaName = DamengUtils.getCurrentSchema(session);
            if (this.activeSchemaName != null) {
                if (this.activeSchemaName.isEmpty()) {
                    this.activeSchemaName = null;
                }
            }
        } catch (Exception e) {
            throw new DBCException(e, this);
        }

        return true;
    }

    void setCurrentSchema(DBRProgressMonitor monitor, DamengSchema object) throws DBCException {
        if (object == null) {
            log.debug("Null current schema");
            return;
        }
        setCurrentSchema(monitor, object.getName());
    }

    private void setCurrentSchema(DBRProgressMonitor monitor, String activeSchemaName) throws DBCException {
        DBSObject oldDefaultSchema = getDefaultSchema();
        try (JDBCSession session = openSession(monitor, DBCExecutionPurpose.UTIL, TASK_TITLE_SET_SCHEMA)) {
            DamengUtils.setCurrentSchema(session, activeSchemaName);
            this.activeSchemaName = activeSchemaName;
            DBSObject newDefaultSchema = getDefaultSchema();
            DBUtils.fireObjectSelectionChange(oldDefaultSchema, newDefaultSchema, this);
        } catch (SQLException e) {
            throw new DBCException(e, this);
        }
    }
    
}
