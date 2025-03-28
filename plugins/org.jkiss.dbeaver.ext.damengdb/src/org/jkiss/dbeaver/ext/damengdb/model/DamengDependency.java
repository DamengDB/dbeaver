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
import org.jkiss.dbeaver.model.DBPImage;
import org.jkiss.dbeaver.model.DBPImageProvider;
import org.jkiss.dbeaver.model.DBPUniqueObject;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

import com.dameng.common.util.LVal;
import com.dameng.common.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class DamengDependency extends DamengObject<DBSObject> implements DBPUniqueObject, DBPImageProvider {
    
	private final String objectOwnerName;

    private final String objectName;

    private final DamengObjectType objectType;

    public DamengDependency(DBSObject parent, String objectName, String objectOwnerName, String objectType) {
        super(parent, null, parent.isPersisted());
        this.objectName = objectName;
        this.objectOwnerName = objectOwnerName;
        this.objectType = DamengObjectType.getByType(objectType);
    }

    /**
     * Determine whether it is Type according to check whether the ddl has "is|as
     * object"
     */
    public static boolean classIsType(String ddl) {
        if (StringUtil.isEmpty(ddl)) {
            return false;
        }

        int idx = ddl.indexOf('(');
        if (idx > 0) {
            ddl = ddl.substring(0, idx);
        }

        boolean asIs = false;
        List<LVal> valList = StringUtil.lex(ddl, true);
        for (LVal val : valList) {
            if (val.type == LVal.Type.COMMENT || val.type == LVal.Type.SPACE) {
                continue;
            }

            if (StringUtil.equalsIgnoreCase(val.value, "object")) {
                if (asIs) {
                    return true;
                }
            }

            asIs = StringUtil.equalsIgnoreCase(val.value, "as") || StringUtil.equalsIgnoreCase(val.value, "is");
        }

        return false;
    }

    @NotNull
    public static List<DamengDependency> readDependencies(@NotNull DBRProgressMonitor monitor,
                                                          @NotNull DBSObject object, boolean dependents) throws DBException {
        List<DamengDependency> dependencies = new ArrayList<>();

        try (JDBCSession session = DBUtils.openMetaSession(monitor, object, "Load object dependencies")) {
            JDBCPreparedStatement dbStat = null;
            if (!dependents) {
                dbStat = session.prepareStatement(
                    "select dobjs.refed_type$ TYPE, objs.id, objs.name, sobjs.id sch_id, sobjs.name sch_name, texts.TXT DDL\n"
                        +
                        "from (select * from sysdependencies where id = (select tobjs.id from sysobjects tobjs, sysobjects schobjs where schobjs.name = ? and tobjs.name = ? and schobjs.id = tobjs.schid )) dobjs \n"
                        + "left join sysobjects objs on dobjs.refed_id = objs.id\n"
                        + "left join sysobjects sobjs on objs.schid = sobjs.id and objs.schid != 0\n"
                        + "left join systexts texts on objs.id = texts.id and texts.seqno = 0\n"
                        + "where objs.id is not null\n" + "union \n"
                        +
                        "select distinct 'TABLE' TYPE,TAB.ID, TAB.NAME, SCH.ID, SCH.NAME, '' DDL from SYSCONS CONS, (select * from SYSOBJECTS where SUBTYPE$ = 'INDEX') INDS, \n"
                        + "(select * from SYSOBJECTS where SUBTYPE$ = 'UTAB') TAB, (select * from SYSOBJECTS where TYPE$ = 'SCH') SCH \n"
                        + "where CONS.FINDEXID = INDS.ID and INDS.PID = TAB.ID and TAB.SCHID = SCH.ID and  CONS.ID  in \n"
                        +
                        "(select CON_OBJ.ID from (select * from SYSCONS where TYPE$='F') CONS, (select NAME, ID, CRTDATE from SYSOBJECTS where SUBTYPE$ = 'CONS') CON_OBJ, (select ID, NAME from SYSOBJECTS where TYPE$='SCH') SCH_OBJ, (select ID, NAME, SCHID from SYSOBJECTS \n"
                        +
                        "where TYPE$='SCHOBJ' and SUBTYPE$ like '_TAB' and  ID = (select tobjs.id from sysobjects tobjs, sysobjects schobjs where schobjs.name = ? and tobjs.name = ? and schobjs.id = tobjs.schid)) TAB_OBJ where CON_OBJ.ID = CONS.ID and CONS.TABLEID = TAB_OBJ.ID and TAB_OBJ.SCHID = SCH_OBJ.ID ORDER BY CON_OBJ.NAME);\n");
            } else {
                dbStat = session.prepareStatement(
                    "select dobjs.type$ type, objs.id, objs.name, sobjs.id sch_id, sobjs.name sch_name, texts.TXT DDL\n"
                        +
                        "from (select * from sysdependencies where refed_id = (select tobjs.id from sysobjects tobjs, sysobjects schobjs where schobjs.name = ? and tobjs.name = ? and schobjs.id = tobjs.schid )) dobjs \n"
                        + "left join sysobjects objs on dobjs.id = objs.id\n"
                        + "left join sysobjects sobjs on objs.schid = sobjs.id and objs.schid != 0\n"
                        + "left join systexts texts on objs.id = texts.id and texts.seqno = 0\n"
                        + "where objs.id is not null\n" + "union \n"
                        + "select distinct 'TABLE' type,obj.id, obj.name, sch_obj.id sch_id, sch_obj.name sch_name, '' DDL \n"
                        +
                        "from sysobjects obj,sysobjects sch_obj,(select * from SYSCONS where TYPE$ = 'F') f_cons,(select * from SYSCONS where TYPE$ = 'P' and tableid = (select tobjs.id from sysobjects tobjs, sysobjects schobjs where schobjs.name = ? and tobjs.name = ? and schobjs.id = tobjs.schid )) p_cons \n"
                        +
                        "where obj.subtype$ like '_TAB' and sch_obj.type$='SCH' and obj.schid = sch_obj.id and f_cons.findexid = p_cons.indexid and f_cons.tableid = obj.id\n");
            }
            dbStat.setString(1, object.getParentObject().getName());
            dbStat.setString(2, object.getName());
            dbStat.setString(3, object.getParentObject().getName());
            dbStat.setString(4, object.getName());

            try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                while (dbResult.next()) {
                    String typeName = JDBCUtils.safeGetString(dbResult, "TYPE");

                    // Deal with the ddl has "Class"
                    if (StringUtil.equals(typeName, "CLASS")) {
                        String ddl = JDBCUtils.safeGetString(dbResult, "DDL");
                        if (classIsType(ddl)) {
                            typeName = "TYPE";
                        }
                    }

                    if (typeName.equalsIgnoreCase("PACKAGE_BODY")) {
                        typeName = "PACKAGE BODY";
                    }
                    dependencies.add(new DamengDependency(object, JDBCUtils.safeGetString(dbResult, "NAME"),
                        JDBCUtils.safeGetString(dbResult, "SCH_NAME"), typeName));
                }
            }
            dbStat.close();
        } catch (Exception e) {
            throw new DBCException("Error reading dependencies", e);
        }

        return dependencies;
    }

    @NotNull
    @Override
    @Property(viewable = true, order = 1)
    public String getName() {
        return objectName;
    }

    @Property(viewable = true, order = 2)
    public DamengObjectType getObjectType() {
        return objectType;
    }

    @Property(viewable = true, order = 3)
    public DamengSchema getObjectOwner(DBRProgressMonitor monitor) throws DBException {
        return getDataSource().getSchema(monitor, objectOwnerName);
    }

    @Property(viewable = true, order = 4)
    public DBSObject getObject(DBRProgressMonitor monitor) throws DBException {
        return objectType.findObject(monitor, getObjectOwner(monitor), objectName);
    }

    @Override
    public DBPImage getObjectImage() {
        return objectType.getImage();
    }

    @NotNull
    @Override
    public String getUniqueName() {
        return objectOwnerName + '.' + objectName;
    }
    
}
