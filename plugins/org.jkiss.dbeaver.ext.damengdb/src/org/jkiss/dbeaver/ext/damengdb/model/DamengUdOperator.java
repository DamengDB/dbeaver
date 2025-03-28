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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.damengdb.model.source.DamengSourceObject;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObjectState;

import com.dameng.common.util.StringUtil;

/**
 * UdOperator
 */
public class DamengUdOperator extends DamengSchemaObject implements DamengSourceObject {
	
    private static final Log log = Log.getLog(DamengUdOperator.class);

    private String udOpName;

    private String schemaName;

    private int version;

    private int overrideCount;

    private Date createdDate;

    private boolean valid;

    private String opDefinationText;

    private List<DamengUdOperatorOverride> udOperatorOverrideList = new ArrayList<>();

    public DamengUdOperator(DamengSchema schema, ResultSet dbResult) {
        super(schema, JDBCUtils.safeGetString(dbResult, "OP_NAME"), true);
        this.schemaName = JDBCUtils.safeGetString(dbResult, "SCH_NAME");
        this.version = JDBCUtils.safeGetInt(dbResult, "OP_VERSION");// version
        this.createdDate = JDBCUtils.safeGetTimestamp(dbResult, "OP_CRTDATE");
        this.valid = JDBCUtils.safeGetString(dbResult, "OP_VALID").equals("Y");
        this.overrideCount = 1;
    }

    public String getUdOpName() {
        return udOpName;
    }

    @Property(viewable = true, editable = false, order = 2)
    public String getSchemaName() {
        return schemaName;
    }

    @Property(viewable = true, editable = false, order = 3)
    public int getVersion() {
        return version;
    }

    @Property(viewable = true, editable = false, order = 4)
    public int getOverrideCount() {
        return overrideCount;
    }

    public void setOverrideCount(int overrideCount) {
        this.overrideCount = overrideCount;
    }

    @Property(viewable = true, editable = false, order = 5)
    public boolean isValid() {
        return valid;
    }

    public List<DamengUdOperatorOverride> getUdOperatorOverrideList() {
        return udOperatorOverrideList;
    }

    @Property(viewable = true, editable = false, order = 6)
    public Date getCreatedDate() {
        return createdDate;
    }

    public String getFullName() {
        StringBuilder fullName = new StringBuilder();

        fullName.append("\"" + this.schemaName + "\"");
        fullName.append(".");
        fullName.append("\"" + getName() + "\"");

        return fullName.toString();
    }

    @Override
    public void setObjectDefinitionText(String source) {
    }

    @Override
    public String getObjectDefinitionText(DBRProgressMonitor monitor, Map<String, Object> options) throws DBException {
        if (opDefinationText == null) {
            StringBuffer definationText = new StringBuffer();
            definationText.append("--");
            if (this.getSchemaName() != null) {
                definationText.append("\"" + this.getSchemaName() + "\"");
                definationText.append(".");
            }
            definationText.append("\"" + getName() + "\"");
            definationText.append(DamengConstants.LINE_SEPARATOR + DamengConstants.LINE_SEPARATOR);

            for (DamengUdOperatorOverride override : this.getUdOperatorOverrideList()) {
                // Deal with the Left Argument and Right Argument
                if (override.getLeftArgType().startsWith("CLASS") || override.getRightArgType().startsWith("CLASS")) {
                    override = resetUDOperatorOverrideArgType(monitor, override);
                }

                definationText.append("CREATE OPERATOR ");
                definationText.append(this.getFullName());

                // 函数
                definationText.append(" (FUNCTION ");
                if (StringUtil.isNotEmpty(override.getFunctionSchemaName())) {
                    definationText.append("\"");
                    definationText.append(override.getFunctionSchemaName());
                    definationText.append("\".");
                }
                definationText.append("\"");
                definationText.append(override.getFunctionName());
                definationText.append("\"");

                // Left Argument
                if (StringUtil.isEmpty(override.getLeftArgType())) {
                    definationText.append(", LEFTARG NULL");
                } else {
                    definationText.append(", LEFTARG ").append(override.getLeftArgType());
                }

                // Right Argument
                if (StringUtil.isEmpty(override.getRightArgType())) {
                    definationText.append(", RIGHTARG NULL");
                } else {
                    definationText.append(", RIGHTARG ").append(override.getRightArgType());
                }

                definationText.append(");");
                definationText.append(DamengConstants.LINE_SEPARATOR);
                definationText.append(DamengConstants.LINE_SEPARATOR);
            }

            opDefinationText = definationText.toString();
        }
        return opDefinationText;

    }

    private DamengUdOperatorOverride resetUDOperatorOverrideArgType(DBRProgressMonitor monitor,
                                                                    DamengUdOperatorOverride udOperatorOverride) {
        try (JDBCSession session = DBUtils.openUtilSession(monitor, this, "Get DDL of '" + getName() + "'")) {

            StringBuilder sql = new StringBuilder();

            sql.append("select id, name from sysobjects");

            boolean flag = false;

            sql.append(" where id in (");
            if (udOperatorOverride.getLeftArgType().startsWith("CLASS")) {
                flag = true;
                sql.append(udOperatorOverride.getLeftArgType().substring("CLASS".length()));
            }
            if (udOperatorOverride.getRightArgType().startsWith("CLASS")) {
                if (flag) {
                    sql.append(", ");
                }
                sql.append(udOperatorOverride.getRightArgType().substring("CLASS".length()));
            }
            sql.append(")");
            JDBCPreparedStatement dbStat = session.prepareStatement(sql.toString());

            JDBCResultSet dbResult = dbStat.executeQuery();
            while (dbResult.next()) {

                int id = dbResult.getInt(1);
                String name = dbResult.getString(2);

                if (udOperatorOverride.getLeftArgType().contains(String.valueOf(id))) {
                    udOperatorOverride.setLeftArgType(name);
                }
                if (udOperatorOverride.getRightArgType().contains(String.valueOf(id))) {
                    udOperatorOverride.setLeftArgType(name);
                }

            }
            dbStat.close();
        } catch (Exception e) {
            log.debug("error occurred :" + e + "");
        }
        return udOperatorOverride;

    }

    @Override
    public @NotNull DBSObjectState getObjectState() {
        return valid ? DBSObjectState.NORMAL : DBSObjectState.INVALID;
    }

    @Override
    public void refreshObjectState(@NotNull DBRProgressMonitor monitor) throws DBCException {

        this.valid = DamengUtils.getObjectStatus(monitor, this, DamengObjectType.UD_OPERATOR);
    }

    @Override
    public DamengSourceType getSourceType() {
        return DamengSourceType.UD_OPERATOR;
    }

    @Override
    public DBEPersistAction[] getCompileActions(DBRProgressMonitor monitor) throws DBCException {
        return null;
    }

    /**
     * Get the overload info of the Udoperator
     */
    @Association
    public Collection<DamengUdOperatorOverride> getUdoperatorsoverrideOnly(DBRProgressMonitor monitor)
        throws DBException {
        return this.getSchema().getUdoperator(monitor, this.getName()).getUdOperatorOverrideList().stream()
            .collect(Collectors.toList());
    }

    /**
     * Get the dependencies
     */
    @Association
    public Collection<DamengDependencyGroup> getDependencies(DBRProgressMonitor monitor) {
        return DamengDependencyGroup.of(this);
    }
    
}
