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
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.damengdb.model.source.DamengSourceObject;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCLogicalOperator;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCStatement;
import org.jkiss.dbeaver.model.impl.DBObjectNameCaseTransformer;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCObjectCache;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.*;
import org.jkiss.utils.CommonUtils;

import dm.Parser;
import dm.dm_lst_node_t;
import dm.dm_lst_t;
import dm.npar.npar_cpkg_def_proc_t;
import dm.npar.npar_cursor_decl_t;
import dm.npar.npar_decl_stmt_t;
import dm.npar.npar_decl_var_t;
import dm.npar.npar_root_t;
import dm.npar.npar_stmt_t;
import dm.npar.npar_param_def_t;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dameng data type
 */
public class DamengDataType extends DamengObject<DBSObject>
    implements DBSDataType, DBSEntity, DBPQualifiedObject, DamengSourceObject, DBPScriptObjectExt {

    public static final String TYPE_CODE_COLLECTION = "COLLECTION";
    public static final String TYPE_CODE_OBJECT = "OBJECT";
    static final Map<String, TypeDesc> PREDEFINED_TYPES = new HashMap<>();
    static final Map<Integer, TypeDesc> PREDEFINED_TYPE_IDS = new HashMap<>();
    private static final Log log = Log.getLog(DamengDataType.class);

    static {
        PREDEFINED_TYPES.put("BFILE", new TypeDesc(DBPDataKind.CONTENT, Types.OTHER, 0, 0, 0));
        PREDEFINED_TYPES.put("BINARY_DOUBLE", new TypeDesc(DBPDataKind.NUMERIC, Types.DOUBLE, 38, 127, -84));
        PREDEFINED_TYPES.put("BINARY_FLOAT", new TypeDesc(DBPDataKind.NUMERIC, Types.FLOAT, 38, 127, -84));
        PREDEFINED_TYPES.put("BLOB", new TypeDesc(DBPDataKind.CONTENT, Types.BLOB, 0, 0, 0));
        PREDEFINED_TYPES.put("CHAR", new TypeDesc(DBPDataKind.STRING, Types.CHAR, 0, 0, 0));
        PREDEFINED_TYPES.put("BOOLEAN", new TypeDesc(DBPDataKind.BOOLEAN, Types.BOOLEAN, 0, 0, 0));

        // ADD NEW TYPE
        PREDEFINED_TYPES.put("SYSGEO2.ST_GEOMETRY", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO2.ST_GEOGRAPHY", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO2.ST_POINT", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO2.ST_LINESTRING", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO2.ST_POLYGON", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO2.ST_MULTIPOINT", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO2.ST_MULTILINESTRING", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO2.ST_MULTIPOLYGON", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO2.ST_GEOMETRYCOLLECTION", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO2.ST_CIRCULARSTRING", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO2.ST_COMPOUNDCURVE", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO2.ST_CURVEPOLYGON", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO2.ST_MULTICURVE", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO2.ST_MULTISURFACE", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO2.ST_POLYHEDRALSURFACE", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO2.ST_TRIANGLE", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO2.ST_TIN", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO2.ST_BOX2D", new TypeDesc(DBPDataKind.STRUCT, Types.OTHER, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO2.ST_BOX3D", new TypeDesc(DBPDataKind.STRUCT, Types.OTHER, 0, 0, 0));

        PREDEFINED_TYPES.put("SYSGEO.ST_GEOMETRY", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO.ST_POINT", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO.ST_LINESTRING", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO.ST_POLYGON", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO.ST_MULTIPOINT", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO.ST_MULTILINESTRING", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO.ST_MULTIPOLYGON", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO.ST_GEOMCOLLECTION", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO.ST_CURVE", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO.ST_MULTICURVE", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO.ST_SURFACE", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSGEO.ST_MULTISURFACE", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));

        PREDEFINED_TYPES.put("SYSRASTER.ST_RASTER", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("SYSTOPOLOGY.TOPOGEOMETRY", new TypeDesc(DBPDataKind.STRUCT, Types.STRUCT, 0, 0, 0));
        PREDEFINED_TYPES.put("CHARACTER VARYING", new TypeDesc(DBPDataKind.STRING, Types.CHAR, 0, 0, 0));

        PREDEFINED_TYPES.put("CLOB", new TypeDesc(DBPDataKind.CONTENT, Types.CLOB, 0, 0, 0));
        PREDEFINED_TYPES.put("TEXT", new TypeDesc(DBPDataKind.CONTENT, Types.CLOB, 0, 0, 0));
        PREDEFINED_TYPES.put("CONTIGUOUS ARRAY", new TypeDesc(DBPDataKind.ARRAY, Types.ARRAY, 0, 0, 0));
        PREDEFINED_TYPES.put("DATE", new TypeDesc(DBPDataKind.DATETIME, Types.TIMESTAMP, 0, 0, 0));
        PREDEFINED_TYPES.put("NUMERIC", new TypeDesc(DBPDataKind.NUMERIC, Types.NUMERIC, 38, 127, -84));
        PREDEFINED_TYPES.put("DECIMAL", new TypeDesc(DBPDataKind.NUMERIC, Types.DECIMAL, 38, 127, -84));
        PREDEFINED_TYPES.put("DEC", new TypeDesc(DBPDataKind.NUMERIC, Types.DECIMAL, 38, 127, -84));
        PREDEFINED_TYPES.put("DOUBLE PRECISION", new TypeDesc(DBPDataKind.NUMERIC, Types.DOUBLE, 38, 127, -84));
        PREDEFINED_TYPES.put("FLOAT", new TypeDesc(DBPDataKind.NUMERIC, Types.FLOAT, 38, 127, -84));
        PREDEFINED_TYPES.put("DOUBLE", new TypeDesc(DBPDataKind.NUMERIC, Types.DOUBLE, 38, 127, -84));
        PREDEFINED_TYPES.put("INTEGER", new TypeDesc(DBPDataKind.NUMERIC, Types.INTEGER, 38, 127, -84));
        PREDEFINED_TYPES.put("INT", new TypeDesc(DBPDataKind.NUMERIC, Types.INTEGER, 38, 127, -84));
        PREDEFINED_TYPES.put("BIGINT", new TypeDesc(DBPDataKind.NUMERIC, Types.BIGINT, 19, 127, -84));
        PREDEFINED_TYPES.put("TINYINT", new TypeDesc(DBPDataKind.NUMERIC, Types.TINYINT, 3, 127, -84));
        PREDEFINED_TYPES.put("BYTE", new TypeDesc(DBPDataKind.NUMERIC, Types.TINYINT, 3, 127, -84));
        PREDEFINED_TYPES.put("SMALLINT", new TypeDesc(DBPDataKind.NUMERIC, Types.SMALLINT, 5, 127, -84));
        PREDEFINED_TYPES.put("INTERVAL DAY TO SECOND", new TypeDesc(DBPDataKind.STRING, Types.VARCHAR, 0, 0, 0));
        PREDEFINED_TYPES.put("INTERVAL YEAR TO MONTH", new TypeDesc(DBPDataKind.STRING, Types.VARCHAR, 0, 0, 0));
        PREDEFINED_TYPES.put("NUMBER", new TypeDesc(DBPDataKind.NUMERIC, Types.NUMERIC, 38, 127, -84));
        PREDEFINED_TYPES.put("REAL", new TypeDesc(DBPDataKind.NUMERIC, Types.REAL, 38, 127, -84));
        PREDEFINED_TYPES.put("SMALLINT", new TypeDesc(DBPDataKind.NUMERIC, Types.SMALLINT, 38, 127, -84));
        PREDEFINED_TYPES.put("TABLE", new TypeDesc(DBPDataKind.OBJECT, Types.OTHER, 0, 0, 0));
        PREDEFINED_TYPES.put("TIME", new TypeDesc(DBPDataKind.DATETIME, Types.TIMESTAMP, 0, 0, 0));
        PREDEFINED_TYPES.put("DATETIME", new TypeDesc(DBPDataKind.DATETIME, Types.TIMESTAMP, 0, 0, 0));
        PREDEFINED_TYPES.put("TIME WITH TZ", new TypeDesc(DBPDataKind.DATETIME, Types.TIMESTAMP, 0, 0, 0));
        PREDEFINED_TYPES.put("TIMESTAMP", new TypeDesc(DBPDataKind.DATETIME, Types.TIMESTAMP, 0, 0, 0));
        PREDEFINED_TYPES.put("TIMESTAMP WITH LOCAL TZ",
            new TypeDesc(DBPDataKind.DATETIME, DamengConstants.DATA_TYPE_TIMESTAMP_WITH_LOCAL_TIMEZONE, 0, 0, 0));
        PREDEFINED_TYPES.put("TIMESTAMP WITH TZ",
            new TypeDesc(DBPDataKind.DATETIME, DamengConstants.DATA_TYPE_TIMESTAMP_WITH_TIMEZONE, 0, 0, 0));
        PREDEFINED_TYPES.put("TIMESTAMP WITH LOCAL TIME ZONE",
            new TypeDesc(DBPDataKind.DATETIME, DamengConstants.DATA_TYPE_TIMESTAMP_WITH_LOCAL_TIMEZONE, 0, 0, 0));
        PREDEFINED_TYPES.put("TIMESTAMP WITH TIME ZONE",
            new TypeDesc(DBPDataKind.DATETIME, DamengConstants.DATA_TYPE_TIMESTAMP_WITH_TIMEZONE, 0, 0, 0));
        PREDEFINED_TYPES.put("DATETIME WITH TIME ZONE",
            new TypeDesc(DBPDataKind.DATETIME, DamengConstants.DATA_TYPE_TIMESTAMP_WITH_TIMEZONE, 0, 0, 0));
        PREDEFINED_TYPES.put("VARCHAR", new TypeDesc(DBPDataKind.STRING, Types.VARCHAR, 0, 0, 0));
        PREDEFINED_TYPES.put("VARCHAR2", new TypeDesc(DBPDataKind.STRING, Types.VARCHAR, 0, 0, 0));
        PREDEFINED_TYPES.put("CHARACTER", new TypeDesc(DBPDataKind.STRING, Types.VARCHAR, 0, 0, 0));
        PREDEFINED_TYPES.put("VARYING ARRAY", new TypeDesc(DBPDataKind.ARRAY, Types.ARRAY, 0, 0, 0));

        PREDEFINED_TYPES.put("VARRAY", new TypeDesc(DBPDataKind.ARRAY, Types.ARRAY, 0, 0, 0));
        PREDEFINED_TYPES.put("ROWID", new TypeDesc(DBPDataKind.ROWID, Types.ROWID, 0, 0, 0));
        PREDEFINED_TYPES.put("LONG", new TypeDesc(DBPDataKind.BINARY, Types.LONGVARCHAR, 0, 0, 0));
        PREDEFINED_TYPES.put("IMAGE", new TypeDesc(DBPDataKind.BINARY, Types.LONGVARBINARY, 0, 0, 0));
        PREDEFINED_TYPES.put("BINARY", new TypeDesc(DBPDataKind.BINARY, Types.BINARY, 0, 0, 0));
        PREDEFINED_TYPES.put("VARBINARY", new TypeDesc(DBPDataKind.BINARY, Types.VARBINARY, 0, 0, 0));
        PREDEFINED_TYPES.put("RAW", new TypeDesc(DBPDataKind.BINARY, Types.VARBINARY, 0, 0, 0));
        PREDEFINED_TYPES.put("LONG RAW", new TypeDesc(DBPDataKind.BINARY, Types.LONGVARBINARY, 0, 0, 0));
        PREDEFINED_TYPES.put("NVARCHAR2", new TypeDesc(DBPDataKind.STRING, Types.NVARCHAR, 0, 0, 0));
        PREDEFINED_TYPES.put("NCHAR", new TypeDesc(DBPDataKind.STRING, Types.NCHAR, 0, 0, 0));
        PREDEFINED_TYPES.put("NCLOB", new TypeDesc(DBPDataKind.CONTENT, Types.NCLOB, 0, 0, 0));
        PREDEFINED_TYPES.put("LOB POINTER", new TypeDesc(DBPDataKind.CONTENT, Types.BLOB, 0, 0, 0));
        PREDEFINED_TYPES.put("BIT", new TypeDesc(DBPDataKind.BOOLEAN, Types.BIT, 0, 0, 0));

        for (TypeDesc type : PREDEFINED_TYPES.values()) {
            PREDEFINED_TYPE_IDS.put(type.valueType, type);
        }
    }

    private final AttributeCache attributeCache;
    private final MethodCache methodCache;
    private final ProcdureCache procdureCache;
    private final TypeCache typeCache;
    private String typeCode;
    private byte[] typeOID;
    private Object superType;
    private Object superDefineType;
    private boolean flagPredefined;
    private boolean flagIncomplete;
    private boolean flagFinal;
    private boolean flagInstantiable;
    private boolean flagHeadEncrypted;
    private boolean flagBodyEncrypted;
    private TypeDesc typeDesc;
    private int valueType = java.sql.Types.OTHER;
    private String sourceDeclaration;
    private String sourceDefinition;
    private String authIDOption;
    private String typeDDL;
    private List<DamengSubVariable> varList;
    private List<DamengSubProcedure> procList;
    private List<DamengSubFunction> funcList;
    private List<DamengSubType> typeList;
    private DamengDataType componentType;
    private Date created;

    public DamengDataType(DBSObject owner, String typeName, boolean persisted) {
        super(owner, typeName, persisted);
        attributeCache = null;
        methodCache = null;
        procdureCache = null;
        typeCache = null;
        typeCode = "CLASS";
        if (owner instanceof DamengDataSource) {
            flagPredefined = true;
            findTypeDesc(typeName);
        }
    }

    public DamengDataType(DBSObject owner, ResultSet dbResult) {
        super(owner, JDBCUtils.safeGetString(dbResult, "NAME"), true);
        this.typeCode = JDBCUtils.safeGetString(dbResult, "SUBTYPE$");
        this.typeOID = JDBCUtils.safeGetBytes(dbResult, "ID");
        int info1 = JDBCUtils.safeGetInt(dbResult, "INFO1");
        this.flagFinal = ((info1 >> 3) & 0x01) == 0;
        this.flagInstantiable = ((info1 >> 4) & 0x01) == 0;
        this.flagHeadEncrypted = (info1 & 0x01) != 0;
        this.flagBodyEncrypted = (info1 & 0x02) != 0;
        this.authIDOption = ((info1 >> 5) & 0x01) != 0 ? DamengConstants.AUTHID_OPTION_CURRENT_USER
            : DamengConstants.AUTHID_OPTION_DEFINER;
        String superTypeOwner = JDBCUtils.safeGetString(dbResult, "P_PKG_SCH_NAME");
        this.created = JDBCUtils.safeGetTimestamp(dbResult, DamengConstants.CREATED);
        this.typeDDL = JDBCUtils.safeGetString(dbResult, "DEFINITION");
        this.varList = new ArrayList<DamengSubVariable>();
        this.procList = new ArrayList<DamengSubProcedure>();
        this.funcList = new ArrayList<DamengSubFunction>();
        this.typeList = new ArrayList<DamengSubType>();
        extractPackageInfo(typeDDL, varList, procList, funcList, typeList);
        this.superType = new DamengLazyReference(superTypeOwner, JDBCUtils.safeGetString(dbResult, "P_PKG_NAME"));
        attributeCache = varList.size() > 0 ? new AttributeCache() : null;
        methodCache = funcList.size() > 0 ? new MethodCache() : null;
        procdureCache = procList.size() > 0 ? new ProcdureCache() : null;
        typeCache = typeList.size() > 0 ? new TypeCache() : null;
        if (owner instanceof DamengDataSource && flagPredefined) {
            // Determine value type for predefined types
            findTypeDesc(name);
        } else {
            if (TYPE_CODE_COLLECTION.equals(this.typeCode)) {
                this.valueType = java.sql.Types.ARRAY;
            } else if (TYPE_CODE_OBJECT.equals(this.typeCode)) {
                this.valueType = java.sql.Types.STRUCT;
            } else {
                if (this.name.equals(DamengConstants.TYPE_NAME_XML)
                    && owner.getName().equals(DamengConstants.SCHEMA_SYS)) {
                    this.valueType = java.sql.Types.SQLXML;
                }
            }
        }
    }

    public DamengDataType(DBSObject owner, ResultSet dbResult, boolean isNeedVar) {
        super(owner, JDBCUtils.safeGetString(dbResult, "NAME"), true);
        this.typeCode = JDBCUtils.safeGetString(dbResult, "SUBTYPE$");
        this.typeOID = JDBCUtils.safeGetBytes(dbResult, "ID");
        int info1 = JDBCUtils.safeGetInt(dbResult, "INFO1");
        this.flagFinal = ((info1 >> 3) & 0x01) == 0;
        this.flagInstantiable = ((info1 >> 4) & 0x01) == 0;
        this.flagHeadEncrypted = (info1 & 0x01) != 0;
        this.flagBodyEncrypted = (info1 & 0x02) != 0;
        this.authIDOption = ((info1 >> 5) & 0x01) != 0 ? DamengConstants.AUTHID_OPTION_CURRENT_USER
            : DamengConstants.AUTHID_OPTION_DEFINER;
        String superTypeOwner = JDBCUtils.safeGetString(dbResult, "P_PKG_SCH_NAME");
        this.created = JDBCUtils.safeGetTimestamp(dbResult, DamengConstants.CREATED);
        this.typeDDL = JDBCUtils.safeGetString(dbResult, "DEFINITION");
        this.varList = new ArrayList<DamengSubVariable>();
        this.procList = new ArrayList<DamengSubProcedure>();
        this.funcList = new ArrayList<DamengSubFunction>();
        this.typeList = new ArrayList<DamengSubType>();
        if (isNeedVar) {
            extractPackageInfo(typeDDL, varList, procList, funcList, typeList);
        }
        this.superType = new DamengLazyReference(superTypeOwner, JDBCUtils.safeGetString(dbResult, "P_PKG_NAME"));
        attributeCache = varList.size() > 0 ? new AttributeCache() : null;
        methodCache = funcList.size() > 0 ? new MethodCache() : null;
        procdureCache = procList.size() > 0 ? new ProcdureCache() : null;
        typeCache = typeList.size() > 0 ? new TypeCache() : null;
        if (owner instanceof DamengDataSource && flagPredefined) {
            // Determine value type for predefined types
            findTypeDesc(name);
        } else {
            if (TYPE_CODE_COLLECTION.equals(this.typeCode)) {
                this.valueType = java.sql.Types.ARRAY;
            } else if (TYPE_CODE_OBJECT.equals(this.typeCode)) {
                this.valueType = java.sql.Types.STRUCT;
            } else {
                if (this.name.equals(DamengConstants.TYPE_NAME_XML)
                    && owner.getName().equals(DamengConstants.SCHEMA_SYS)) {
                    this.valueType = java.sql.Types.SQLXML;
                }
            }
        }
    }

    @Nullable
    public static DBPDataKind getDataKind(String typeName) {
        TypeDesc desc = PREDEFINED_TYPES.get(typeName);
        return desc != null ? desc.dataKind : null;
    }

    public static DamengDataType resolveDataType(DBRProgressMonitor monitor, DamengDataSource dataSource,
                                                 String typeOwner, String typeName) {
        typeName = normalizeTypeName(typeName);
        DamengSchema typeSchema = null;
        DamengDataType type = null;
        if (typeOwner != null) {
            try {
                typeSchema = dataSource.getSchema(monitor, typeOwner);
                if (typeSchema == null) {
                    log.error("Type attr schema '" + typeOwner + "' not found");
                } else {
                    type = typeSchema.getDataType(monitor, typeName);
                }
            } catch (DBException e) {
                log.error(e);
            }
        } else {
            type = (DamengDataType) dataSource.getLocalDataType(typeName);
        }
        if (type == null) {
            type = new DamengDataType(typeSchema == null ? dataSource : typeSchema, typeName, true);
            type.flagPredefined = true;
            if (typeSchema == null) {
                dataSource.dataTypeCache.cacheObject(type);
            } else {
                typeSchema.dataTypeCache.cacheObject(type);
            }
        }
        return type;
    }

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
        return typeName.trim();
    }

    private void extractPackageInfo(String pkgDdl, List<DamengSubVariable> varList, List<DamengSubProcedure> procList,
                                    List<DamengSubFunction> funcList, List<DamengSubType> typeList) {
        npar_root_t root;
        try {
            root = new Parser().parse(pkgDdl);
        } catch (Exception e) {
            log.error("sql parse fail", e);
            return;
        }

        try {
            npar_stmt_t stmt = (npar_stmt_t) root.single;
            dm_lst_t lst = stmt.info.create_package_stmt != null ? stmt.info.create_package_stmt.def_list
                : stmt.info.create_package_body_stmt.def_list;
            dm_lst_node_t node = lst.start;
            while (node != null) {
                Object data = node.data;
                if (data != null) {
                    if (data instanceof npar_cpkg_def_proc_t) {
                        // procedure
                        if (((npar_cpkg_def_proc_t) data).is_proc) {
                            if (procList != null) {
                                DamengSubProcedure proc = new DamengSubProcedure();
                                proc.setName(((npar_cpkg_def_proc_t) data).name);
                                procList.add(proc);

                                // parameter
                                List<DamengSubParameter> paramList = new ArrayList<DamengSubParameter>();
                                proc.setParamList(paramList);
                                dm_lst_t param_lst = ((npar_cpkg_def_proc_t) data).param_def_list;
                                if (param_lst != null) {
                                    dm_lst_node_t param_node = param_lst.start;
                                    while (param_node != null) {
                                        DamengSubParameter param = new DamengSubParameter();
                                        param.setProcedure(proc);
                                        param.setName(((npar_param_def_t) param_node.data).param_name);
                                        param.setType(((npar_param_def_t) param_node.data).plsql_datatype.toString());
                                        paramList.add(param);
                                        param_node = param_node.next;
                                    }
                                }
                            }
                        }
                        // function
                        else {
                            if (funcList != null) {
                                DamengSubFunction func = new DamengSubFunction();
                                func.setName(((npar_cpkg_def_proc_t) data).name);
                                funcList.add(func);

                                // parameter
                                List<DamengSubParameter> paramList = new ArrayList<DamengSubParameter>();
                                func.setParamList(paramList);
                                DamengSubParameter param = new DamengSubParameter();
                                param.setProcedure(func);
                                param.setName("RETURN");
                                param.setType(((npar_cpkg_def_proc_t) data).plsql_datatype != null
                                    ? ((npar_cpkg_def_proc_t) data).plsql_datatype.toString()
                                    : DamengConstants.EMPTY);
                                param.setRet(true);
                                paramList.add(param);

                                dm_lst_t param_lst = ((npar_cpkg_def_proc_t) data).param_def_list;
                                if (param_lst != null) {
                                    dm_lst_node_t param_node = param_lst.start;
                                    while (param_node != null) {
                                        param = new DamengSubParameter();
                                        param.setProcedure(func);
                                        param.setName(((npar_param_def_t) param_node.data).param_name);
                                        param.setType(((npar_param_def_t) param_node.data).plsql_datatype.toString());
                                        paramList.add(param);
                                        param_node = param_node.next;
                                    }
                                }
                            }
                        }
                    } else if (data instanceof npar_stmt_t) {
                        npar_decl_stmt_t decl_stmt = ((npar_stmt_t) data).info.decl_stmt;
                        if (decl_stmt != null && decl_stmt.decl_plsql_type != null) {
                            // type
                            if (typeList != null) {
                                DamengSubType type = new DamengSubType();
                                type.setName(decl_stmt.decl_plsql_type.name);
                                typeList.add(type);
                            }
                        } else {
                            if (varList != null) {
                                if (decl_stmt.cursor != null) {
                                    DamengSubVariable var = new DamengSubVariable();
                                    var.setName(((npar_cursor_decl_t) decl_stmt.cursor).cursor_name);
                                    var.setType("CURSOR");
                                    varList.add(var);
                                } else {
                                    npar_decl_var_t decl_var = decl_stmt.var_name_lst.start;
                                    while (decl_var != null) {
                                        DamengSubVariable var = new DamengSubVariable();
                                        var.setName(decl_var.name);
                                        var.setType(decl_stmt.lvtype.toString());
                                        varList.add(var);
                                        decl_var = (npar_decl_var_t) decl_var.next;
                                    }
                                }
                            }
                        }
                    }
                }

                node = node.next;
            }
        } catch (Exception e) {
        }
    }

    // Use by tree navigator thru reflection
    public boolean hasMethods() {
        return methodCache != null;
    }

    // Use by tree navigator thru reflection
    public boolean hasAttributes() {
        return attributeCache != null;
    }

    public boolean hasProcdure() {
        return procdureCache != null;
    }

    public boolean hasType() {
        return typeCache != null;
    }

    private boolean findTypeDesc(String typeName) {
        if (typeName.startsWith("PL/SQL")) {
            return true;
        }
        typeName = normalizeTypeName(typeName);
        this.typeDesc = PREDEFINED_TYPES.get(typeName);
        if (this.typeDesc == null) {
            return false;
        } else {
            this.valueType = this.typeDesc.valueType;
            return true;
        }
    }

    @Nullable
    @Override
    public DamengSchema getSchema() {
        return parent instanceof DamengSchema ? (DamengSchema) parent : null;
    }

    @Override
    public DamengSourceType getSourceType() {
        return typeCode.equalsIgnoreCase("CLASS") ? DamengSourceType.CLASS : DamengSourceType.JCLASS;
    }

    @Override
    @Property(hidden = true, editable = true, updatable = true, order = -1)
    public String getObjectDefinitionText(DBRProgressMonitor monitor, Map<String, Object> options) throws DBCException {
        if (flagPredefined) {
            return "-- Source code not available";
        }
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
        if (!isPredefined()) {
            return new DBEPersistAction[] {new DamengObjectPersistAction(DamengObjectType.VIEW, "Compile type",
                "ALTER TYPE " + getFullyQualifiedName(DBPEvaluationContext.DDL) + " COMPILE")};
        } else {
            throw new DBCException("Can't compile " + getName() + ". Compilation works only for user-defined types.");
        }
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
    public Integer getScale() {
        return typeDesc == null ? 0 : typeDesc.minScale;
    }

    @Override
    public Integer getPrecision() {
        return typeDesc == null ? 0 : typeDesc.precision;
    }

    @Override
    public long getMaxLength() {
        return CommonUtils.toInt(getPrecision());
    }

    @Override
    public long getTypeModifiers() {
        return 0;
    }

    @Override
    public int getMinScale() {
        return typeDesc == null ? 0 : typeDesc.minScale;
    }

    @Override
    public int getMaxScale() {
        return typeDesc == null ? 0 : typeDesc.maxScale;
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

    // @Property (hidden = true, viewable = false, editable = false)
    public byte[] getTypeOID() {
        return typeOID;
    }

    @Property(viewable = true, editable = true, order = 3)
    public Object getSuperType(DBRProgressMonitor monitor) {
        if (superType == null) {
            return null;
        } else if (superType instanceof DamengDataType) {
            return (DamengDataType) superType;
        } else {
            try {
                DamengLazyReference olr = (DamengLazyReference) superType;
                if (olr.schemaName == null) {
                    return null;
                }
                final DamengSchema superSchema = getDataSource().getSchema(monitor, olr.schemaName);
                if (superSchema == null) {
                    log.warn("Referenced schema '" + olr.schemaName + "' not found for super type '" + olr.objectName
                        + "'");
                } else {
                    if (superSchema.dataTypeCache.getObject(monitor, superSchema, olr.objectName) == null) {
                        superDefineType = superSchema.defineTypeCache.getObject(monitor, superSchema, olr.objectName);
                        if (superDefineType == null) {
                            log.warn("Referenced type '" + olr.objectName + "' not found in schema '" + olr.schemaName
                                + "'");
                        } else {
                            return (DamengDefineType) superDefineType;
                        }
                    } else {
                        superType = superSchema.dataTypeCache.getObject(monitor, superSchema, olr.objectName);
                        return (DamengDataType) superType;
                    }
                }
            } catch (DBException e) {
                log.error(e);
            }
            superType = null;
            return null;
        }
    }

    public boolean isPredefined() {
        return flagPredefined;
    }

    public boolean isIncomplete() {
        return flagIncomplete;
    }

    @Property(viewable = true, order = 4)
    public boolean isFinal() {
        return flagFinal;
    }

    @Property(viewable = true, order = 5)
    public boolean isInstantiable() {
        return flagInstantiable;
    }

    @Property(viewable = true, order = 6)
    public boolean isHeadEncrypted() {
        return flagHeadEncrypted;
    }

    @Property(viewable = true, order = 7)
    public boolean isBodyEncrypted() {
        return flagBodyEncrypted;
    }

    @Property(viewable = true, order = 8)
    public String getAuthIDOption() {
        return authIDOption;
    }

    @Property(viewable = true, order = 9)
    public Date getCreated() {
        return created;
    }

    @NotNull
    @Override
    public DBSEntityType getEntityType() {
        return DBSEntityType.TYPE;
    }

    @Override
    public List<DamengDataTypeAttribute> getAttributes(@NotNull DBRProgressMonitor monitor) throws DBException {
        return attributeCache != null ? attributeCache.getAllObjects(monitor, this) : null;
    }

    @Nullable
    @Override
    public Collection<? extends DBSEntityConstraint> getConstraints(@NotNull DBRProgressMonitor monitor)
        throws DBException {
        return null;
    }

    @Override
    public DamengDataTypeAttribute getAttribute(@NotNull DBRProgressMonitor monitor, @NotNull String attributeName)
        throws DBException {
        return attributeCache != null ? attributeCache.getObject(monitor, this, attributeName) : null;
    }

    @Nullable
    @Association
    public Collection<DamengDataTypeMethod> getMethods(DBRProgressMonitor monitor) throws DBException {
        return methodCache != null ? methodCache.getAllObjects(monitor, this) : null;
    }

    @Nullable
    @Association
    public Collection<DamengDataTypeMethod> getProcdures(DBRProgressMonitor monitor) throws DBException {
        return procdureCache != null ? procdureCache.getAllObjects(monitor, this) : null;
    }

    @Nullable
    @Association
    public Collection<DamengDataTypeDefineType> getTypes(DBRProgressMonitor monitor) throws DBException {
        return typeCache != null ? typeCache.getAllObjects(monitor, this) : null;
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

    @Nullable
    @Override
    public Object geTypeExtension() {
        return typeOID;
    }

    public DamengDataType getComponentType(@NotNull DBRProgressMonitor monitor) throws DBException {
        if (componentType != null) {
            return componentType;
        }
        DamengSchema schema = getSchema();
        if (schema == null || !TYPE_CODE_COLLECTION.equals(typeCode) || !getDataSource().isAtLeastV10()) {
            return null;
        }
        try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Load collection types")) {
            try (JDBCPreparedStatement dbStat = session
                .prepareStatement("SELECT ELEM_TYPE_OWNER,ELEM_TYPE_NAME,ELEM_TYPE_MOD FROM "
                    + DamengUtils.getSysSchemaPrefix(getDataSource())
                    + "ALL_COLL_TYPES WHERE OWNER=? AND TYPE_NAME=?")) {
                dbStat.setString(1, schema.getName());
                dbStat.setString(2, getName());
                try (JDBCResultSet dbResults = dbStat.executeQuery()) {
                    if (dbResults.next()) {
                        String compTypeSchema = JDBCUtils.safeGetString(dbResults, "ELEM_TYPE_OWNER");
                        String compTypeName = JDBCUtils.safeGetString(dbResults, "ELEM_TYPE_NAME");

                        componentType = DamengDataType.resolveDataType(monitor, getDataSource(), compTypeSchema,
                            compTypeName);
                    } else {
                        log.warn("Can't resolve collection type [" + getName() + "]");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error reading collection types", e);
        }

        return componentType;
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

    static class TypeDesc {
        final DBPDataKind dataKind;

        final int valueType;

        final int precision;

        final int minScale;

        final int maxScale;

        private TypeDesc(DBPDataKind dataKind, int valueType, int precision, int minScale, int maxScale) {
            this.dataKind = dataKind;
            this.valueType = valueType;
            this.precision = precision;
            this.minScale = minScale;
            this.maxScale = maxScale;
        }
    }

    private class AttributeCache extends JDBCObjectCache<DamengDataType, DamengDataTypeAttribute> {
        private int number = 0;

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengDataType owner)
            throws SQLException {
            final JDBCPreparedStatement dbStat = session.prepareStatement("select 1 from dual connect by level <= ?;");
            dbStat.setInt(1, varList.size());
            return dbStat;
        }

        @Override
        protected DamengDataTypeAttribute fetchObject(@NotNull JDBCSession session, @NotNull DamengDataType owner,
                                                      @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            if (owner.getTypeName().equalsIgnoreCase("SYSTOPOLOGY.TOPOGEOMETRY") && number == 2) {
                DamengSubVariable var = new DamengSubVariable();
                var.setName("ID");
                var.setType("SYSGEO2.ST_Geometry");
                varList.set(2, var);
            }
            DamengDataTypeAttribute dmDataTypeAttribute = new DamengDataTypeAttribute(session.getProgressMonitor(),
                DamengDataType.this, varList.get(number), number);
            number++;
            return dmDataTypeAttribute;
        }
    }

    private class MethodCache extends JDBCObjectCache<DamengDataType, DamengDataTypeMethod> {
        private int number = 0;

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengDataType owner)
            throws SQLException {
            final JDBCPreparedStatement dbStat = session.prepareStatement("select 1 from dual connect by level <= ?;");
            dbStat.setInt(1, funcList.size());
            return dbStat;
        }

        @Override
        protected DamengDataTypeMethod fetchObject(@NotNull JDBCSession session, @NotNull DamengDataType owner,
                                                   @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            DamengDataTypeMethod dmDataTypeMethod = new DamengDataTypeMethod(session.getProgressMonitor(),
                DamengDataType.this, funcList.get(number));
            number++;
            return dmDataTypeMethod;
        }
    }

    private class ProcdureCache extends JDBCObjectCache<DamengDataType, DamengDataTypeMethod> {
        private int number = 0;

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengDataType owner)
            throws SQLException {
            final JDBCPreparedStatement dbStat = session.prepareStatement("select 1 from dual connect by level <= ?;");
            dbStat.setInt(1, procList.size());
            return dbStat;
        }

        @Override
        protected DamengDataTypeMethod fetchObject(@NotNull JDBCSession session, @NotNull DamengDataType owner,
                                                   @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            DamengDataTypeMethod dmDataTypeProc = new DamengDataTypeMethod(session.getProgressMonitor(),
                DamengDataType.this, procList.get(number));
            number++;
            return dmDataTypeProc;
        }
    }

    private class TypeCache extends JDBCObjectCache<DamengDataType, DamengDataTypeDefineType> {
        private int number = 0;

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengDataType owner)
            throws SQLException {
            final JDBCPreparedStatement dbStat = session.prepareStatement("select 1 from dual connect by level <= ?;");
            dbStat.setInt(1, typeList.size());
            return dbStat;
        }

        @Override
        protected DamengDataTypeDefineType fetchObject(@NotNull JDBCSession session, @NotNull DamengDataType owner,
                                                       @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            DamengDataTypeDefineType dmDataTypeDefineType = new DamengDataTypeDefineType(session.getProgressMonitor(),
                DamengDataType.this, typeList.get(number), number);
            number++;
            return dmDataTypeDefineType;
        }
    }

}
