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
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.damengdb.model.source.DamengSourceObject;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCStatement;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCObjectCache;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObjectState;

public class DamengDomain extends DamengSchemaObject implements DamengSourceObject {
	
    private final DomainPrivCache domainPrivCache = new DomainPrivCache();
    private DamengSchema schema;
    private Date createdDate;
    private String dataType;
    private String size;
    private String scale;
    private String defaultValue;
    private String constraintValue;
    private String domainDefinationtext;

    public DamengDomain(DamengSchema schema, ResultSet dbResult) {
        // Domain name is show by DmObject
        super(schema, JDBCUtils.safeGetString(dbResult, "DOMAIN_NAME"), true);
        this.createdDate = JDBCUtils.safeGetTimestamp(dbResult, "CRTDATE");
        this.dataType = JDBCUtils.safeGetString(dbResult, "DATA_TYPE");
        this.size = JDBCUtils.safeGetString(dbResult, "LEN");
        this.scale = JDBCUtils.safeGetString(dbResult, "SCALE") == null ? ""
            : JDBCUtils.safeGetString(dbResult, "SCALE");
        this.defaultValue = JDBCUtils.safeGetString(dbResult, "DEFVAL") == null ? ""
            : JDBCUtils.safeGetString(dbResult, "DEFVAL");
        this.constraintValue = JDBCUtils.safeGetString(dbResult, "CHECKINFO");
        this.schema = schema;
    }

    @Override
    public void setObjectDefinitionText(String source) {
    }

    @Override
    @Property(hidden = true, editable = false, updatable = false, order = -1)
    public String getObjectDefinitionText(DBRProgressMonitor monitor, Map<String, Object> options) throws DBException {
        if (domainDefinationtext == null) {
            StringBuilder definationText = new StringBuilder();
            definationText.append("--");
            if (this.getSchema().getName() != null) {
                definationText.append("\"" + this.getSchema().getName() + "\"");
                definationText.append(".");
            }
            definationText.append("\"" + getName() + "\"");
            definationText.append(DamengConstants.LINE_SEPARATOR + DamengConstants.LINE_SEPARATOR);

            definationText.append("CREATE DOMAIN ");
            StringBuilder fullName = new StringBuilder();
            fullName.append("\"");
            fullName.append(this.getSchemaName());
            fullName.append("\"");
            fullName.append(".");
            fullName.append("\"");
            fullName.append(this.getName());
            fullName.append("\"");
            definationText.append(fullName.toString() + " ");
            definationText.append(DamengUtils.getRealTypeName(this.getDataType(), this.getSize(), this.getScale()));

            // default value
            if (!this.getDefaultValue().isEmpty()) {
                definationText.append(DamengConstants.LINE_SEPARATOR);
                definationText.append("DEFUALT ");
                definationText.append(this.getDefaultValue());
            }

            // constraint value
            if (!this.getConstraintValue().isEmpty()) {
                definationText.append(DamengConstants.LINE_SEPARATOR);
                definationText.append("check(" + this.getConstraintValue() + ")");
            }

            domainDefinationtext = definationText.toString();
        }

        return domainDefinationtext;
    }

    @Override
    public @NotNull DBSObjectState getObjectState() {
        return DBSObjectState.NORMAL;
    }

    @Override
    public void refreshObjectState(@NotNull DBRProgressMonitor monitor) throws DBCException {
    }

    @Override
    public DamengSourceType getSourceType() {
        return DamengSourceType.DOMAIN;
    }

    @Override
    public DBEPersistAction[] getCompileActions(DBRProgressMonitor monitor) throws DBCException {
        return null;
    }

    @Property(viewable = true, editable = false, order = 2)
    public String getDataType() {
        return dataType;
    }

    public DamengSchema getSchema() {
        return schema;
    }

    @Property(viewable = true, editable = false, order = 5)
    public String getSchemaName() {
        return schema.getName();
    }

    public String getSize() {
        return size;
    }

    public String getScale() {
        return scale;
    }

    @Property(viewable = true, editable = false, order = 3)
    public String getDefaultValue() {
        return defaultValue;
    }

    @Property(viewable = true, editable = false, order = 4)
    public String getConstraintValue() {
        return constraintValue;
    }

    @Property(viewable = true, editable = false, order = 6)
    public Date getCreatedDate() {
        return createdDate;
    }

    @Association
    public Collection<DamengDependencyGroup> getDependencies(DBRProgressMonitor monitor) {
        return DamengDependencyGroup.of(this);
    }

    @Association
    public Collection<DamengPrivDomain> getDomainPrivs(DBRProgressMonitor monitor) throws DBException {
        return domainPrivCache.getAllObjects(monitor, this);
    }

    static class DomainPrivCache extends JDBCObjectCache<DamengDomain, DamengPrivDomain> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengDomain domain)
            throws SQLException {
            boolean hasDBA = domain.getDataSource().isViewAvailable(session.getProgressMonitor(),
                DamengConstants.SCHEMA_SYS, DamengConstants.VIEW_DBA_TAB_PRIVS);
            final JDBCPreparedStatement dbStat = session
                .prepareStatement("SELECT p.*\n" + "FROM " + (hasDBA ? "DBA_TAB_PRIVS p" : "ALL_TAB_PRIVS p") + "\n"
                    + "WHERE p." + (hasDBA ? "OWNER" : "TABLE_SCHEMA") + "=? AND p.TABLE_NAME =?");
            dbStat.setString(1, domain.getSchema().getName());
            dbStat.setString(2, domain.getName());
            return dbStat;
        }

        @Override
        protected DamengPrivDomain fetchObject(@NotNull JDBCSession session, @NotNull DamengDomain domain,
                                               @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new DamengPrivDomain(domain, resultSet);
        }
    }
    
}
