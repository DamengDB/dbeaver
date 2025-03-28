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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;

public class DamengRoleDao {
	
    public static List<DamengGrantRole> getRoles(int userType, JDBCSession session) {
        return getRoles(null, false, false, 0, 0, userType, session);
    }

    public static List<DamengGrantRole> getRoles(String symbol, boolean name, boolean like, long pageSize, long pageNum,
                                                 int userType, JDBCSession session) {
        StringBuilder sql = new StringBuilder(128);
        if (pageSize > 0 && pageNum > 0) {
            sql.append("select top ").append(pageSize * (pageNum - 1)).append(", ").append(pageSize);
        } else {
            sql.append("select ");
        }
        sql.append(" ID, NAME, INFO1, VALID, CRTDATE from SYSOBJECTS WHERE TYPE$='UR' AND SUBTYPE$='ROLE'");
        if (userType != DamengGrantUser.USER_TYPE_ALL) {
            sql.append(" and INFO1=" + userType);
        }
        if (symbol != null) {
            sql.append(" and ");
            sql.append(DamengUserDao.getSymbolStr(symbol, name, like, null, null));
        }

        sql.append(" ORDER BY NAME");

        List<DamengGrantRole> roleList = new ArrayList<DamengGrantRole>();
        try (JDBCPreparedStatement dbStat = session.prepareStatement(sql.toString())) {
            try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                while (dbResult.next()) {
                    DamengGrantRole role = new DamengGrantRole();
                    role.setId(dbResult.getString(1));
                    role.setName(dbResult.getString(2));
                    role.setUserType(dbResult.getInt(3));
                    role.setEnable(dbResult.getString(4).equals("Y")); // 580615
                    role.setCreateDate((dbResult.getString(5) == null) ? "" : dbResult.getString(5).trim());
                    roleList.add(role);
                }
            }
        } catch (Exception e) {
        }

        return roleList;
    }

    public static List<DamengPrivilege> getObjPrivileges(DamengGrantRole role, DamengGrantUser user, String objId,
                                                         String colId, JDBCSession session) {

        String urid = role != null ? role.getId() : user.getId();
        StringBuilder sql = new StringBuilder(128);
        sql.append("select SF_GET_SYS_PRIV(PRIVID), GRANTABLE from SYSGRANTS where OBJID = " + objId);
        sql.append(" AND COLID = " + colId);
        sql.append(" AND URID = " + urid);

        List<DamengPrivilege> list = new ArrayList<DamengPrivilege>();

        try (JDBCPreparedStatement dbStat = session.prepareStatement(sql.toString())) {
            try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                while (dbResult.next()) {
                    DamengPrivilege privilege;

                    privilege = new DamengPrivilege();
                    privilege.setPrivName(dbResult.getString(1));
                    privilege.setCanGrant(dbResult.getString(2).equalsIgnoreCase("Y"));
                    list.add(privilege);
                }
            }
        } catch (Exception e) {
        }

        return list;
    }

    public static List<String> builePrivilegeManageDDL(String fullObjName, HashMap<String, DamengPrivilege[]> urObjMap,
                                                       HashMap<String, DamengPrivilege[]> orgUrObjMap, boolean cascade) {
        List<String> sqls = new ArrayList<String>();
        Set<String> urNames = urObjMap.keySet();
        for (String urName : urNames) {
            setSqls(urName, urObjMap.get(urName), orgUrObjMap.get(urName), sqls, fullObjName, cascade);
        }
        return sqls;
    }

    private static void setSqls(String fullGranteeName, DamengPrivilege[] curPrivs, DamengPrivilege[] orgPrivs,
                                List<String> sqlList, String fullObjName, boolean cascade) {
        StringBuilder sql;
        String fullPrivName = null;
        DamengPrivilege curPriv, orgPriv;
        boolean col = false;
        String colName = "";
        if (fullObjName != null && isColumnObj(fullObjName)) {
            col = true;
            String[] names = getColumnAndObjName(fullObjName);
            colName = names[0];
            fullObjName = names[1];
        }
        int index = (curPrivs.length == 1 ? 0 : 1);
        for (int i = index; i < curPrivs.length; ++i) {
            fullPrivName = curPrivs[i].getPrivName();
            if (col) {
                fullPrivName = fullPrivName + "(" + colName + ")";
            }
            orgPriv = null;
            curPriv = curPrivs[i];
            for (int j = 0; j < orgPrivs.length; ++j) {
                if (orgPrivs[j].getPrivName().equalsIgnoreCase(curPriv.getPrivName())) {
                    orgPriv = orgPrivs[j];
                    break;
                }
            }
            if (orgPriv == null) {
                if (curPriv.isBeGrant()) {
                    sql = new StringBuilder();
                    sql.append("grant " + fullPrivName);
                    sql.append(" on " + fullObjName);
                    sql.append(" to " + fullGranteeName);
                    if (curPriv.isCanGrant()) {
                        sql.append(" with grant option");
                    }
                    sql.append(";");
                    sqlList.add(sql.toString());
                } else {
                    continue;
                }
            } else {
                if (!curPriv.isBeGrant()) {
                    sql = new StringBuilder();
                    sql.append("revoke " + fullPrivName);
                    sql.append(" on " + fullObjName);
                    sql.append(" from " + fullGranteeName);
                    sql.append(cascade ? " cascade" : " restrict");
                    sql.append(";");
                    sqlList.add(sql.toString());
                } else {
                    if (curPriv.isCanGrant() != orgPriv.isCanGrant()) {
                        sql = new StringBuilder();
                        if (curPriv.isCanGrant()) {
                            sql.append("grant " + fullPrivName);
                            sql.append(" on " + fullObjName);
                            sql.append(" to " + fullGranteeName);
                            sql.append(" with grant option");
                        } else {
                            sql.append("revoke grant option for " + fullPrivName);
                            sql.append(" on " + fullObjName);
                            sql.append(" from " + fullGranteeName);
                            sql.append(cascade ? " cascade" : " restrict");
                        }
                        sql.append(";");
                        sqlList.add(sql.toString());
                    } else {
                        continue;
                    }
                }
            }
        }
    }

    private static boolean isColumnObj(String fullObjName) {
        if (fullObjName.indexOf("\".\"") != fullObjName.lastIndexOf("\".\"")) {
            return true;
        }
        return false;
    }

    private static String[] getColumnAndObjName(String fullObjName) {
        String[] names = new String[2];
        names[0] = fullObjName.substring(fullObjName.lastIndexOf("\".\"") + 2, fullObjName.length());
        names[1] = fullObjName.substring(0, fullObjName.lastIndexOf("\".\"") + 1);
        return names;
    }
    
}
