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
import org.jkiss.dbeaver.model.DBPDataKind;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSTypedObject;
import org.jkiss.dbeaver.model.struct.rdb.DBSProcedureParameter;
import org.jkiss.dbeaver.model.struct.rdb.DBSProcedureParameterKind;
import org.jkiss.utils.CommonUtils;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * DamengProcedureArgument
 */
public class DamengProcedureArgument implements DBSProcedureParameter, DBSTypedObject {
    
	private final DamengProcedureBase<?> procedure;

    private String name;

    private int position;

    private int dataLevel;

    private int sequence;

    private DamengParameterMode mode;

    private DamengDataType type;

    private DamengDataType dataType;

    private String packageTypeName;

    private int dataLength;

    private int dataScale;

    private int dataPrecision;

    private List<DamengProcedureArgument> attributes;

    public DamengProcedureArgument(DBRProgressMonitor monitor, DamengProcedureBase<?> procedure, ResultSet dbResult) {
        this.procedure = procedure;
        this.name = JDBCUtils.safeGetString(dbResult, "ARGUMENT_NAME");
        this.position = JDBCUtils.safeGetInt(dbResult, "POSITION");
        this.dataLevel = JDBCUtils.safeGetInt(dbResult, "DATA_LEVEL");
        this.sequence = JDBCUtils.safeGetInt(dbResult, "SEQUENCE");

        // If the position is 0 and name is "V_RET"，modify the name to "RETURN"
        if ((this.name).equals("V_RET") && this.position == 0) {
            this.name = "RETURN";
        }
        // If the position is 0, modify the mode to "RETURN"
        if (CommonUtils.isEmpty(this.name) || this.position == 0) {
            this.mode = DamengParameterMode.RETURN;
        } else {
            this.mode = DamengParameterMode.getMode(JDBCUtils.safeGetString(dbResult, "IN_OUT"));
        }
        final String dataType = JDBCUtils.safeGetString(dbResult, "DATA_TYPE");
        this.type = CommonUtils.isEmpty(dataType) ? null
            : DamengDataType.resolveDataType(monitor, procedure.getDataSource(), null, dataType);
        final String typeName = JDBCUtils.safeGetString(dbResult, "TYPE_NAME");
        final String typeOwner = JDBCUtils.safeGetString(dbResult, "TYPE_OWNER");
        this.packageTypeName = JDBCUtils.safeGetString(dbResult, "TYPE_SUBNAME");
        if (!CommonUtils.isEmpty(typeName) && !CommonUtils.isEmpty(typeOwner)
            && CommonUtils.isEmpty(this.packageTypeName)) {
            this.dataType = DamengDataType.resolveDataType(monitor, procedure.getDataSource(), typeOwner, typeName);
            if (this.dataType == null) {
                this.packageTypeName = typeOwner + "." + typeName;
            }
        } else if (this.packageTypeName != null) {
            packageTypeName = typeName + "." + packageTypeName;
        }
        this.dataLength = JDBCUtils.safeGetInt(dbResult, "DATA_LENGTH");
        this.dataScale = JDBCUtils.safeGetInt(dbResult, "DATA_SCALE");
        this.dataPrecision = JDBCUtils.safeGetInt(dbResult, "DATA_PRECISION");
    }

    @Nullable
    @Override
    public String getDescription() {
        return null;
    }

    @NotNull
    @Override
    public DamengDataSource getDataSource() {
        return procedure.getDataSource();
    }

    @Override
    public DamengProcedureBase<?> getParentObject() {
        return procedure;
    }

    @Override
    public boolean isPersisted() {
        return true;
    }

    @NotNull
    @Override
    @Property(viewable = true, order = 10)
    public String getName() {
        if (CommonUtils.isEmpty(name)) {
            if (dataLevel == 0) {
                // Function result
                return "RESULT";
            } else {
                // Collection element
                return "ELEMENT";
            }
        }
        return name;
    }

    public boolean isResultArgument() {
        return CommonUtils.isEmpty(name) && dataLevel == 0;
    }

    @Property(viewable = true, order = 11)
    public int getPosition() {
        return position;
    }

    @NotNull
    @Override
    @Property(viewable = true, order = 20)
    public DBSProcedureParameterKind getParameterKind() {
        return mode == null ? DBSProcedureParameterKind.UNKNOWN : mode.getParameterKind();
    }

    @Property(viewable = true, order = 21)
    public Object getType() {
        return packageTypeName != null ? packageTypeName : dataType == null ? type : dataType;
    }

    @Override
    @Property(viewable = true, order = 30)
    public long getMaxLength() {
        return dataLength;
    }

    @Override
    public long getTypeModifiers() {
        return 0;
    }

    @Override
    public String getTypeName() {
        return type == null ? packageTypeName : type.getName();
    }

    @Override
    public String getFullTypeName() {
        return DBUtils.getFullTypeName(this);
    }

    @Override
    public int getTypeID() {
        return type == null ? 0 : type.getTypeID();
    }

    @Override
    public DBPDataKind getDataKind() {
        return type == null ? DBPDataKind.OBJECT : type.getDataKind();
    }

    @Override
    @Property(viewable = true, order = 40)
    public Integer getScale() {
        return dataScale;
    }

    @Override
    @Property(viewable = true, order = 50)
    public Integer getPrecision() {
        return dataPrecision;
    }

    public int getDataLevel() {
        return dataLevel;
    }

    public int getSequence() {
        return sequence;
    }

    @Association
    public List<DamengProcedureArgument> getAttributes() {
        return attributes;
    }

    void addAttribute(DamengProcedureArgument attribute) {
        if (attributes == null) {
            attributes = new ArrayList<>();
        }
        attributes.add(attribute);
    }

    public boolean hasAttributes() {
        return !CommonUtils.isEmpty(attributes);
    }

    @NotNull
    @Override
    public DBSTypedObject getParameterType() {
        return this;
    }
    
}
