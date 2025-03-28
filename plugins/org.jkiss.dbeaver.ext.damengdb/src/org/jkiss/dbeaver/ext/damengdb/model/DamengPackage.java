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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.damengdb.model.source.DamengSourceObject;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBPRefreshableObject;
import org.jkiss.dbeaver.model.DBPScriptObjectExt;
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
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectContainer;
import org.jkiss.dbeaver.model.struct.DBSObjectState;
import org.jkiss.dbeaver.model.struct.rdb.DBSPackage;
import org.jkiss.dbeaver.model.struct.rdb.DBSProcedureContainer;
import org.jkiss.dbeaver.model.struct.rdb.DBSProcedureType;
import org.jkiss.utils.CommonUtils;

import dm.Parser;
import dm.dm_lst_node_t;
import dm.dm_lst_t;
import dm.npar.npar_cpkg_def_proc_t;
import dm.npar.npar_cursor_decl_t;
import dm.npar.npar_decl_stmt_t;
import dm.npar.npar_decl_var_t;
import dm.npar.npar_param_def_t;
import dm.npar.npar_root_t;
import dm.npar.npar_stmt_t;

/**
 * GenericProcedure
 */
public class DamengPackage extends DamengSchemaObject implements DamengSourceObject, DBPScriptObjectExt,
    DBSObjectContainer, DBSPackage, DBPRefreshableObject, DBSProcedureContainer {

    private static final Log log = Log.getLog(DamengPackage.class);

    private final ProceduresCache proceduresCache = new ProceduresCache();

    private final AttributeCache attributeCache;

    private final MethodCache methodCache;

    private final ProcdureCache procdureCache;

    private final TypeCache typeCache;

    private boolean valid;

    private Date created;

    private boolean flagHeadEncrypted;

    private boolean flagBodyEncrypted;

    private String authIDOption;

    private String sourceDeclaration;

    private String sourceDefinition;

    private String typeDDL;

    private List<DamengSubVariable> varList;

    private List<DamengSubProcedure> procList;

    private List<DamengSubFunction> funcList;

    private List<DamengSubType> typeList;

    public DamengPackage(DamengSchema schema, ResultSet dbResult) {
        super(schema, JDBCUtils.safeGetString(dbResult, DamengConstants.COLUMN_OBJECT_NAME), true);
        this.valid = DamengConstants.RESULT_YES_VALUE
            .equals(JDBCUtils.safeGetString(dbResult, DamengConstants.RESULT_STATUS_VALID));
        this.created = JDBCUtils.safeGetTimestamp(dbResult, DamengConstants.CREATED);
        int info1 = JDBCUtils.safeGetInt(dbResult, "INFO1");
        this.flagHeadEncrypted = (info1 & 0x01) != 0;
        this.flagBodyEncrypted = (info1 & 0x02) != 0;
        this.authIDOption = ((info1 >> 5) & 0x01) != 0 ? DamengConstants.AUTHID_OPTION_CURRENT_USER
            : DamengConstants.AUTHID_OPTION_DEFINER;

        this.typeDDL = JDBCUtils.safeGetString(dbResult, "DEFINITION");
        this.varList = new ArrayList<DamengSubVariable>();
        this.procList = new ArrayList<DamengSubProcedure>();
        this.funcList = new ArrayList<DamengSubFunction>();
        this.typeList = new ArrayList<DamengSubType>();
        extractPackageInfo(typeDDL, varList, procList, funcList, typeList);

        attributeCache = varList.size() > 0 ? new AttributeCache() : null;
        methodCache = funcList.size() > 0 ? new MethodCache() : null;
        procdureCache = procList.size() > 0 ? new ProcdureCache() : null;
        typeCache = typeList.size() > 0 ? new TypeCache() : null;
    }

    public DamengPackage(DamengSchema schema, String name) {
        super(schema, name, false);
        attributeCache = null;
        methodCache = null;
        procdureCache = null;
        typeCache = null;
    }

    @Property(viewable = true, order = 3)
    public Date getCreated() {
        return created;
    }

    @Property(viewable = true, order = 4)
    public boolean isHeadEncrypted() {
        return flagHeadEncrypted;
    }

    @Property(viewable = true, order = 5)
    public boolean isBodyEncrypted() {
        return flagBodyEncrypted;
    }

    @Property(viewable = true, order = 6)
    public String getAuthIDOption() {
        return authIDOption;
    }

    @Property(viewable = true, order = 7)
    public boolean isValid() {
        return valid;
    }

    @Override
    public DamengSourceType getSourceType() {
        return DamengSourceType.PACKAGE;
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
    @Property(hidden = true, editable = true, updatable = true, order = -1)
    public String getExtendedDefinitionText(DBRProgressMonitor monitor) throws DBException {
        if (sourceDefinition == null && monitor != null) {
            sourceDefinition = DamengUtils.getSource(monitor, this, true, true);
        }
        return sourceDefinition;
    }

    public void setExtendedDefinitionText(String source) {
        this.sourceDefinition = source;
    }

    @Association
    public Collection<DamengDependencyGroup> getDependencies(DBRProgressMonitor monitor) {
        return DamengDependencyGroup.of(this);
    }

    public Collection<DamengProcedurePackaged> getProceduresOnly(DBRProgressMonitor monitor) throws DBException {
        return getProcedures(monitor).stream().filter(proc -> proc.getProcedureType() == DBSProcedureType.PROCEDURE)
            .collect(Collectors.toList());
    }

    public Collection<DamengProcedurePackaged> getFunctionsOnly(DBRProgressMonitor monitor) throws DBException {
        return getProcedures(monitor).stream().filter(proc -> proc.getProcedureType() == DBSProcedureType.FUNCTION)
            .collect(Collectors.toList());
    }

    public Collection<DamengProcedurePackaged> getProcedures(DBRProgressMonitor monitor) throws DBException {
        return proceduresCache.getAllObjects(monitor, this);
    }

    @Override
    public DamengProcedurePackaged getProcedure(DBRProgressMonitor monitor, String uniqueName) throws DBException {
        return proceduresCache.getObject(monitor, this, uniqueName);
    }

    @Nullable
    @Association
    public List<DamengDataTypeAttribute> getAttributes(@NotNull DBRProgressMonitor monitor) throws DBException {
        return attributeCache != null ? attributeCache.getAllObjects(monitor, this) : null;
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
    public Collection<? extends DBSObject> getChildren(@NotNull DBRProgressMonitor monitor) throws DBException {
        return proceduresCache.getAllObjects(monitor, this);
    }

    @Override
    public DBSObject getChild(@NotNull DBRProgressMonitor monitor, @NotNull String childName) throws DBException {
        return proceduresCache.getObject(monitor, this, childName);
    }

    @NotNull
    @Override
    public Class<? extends DBSObject> getPrimaryChildType(@Nullable DBRProgressMonitor monitor) throws DBException {
        return DamengProcedurePackaged.class;
    }

    @Override
    public void cacheStructure(@NotNull DBRProgressMonitor monitor, int scope) throws DBException {
        proceduresCache.getAllObjects(monitor, this);
    }

    @Override
    public DBSObject refreshObject(@NotNull DBRProgressMonitor monitor) throws DBException {
        this.proceduresCache.clearCache();
        this.sourceDeclaration = null;
        this.sourceDefinition = null;
        return this;
    }

    @Override
    public void refreshObjectState(@NotNull DBRProgressMonitor monitor) throws DBCException {
        this.valid = DamengUtils.getObjectStatus(monitor, this, DamengObjectType.PACKAGE)
            && DamengUtils.getObjectStatus(monitor, this, DamengObjectType.PACKAGE_BODY);
    }

    @Override
    public DBEPersistAction[] getCompileActions(DBRProgressMonitor monitor) {
        List<DBEPersistAction> actions = new ArrayList<>();
        /* if (!CommonUtils.isEmpty(sourceDeclaration)) */
        {
            actions.add(new DamengObjectPersistAction(DamengObjectType.PACKAGE, "Compile package",
                "ALTER PACKAGE " + getFullyQualifiedName(DBPEvaluationContext.DDL) + " COMPILE"));
        }

        return actions.toArray(new DBEPersistAction[0]);
    }

    @NotNull
    @Override
    public DBSObjectState getObjectState() {
        return valid ? DBSObjectState.NORMAL : DBSObjectState.INVALID;
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

    static class ProceduresCache extends JDBCObjectCache<DamengPackage, DamengProcedurePackaged> {

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengPackage owner)
            throws SQLException {
            JDBCPreparedStatement dbStat = session.prepareStatement(
                "SELECT P.*,CASE WHEN A.DATA_TYPE IS NULL THEN 'PROCEDURE' ELSE 'FUNCTION' END as PROCEDURE_TYPE FROM ALL_PROCEDURES P\n"
                    +
                    "LEFT OUTER JOIN ALL_ARGUMENTS A ON A.OWNER=P.OWNER AND A.PACKAGE_NAME=P.OBJECT_NAME AND A.OBJECT_NAME=P.PROCEDURE_NAME AND A.ARGUMENT_NAME IS NULL AND A.DATA_LEVEL=0\n"
                    + "WHERE P.OWNER=? AND P.OBJECT_NAME=?\n" + "ORDER BY P.PROCEDURE_NAME");
            dbStat.setString(1, owner.getSchema().getName());
            dbStat.setString(2, owner.getName());
            return dbStat;
        }

        @Override
        protected DamengProcedurePackaged fetchObject(@NotNull JDBCSession session, @NotNull DamengPackage owner,
                                                      @NotNull JDBCResultSet dbResult) throws SQLException, DBException {
            return new DamengProcedurePackaged(owner, dbResult);
        }

        @Override
        protected void invalidateObjects(DBRProgressMonitor monitor, DamengPackage owner,
                                         Iterator<DamengProcedurePackaged> objectIter) {
            Map<String, DamengProcedurePackaged> overloads = new HashMap<>();
            while (objectIter.hasNext()) {
                final DamengProcedurePackaged proc = objectIter.next();
                if (CommonUtils.isEmpty(proc.getName())) {
                    objectIter.remove();
                    continue;
                }
                final DamengProcedurePackaged overload = overloads.get(proc.getName());
                if (overload == null) {
                    overloads.put(proc.getName(), proc);
                } else {
                    if (overload.getOverloadNumber() == null) {
                        overload.setOverload(1);
                    }
                    proc.setOverload(overload.getOverloadNumber() + 1);
                    overloads.put(proc.getName(), proc);
                }
            }
        }
    }

    private class AttributeCache extends JDBCObjectCache<DamengPackage, DamengDataTypeAttribute> {
        private int number = 0;

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengPackage owner)
            throws SQLException {
            final JDBCPreparedStatement dbStat = session.prepareStatement("select 1 from dual connect by level <= ?;");
            dbStat.setInt(1, varList.size());
            return dbStat;
        }

        @Override
        protected DamengDataTypeAttribute fetchObject(@NotNull JDBCSession session, @NotNull DamengPackage owner,
                                                      @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            DamengDataTypeAttribute dmDataTypeAttribute = new DamengDataTypeAttribute(session.getProgressMonitor(),
                owner, varList.get(number), number);
            number++;
            return dmDataTypeAttribute;
        }
    }

    private class MethodCache extends JDBCObjectCache<DamengPackage, DamengDataTypeMethod> {
        private int number = 0;

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengPackage owner)
            throws SQLException {
            final JDBCPreparedStatement dbStat = session.prepareStatement("select 1 from dual connect by level <= ?;");
            dbStat.setInt(1, funcList.size());
            return dbStat;
        }

        @Override
        protected DamengDataTypeMethod fetchObject(@NotNull JDBCSession session, @NotNull DamengPackage owner,
                                                   @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            DamengDataTypeMethod dmDataTypeMethod = new DamengDataTypeMethod(session.getProgressMonitor(), owner,
                funcList.get(number));
            number++;
            return dmDataTypeMethod;
        }
    }

    private class ProcdureCache extends JDBCObjectCache<DamengPackage, DamengDataTypeMethod> {
        private int number = 0;

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengPackage owner)
            throws SQLException {
            final JDBCPreparedStatement dbStat = session.prepareStatement("select 1 from dual connect by level <= ?;");
            dbStat.setInt(1, procList.size());
            return dbStat;
        }

        @Override
        protected DamengDataTypeMethod fetchObject(@NotNull JDBCSession session, @NotNull DamengPackage owner,
                                                   @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            DamengDataTypeMethod dmDataTypeProc = new DamengDataTypeMethod(session.getProgressMonitor(), owner,
                procList.get(number));
            number++;
            return dmDataTypeProc;
        }
    }

    private class TypeCache extends JDBCObjectCache<DamengPackage, DamengDataTypeDefineType> {
        private int number = 0;

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull DamengPackage owner)
            throws SQLException {
            final JDBCPreparedStatement dbStat = session.prepareStatement("select 1 from dual connect by level <= ?;");
            dbStat.setInt(1, typeList.size());
            return dbStat;
        }

        @Override
        protected DamengDataTypeDefineType fetchObject(@NotNull JDBCSession session, @NotNull DamengPackage owner,
                                                       @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            DamengDataTypeDefineType dmDataTypeDefineType = new DamengDataTypeDefineType(session.getProgressMonitor(),
                owner, typeList.get(number), number);
            number++;
            return dmDataTypeDefineType;
        }
    }

}
