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

import java.util.Date;
import java.util.Map;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.damengdb.model.source.DamengSourceObject;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObjectState;
import com.dameng.common.util.StringUtil;

/**
 * UdOperator parameter info
 */
public class DamengUdOperatorOverride extends DamengSchemaObject implements DamengSourceObject {

    private static final Log log = Log.getLog(DamengUdOperatorOverride.class);

    private DamengUdOperator parent;

    private String schemaName;

    // Left Argument
    private String leftArgType;

    // Right Argument
    private String rightArgType;

    private Date createdDate;

    private boolean valid;

    // ID
    private int argId;

    private String functionSchemaName;

    private String functionName;

    // Whether is the system operator
    private int type;

    private boolean isSysOp;

    private int charset;

    private String overrideDefinationText;

    public DamengUdOperatorOverride(JDBCSession session, DamengSchema schema, ResultSet dbResult,
                                    DamengUdOperator parent, String leftArgType, String rightArgType) {
        super(schema, getFullName(dbResult, leftArgType, rightArgType), true);

        this.parent = parent;
        this.createdDate = JDBCUtils.safeGetTimestamp(dbResult, "ARG_CRTDATE");
        // get the left and right full name(eg:SYSGEO2)
        this.leftArgType = new DamengTableColumn().getClassName(session,
            JDBCUtils.safeGetString(dbResult, "LEFTARG_TYPE"), true);
        this.rightArgType = new DamengTableColumn().getClassName(session,
            JDBCUtils.safeGetString(dbResult, "RIGHTARG_TYPE"), true);

        int info2 = JDBCUtils.safeGetInt(dbResult, "INFO2");
        this.type = (info2 & 0x01) == 0 ? DamengConstants.OBJTYPE_USER : DamengConstants.OBJTYPE_SYSTEM;

        this.isSysOp = type == DamengConstants.OBJTYPE_SYSTEM ? true : false;
        this.charset = JDBCUtils.safeGetInt(dbResult, "CHARSET");
        this.argId = JDBCUtils.safeGetInt(dbResult, "ARG_ID");
        this.valid = JDBCUtils.safeGetString(dbResult, "ARG_VALID").equals("Y");
        this.schemaName = JDBCUtils.safeGetString(dbResult, "SCH_NAME");

        // Get the Function information by "INFO1"
        initFunctionInfo(JDBCUtils.safeGetBytes(dbResult, "INFO1"));

    }

    /**
     * Format：ARG_ID[LEFTARGTYPE,RIGHTARGTYPE]
     */
    private static String getFullName(ResultSet dbResult, String leftArgType, String rightArgType) {
        StringBuffer fullName = new StringBuffer();

        fullName.append(JDBCUtils.safeGetString(dbResult, "ARG_ID"));
        fullName.append(" [");
        fullName.append(leftArgType);
        fullName.append(", ");
        fullName.append(rightArgType);
        fullName.append("]");
        return fullName.toString();
    }

    /**
     * Get the schema name and function name of the Function
     */
    public void initFunctionInfo(byte[] info1) {
        try {
            int offset = 2;
            int length = info1[0];
            String value = new String(info1, offset, length, DamengConstants.CHARSET_NAME_ARR_JAVA[charset]);
            if (2 + length == info1.length) {
                this.functionSchemaName = "";
                this.functionName = value;
            } else {
                this.functionSchemaName = value;

                offset = 2 + 2 + length;
                length = info1[2 + length];
                value = new String(info1, offset, length, DamengConstants.CHARSET_NAME_ARR_JAVA[charset]);
                this.functionName = value;
            }
        } catch (Exception e) {
            log.debug("Decode operator fail!", e);
        }
    }

    public DamengUdOperator getParent() {
        return parent;
    }

    @Property(viewable = true, editable = false, order = 2)
    public int getArgId() {
        return argId;
    }

    @Property(viewable = true, editable = false, order = 3)
    public String getSchemaName() {
        return schemaName;
    }

    @Property(viewable = true, editable = false, order = 4)
    public String getParentName() {
        return parent.getName();
    }

    @Property(viewable = true, editable = false, order = 5)
    public boolean getIsSysOp() {
        return isSysOp;
    }

    @Property(viewable = true, editable = false, order = 6)
    public String getFunctionSchemaName() {
        return functionSchemaName;
    }

    @Property(viewable = true, editable = false, order = 7)
    public String getFunctionName() {
        return functionName;
    }

    @Property(viewable = true, editable = false, order = 8)
    public String getLeftArgType() {
        return leftArgType;
    }

    protected void setLeftArgType(String leftArgType) {
        this.leftArgType = leftArgType;
    }

    @Property(viewable = true, editable = false, order = 9)
    public String getRightArgType() {
        return rightArgType;
    }

    @Property(viewable = true, editable = false, order = 10)
    public boolean isValid() {
        return valid;
    }

    @Property(viewable = true, editable = false, order = 11)
    public Date getCreatedDate() {
        return createdDate;
    }

    public int getType() {
        return type;
    }

    public int getCharset() {
        return charset;
    }

    /**
     * Cound't modify the definition
     */
    @Override
    public void setObjectDefinitionText(String source) {
    }

    @Override
    public String getObjectDefinitionText(DBRProgressMonitor monitor, Map<String, Object> options) throws DBException {
        if (overrideDefinationText == null) {
            StringBuffer definationText = new StringBuffer();

            DamengUdOperator udOperator = this.parent;
            definationText.append("CREATE OPERATOR ");
            definationText.append(udOperator.getFullName());

            // Function
            definationText.append(" (FUNCTION ");
            if (StringUtil.isNotEmpty(this.getFunctionSchemaName())) {
                definationText.append("\"");
                definationText.append(this.getFunctionSchemaName());
                definationText.append("\".");
            }
            definationText.append("\"");
            definationText.append(this.getFunctionName());
            definationText.append("\"");

            // Left Argument
            if (StringUtil.isEmpty(this.getLeftArgType())) {
                definationText.append(", LEFTARG NULL");
            } else {
                definationText.append(", LEFTARG ").append(this.getLeftArgType());
            }

            // Right Argument
            if (StringUtil.isEmpty(this.getRightArgType())) {
                definationText.append(", RIGHTARG NULL");
            } else {
                definationText.append(", RIGHTARG ").append(this.getRightArgType());
            }

            definationText.append(");");
            definationText.append(DamengConstants.LINE_SEPARATOR);
            definationText.append(DamengConstants.LINE_SEPARATOR);

            overrideDefinationText = definationText.toString();
        }
        return overrideDefinationText;

    }

    @Override
    public @NotNull DBSObjectState getObjectState() {
        return valid ? DBSObjectState.NORMAL : DBSObjectState.INVALID;
    }

    @Override
    public void refreshObjectState(@NotNull DBRProgressMonitor monitor) throws DBCException {
    }

    @Override
    public DamengSourceType getSourceType() {
        return DamengSourceType.UD_OPERATOR;
    }

    @Override
    public DBEPersistAction[] getCompileActions(DBRProgressMonitor monitor) throws DBCException {
        return null;
    }

}
