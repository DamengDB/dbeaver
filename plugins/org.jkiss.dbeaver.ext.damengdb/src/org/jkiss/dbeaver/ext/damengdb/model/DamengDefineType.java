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
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.damengdb.model.source.DamengSourceObject;
import org.jkiss.dbeaver.model.DBPDataKind;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBPQualifiedObject;
import org.jkiss.dbeaver.model.DBPScriptObjectExt;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCLogicalOperator;
import org.jkiss.dbeaver.model.impl.DBObjectNameCaseTransformer;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSDataType;
import org.jkiss.dbeaver.model.struct.DBSEntity;
import org.jkiss.dbeaver.model.struct.DBSEntityAssociation;
import org.jkiss.dbeaver.model.struct.DBSEntityAttribute;
import org.jkiss.dbeaver.model.struct.DBSEntityConstraint;
import org.jkiss.dbeaver.model.struct.DBSEntityType;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectState;
import org.jkiss.dbeaver.model.struct.DBSTypedObject;
import org.jkiss.utils.CommonUtils;

/**
 * Dameng data type
 */
public class DamengDefineType extends DamengObject<DBSObject>
    implements DBSDataType, DBSEntity, DBPQualifiedObject, DamengSourceObject, DBPScriptObjectExt {
    
	private String typeCode;

    private boolean flagHeadEncrypted;

    private boolean flagBodyEncrypted;

    private int valueType = java.sql.Types.OTHER;

    private String authIDOption;

    private String sourceDeclaration;

    private String sourceDefinition;

    private Date created;

    public DamengDefineType(DBSObject owner, ResultSet dbResult) {
        super(owner, JDBCUtils.safeGetString(dbResult, "NAME"), true);
        this.typeCode = JDBCUtils.safeGetString(dbResult, "SUBTYPE$");
        int info1 = JDBCUtils.safeGetInt(dbResult, "INFO1");
        this.flagHeadEncrypted = (info1 & 0x01) != 0;
        this.flagBodyEncrypted = (info1 & 0x02) != 0;
        this.authIDOption = ((info1 >> 5) & 0x01) != 0 ? DamengConstants.AUTHID_OPTION_CURRENT_USER
            : DamengConstants.AUTHID_OPTION_DEFINER;
        this.created = JDBCUtils.safeGetTimestamp(dbResult, DamengConstants.CREATED);
    }

    @SuppressWarnings("unused")
    private static String normalizeTypeName(String typeName) {
        if (CommonUtils.isEmpty(typeName)) {
            return "";
        }
        for (; ; ) {
            int modIndex = typeName.indexOf('(');
            if (modIndex == -1) {
                break;
            }
            int modEnd = typeName.indexOf(')', modIndex);
            if (modEnd == -1) {
                break;
            }
            typeName = typeName.substring(0, modIndex)
                + (modEnd == typeName.length() - 1 ? "" : typeName.substring(modEnd + 1));
        }
        return typeName;
    }

    @Nullable
    @Override
    public DamengSchema getSchema() {
        return parent instanceof DamengSchema ? (DamengSchema) parent : null;
    }

    @Override
    public DamengSourceType getSourceType() {
        return typeCode.equalsIgnoreCase("CLASS") ? DamengSourceType.CLASS : DamengSourceType.TYPE;
    }

    @Override
    @Property(hidden = true, editable = true, updatable = true, order = -1)
    public String getObjectDefinitionText(DBRProgressMonitor monitor, Map<String, Object> options) throws DBCException {
        if (sourceDeclaration == null && monitor != null) {
            sourceDeclaration = DamengUtils.getSource(monitor, this, false, true);
        }
        return sourceDeclaration;
    }

    public void setObjectDefinitionText(String sourceDeclaration) {
        this.sourceDeclaration = sourceDeclaration;
    }

    @Override
    public DBEPersistAction[] getCompileActions(DBRProgressMonitor monitor) throws DBCException {
        return new DBEPersistAction[] {new DamengObjectPersistAction(DamengObjectType.VIEW, "Compile type",
            "ALTER TYPE " + getFullyQualifiedName(DBPEvaluationContext.DDL) + " COMPILE")};
    }

    @Override
    @Property(hidden = true, editable = true, updatable = true, order = -1)
    public String getExtendedDefinitionText(DBRProgressMonitor monitor) throws DBException {
        if (sourceDefinition == null && monitor != null) {
            sourceDefinition = DamengUtils.getSource(monitor, this, true, false);
        }
        return sourceDefinition;
    }

    public void setExtendedDefinitionText(String source) {
        this.sourceDefinition = source;
    }

    @Override
    public String getTypeName() {
        return getFullyQualifiedName(DBPEvaluationContext.DDL);
    }

    @Override
    public String getFullTypeName() {
        return DBUtils.getFullTypeName(this);
    }

    @Override
    public int getTypeID() {
        return valueType;
    }

    @Override
    public DBPDataKind getDataKind() {
        return JDBCUtils.resolveDataKind(getDataSource(), getName(), valueType);
    }

    @Override
    public long getMaxLength() {
        return CommonUtils.toInt(getPrecision());
    }

    @Override
    public long getTypeModifiers() {
        return 0;
    }

    @NotNull
    @Override
    public DBCLogicalOperator[] getSupportedOperators(DBSTypedObject attribute) {
        return DBUtils.getDefaultOperators(this);
    }

    @Override
    public DBSObject getParentObject() {
        return parent instanceof DamengSchema ? parent
            : parent instanceof DamengDataSource ? ((DamengDataSource) parent).getContainer() : null;
    }

    @NotNull
    @Override
    @Property(viewable = true, editable = true, valueTransformer = DBObjectNameCaseTransformer.class, order = 1)
    public String getName() {
        return name;
    }

    @Property(viewable = true, editable = true, order = 2)
    public String getTypeCode() {
        return typeCode;
    }

    @Property(viewable = true, order = 3)
    public boolean isHeadEncrypted() {
        return flagHeadEncrypted;
    }

    @Property(viewable = true, order = 4)
    public boolean isBodyEncrypted() {
        return flagBodyEncrypted;
    }

    @Property(viewable = true, order = 6)
    public String getAuthIDOption() {
        return authIDOption;
    }

    @Property(viewable = true, order = 7)
    public Date getCreated() {
        return created;
    }

    @NotNull
    @Override
    public DBSEntityType getEntityType() {
        return DBSEntityType.TYPE;
    }

    @Nullable
    @Override
    public Collection<? extends DBSEntityConstraint> getConstraints(@NotNull DBRProgressMonitor monitor)
        throws DBException {
        return null;
    }

    @Override
    public Collection<? extends DBSEntityAssociation> getAssociations(@NotNull DBRProgressMonitor monitor)
        throws DBException {
        return null;
    }

    @Override
    public Collection<? extends DBSEntityAssociation> getReferences(@NotNull DBRProgressMonitor monitor)
        throws DBException {
        return null;
    }

    @NotNull
    @Override
    public String getFullyQualifiedName(DBPEvaluationContext context) {
        return parent instanceof DamengSchema ? DBUtils.getFullQualifiedName(getDataSource(), parent, this) : name;
    }

    @Override
    public String toString() {
        return getFullyQualifiedName(DBPEvaluationContext.UI);
    }

    @NotNull
    @Override
    public DBSObjectState getObjectState() {
        return DBSObjectState.NORMAL;
    }

    @Override
    public void refreshObjectState(@NotNull DBRProgressMonitor monitor) throws DBCException {

    }

    @Override
    public Integer getScale() {
        return null;
    }

    @Override
    public Integer getPrecision() {
        return null;
    }

    @Override
    public int getMinScale() {
        return 0;
    }

    @Override
    public int getMaxScale() {
        return 0;
    }

    @Override
    public @Nullable List<? extends DBSEntityAttribute> getAttributes(@NotNull DBRProgressMonitor monitor)
        throws DBException {
        return null;
    }

    @Override
    public @Nullable DBSEntityAttribute getAttribute(@NotNull DBRProgressMonitor monitor, @NotNull String attributeName)
        throws DBException {
        return null;
    }

    @Override
    public @Nullable DBSDataType getComponentType(@NotNull DBRProgressMonitor monitor) throws DBException {
        return null;
    }

    @Override
    public @Nullable Object geTypeExtension() {
        return null;
    }

}
