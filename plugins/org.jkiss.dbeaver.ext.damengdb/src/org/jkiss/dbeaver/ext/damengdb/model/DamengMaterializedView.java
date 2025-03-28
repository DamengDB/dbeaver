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
import org.jkiss.dbeaver.ext.damengdb.model.source.DamengSourceObject;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.LazyProperty;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.meta.PropertyGroup;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectLazy;
import org.jkiss.dbeaver.model.struct.DBSObjectState;
import org.jkiss.utils.CommonUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Map;

/**
 * Dameng materialized view
 */
public class DamengMaterializedView extends DamengTableBase
    implements DamengSourceObject, DBSObjectLazy<DamengDataSource> {
	
    private static final Log log = Log.getLog(DamengMaterializedView.class);
    private final AdditionalInfo additionalInfo = new AdditionalInfo();
    private String username;
    private String query;
    private DamengDDLFormat currentDDLFormat;

    public DamengMaterializedView(DamengSchema schema, String name) {
        super(schema, name, false);
    }

    public DamengMaterializedView(DamengSchema schema, ResultSet dbResult) {
        super(schema, dbResult);
        this.username = JDBCUtils.safeGetString(dbResult, "OWNER");
    }

    @PropertyGroup()
    @LazyProperty(cacheValidator = AdditionalInfoValidator.class)
    public AdditionalInfo getAdditionalInfo(DBRProgressMonitor monitor) throws DBCException {
        synchronized (additionalInfo) {
            if (!additionalInfo.loaded && monitor != null) {
                loadAdditionalInfo(monitor);
            }
            return additionalInfo;
        }
    }

    @Override
    public DamengSourceType getSourceType() {
        return DamengSourceType.MATERIALIZED_VIEW;
    }

    @Override
    @Property(hidden = true, editable = true, updatable = true, order = -1)
    public String getObjectDefinitionText(DBRProgressMonitor monitor, Map<String, Object> options) {
        if (query == null) {
            currentDDLFormat = DamengDDLFormat.getCurrentFormat(getDataSource());
        }
        DamengDDLFormat newFormat = DamengDDLFormat.FULL;
        boolean isFormatInOptions = !CommonUtils.isEmpty(options)
            && options.containsKey(DamengConstants.PREF_KEY_DDL_FORMAT);
        if (isFormatInOptions) {
            newFormat = (DamengDDLFormat) options.get(DamengConstants.PREF_KEY_DDL_FORMAT);
        }
        if (query == null || currentDDLFormat != newFormat && isPersisted()) {
            try {
                if (query == null || !isFormatInOptions) {
                    query = DamengUtils.getDDL(monitor, getTableTypeName(), this, currentDDLFormat, options);
                } else {
                    query = DamengUtils.getDDL(monitor, getTableTypeName(), this, newFormat, options);
                    currentDDLFormat = newFormat;
                }
            } catch (DBException e) {
                String message = e.getMessage();
                if (message != null) {
                    message = message.replace("*/", "* /");
                }
                query = "/*\nError generating materialized view DDL:\n" + message + "\n*/";
                log.warn("Error getting view definition from system package", e);
            }
        }
        return query;
    }

    public void setObjectDefinitionText(String source) {
        this.query = source;
    }

    public String getMViewText() {
        return query;
    }

    public void setCurrentDDLFormat(DamengDDLFormat currentDDLFormat) {
        this.currentDDLFormat = currentDDLFormat;
    }

    private void loadAdditionalInfo(DBRProgressMonitor monitor) throws DBCException {
        if (!isPersisted()) {
            additionalInfo.loaded = true;
            return;
        }
        try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Load table status")) {
            // modify the sql
            try (JDBCPreparedStatement dbStat = session.prepareStatement(
                "select *,SF_VIEW_IS_UPDATABLE(?,MVIEW_NAME) as UPDATABLE from SYS.USER_MVIEWS;")) {
                dbStat.setString(1, this.username);
                try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                    if (dbResult.next()) {
                        additionalInfo.mviewValid = this.valid;
                        additionalInfo.updatable = JDBCUtils.safeGetBoolean(dbResult, "UPDATABLE",
                            DamengConstants.RESULT_BOOL_VALUE);
                        additionalInfo.rewriteEnabled = JDBCUtils.safeGetBoolean(dbResult, "REWRITE_ENABLED",
                            DamengConstants.RESULT_YES_VALUE);
                        additionalInfo.rewriteCapability = JDBCUtils.safeGetString(dbResult, "REWRITE_ENABLED");
                        additionalInfo.refreshMode = JDBCUtils.safeGetString(dbResult, "REFRESH_MODE");
                        additionalInfo.refreshMethod = JDBCUtils.safeGetString(dbResult, "REFRESH_METHOD");
                        additionalInfo.lastRefreshType = JDBCUtils.safeGetString(dbResult, "LAST_REFRESH_TYPE");
                        additionalInfo.lastRefreshDate = JDBCUtils.safeGetTimestamp(dbResult, "LAST_REFRESH_DATE");
                        additionalInfo.staleness = JDBCUtils.safeGetString(dbResult, "STALENESS");
                    }
                    additionalInfo.loaded = true;
                }
            } catch (SQLException e) {
                throw new DBCException(e, session.getExecutionContext());
            }
        }
    }

    @Override
    public DBEPersistAction[] getCompileActions(DBRProgressMonitor monitor) {
        return new DBEPersistAction[] {
            new DamengObjectPersistAction(DamengObjectType.MATERIALIZED_VIEW, "Compile materialized view",
                "ALTER MATERIALIZED VIEW " + getFullyQualifiedName(DBPEvaluationContext.DDL) + " COMPILE")};
    }

    @NotNull
    @Override
    public DBSObjectState getObjectState() {
        return valid ? DBSObjectState.NORMAL : DBSObjectState.INVALID;
    }

    @Override
    public void refreshObjectState(@NotNull DBRProgressMonitor monitor) throws DBCException {
        this.valid = DamengUtils.getObjectStatus(monitor, this, DamengObjectType.MATERIALIZED_VIEW);
    }

    @Override
    public Object getLazyReference(Object propertyId) {
        return additionalInfo.container;
    }

    @Override
    public boolean isView() {
        return true;
    }

    @Override
    public TableAdditionalInfo getAdditionalInfo() {
        return additionalInfo;
    }

    @Override
    protected String getTableTypeName() {
        return "MATERIALIZED_VIEW";
    }

    protected String queryTableComment(JDBCSession session) throws SQLException {
        return null;
    }

    @Override
    public DBSObject refreshObject(@NotNull DBRProgressMonitor monitor) throws DBException {
        getContainer().constraintCache.clearObjectCache(this);

        return getContainer().tableCache.refreshObject(monitor, getContainer(), this);
    }

    public static class AdditionalInfo extends TableAdditionalInfo {
        private volatile boolean loaded = false;

        @SuppressWarnings("unused")
        private boolean mviewValid;

        private Object container;

        private boolean updatable;

        private boolean rewriteEnabled;

        @SuppressWarnings("unused")
        private boolean valid;

        private String rewriteCapability;

        private String refreshMode;

        private String refreshMethod;

        private String buildMode;

        private String fastRefreshable;

        private String lastRefreshType;

        private Date lastRefreshDate;

        private String staleness;

        @Property(viewable = false, order = 14)
        public boolean isUpdatable() {
            return updatable;
        }

        @Property(viewable = false, order = 15)
        public boolean isRewriteEnabled() {
            return rewriteEnabled;
        }

        @Property(viewable = false, order = 16)
        public String getRewriteCapability() {
            return rewriteCapability;
        }

        @Property(viewable = false, order = 17)
        public String getRefreshMode() {
            return refreshMode;
        }

        @Property(viewable = false, order = 18)
        public String getRefreshMethod() {
            return refreshMethod;
        }

        @Property(viewable = false, order = 19)
        public String getBuildMode() {
            return buildMode;
        }

        @Property(viewable = false, order = 20)
        public String getFastRefreshable() {
            return fastRefreshable;
        }

        @Property(viewable = false, order = 21)
        public String getLastRefreshType() {
            return lastRefreshType;
        }

        @Property(viewable = false, order = 22)
        public Date getLastRefreshDate() {
            return lastRefreshDate;
        }

        @Property(viewable = false, order = 23)
        public String getStaleness() {
            return staleness;
        }

    }

}
