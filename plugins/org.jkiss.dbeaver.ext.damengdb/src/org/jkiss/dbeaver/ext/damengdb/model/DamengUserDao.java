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
import java.util.List;

import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;

public class DamengUserDao {
	
    public static List<DamengGrantUser> getUsers(int userType, JDBCSession session) {
        return getUsers(null, false, false, 0, 0, userType, session);
    }

    public static List<DamengGrantUser> getUsers(String symbol, boolean name, boolean like, long pageSize, long pageNum,
                                                 int userType, JDBCSession session) {
        StringBuilder sql = new StringBuilder(128);
        if (pageSize > 0 && pageNum > 0) {
            sql.append("select top ").append(pageSize * (pageNum - 1)).append(", ").append(pageSize);
        } else {
            sql.append("select ");
        }
        sql.append(" USR_OBJ.ID, USR_OBJ.NAME, USR_OBJ.INFO1, USR_OBJ.CRTDATE, USR.LOCKED_STATUS from ");
        // user object
        sql.append("(");
        sql.append("select ID, NAME, INFO1, CRTDATE from SYSOBJECTS where TYPE$='UR' and SUBTYPE$='USER' ");
        if (userType != DamengGrantUser.USER_TYPE_ALL) {
            sql.append(" and INFO1=" + userType);
        }
        if (symbol != null) {
            sql.append(" and ");
            sql.append(getSymbolStr(symbol, name, like, null, null));
        }
        sql.append(") USR_OBJ, ");
        // user
        sql.append("(");
        sql.append("select ID, LOCKED_STATUS from SYS.SYSUSERS");
        sql.append(") USR ");

        sql.append(" where USR_OBJ.ID = USR.ID");

        sql.append(" ORDER BY INFO1,NAME");

        List<DamengGrantUser> userList = new ArrayList<DamengGrantUser>();
        try (JDBCPreparedStatement dbStat = session.prepareStatement(sql.toString())) {
            try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                while (dbResult.next()) {
                    DamengGrantUser user = new DamengGrantUser();
                    user.setId(dbResult.getString(1));
                    user.setName(dbResult.getString(2));
                    user.setUserType(dbResult.getInt(3));
                    user.setCreateDate((dbResult.getString(4) == null) ? "" : dbResult.getString(4).trim());
                    user.setLocked(dbResult.getInt(5) == 1);
                    userList.add(user);
                }
            }
        } catch (Exception e) {
        }

        return userList;
    }

    protected static String getSymbolStr(String symbol, boolean name, boolean like, String nameStr, String idStr) {
        StringBuilder sql = new StringBuilder();
        if (name) {
            sql.append(" " + (nameStr == null ? "NAME" : nameStr) + " ");
            if (like) {
                sql.append("like ");
                sql.append("\'%" + processQuoteOfName(symbol, "'") + "%\'");
            } else {
                sql.append("= ");
                sql.append("\'" + processQuoteOfName(symbol, "'") + "\'");
            }
        } else {
            sql.append(" " + (idStr == null ? "ID" : idStr) + " ");
            sql.append("= " + symbol);
        }
        return sql.toString();
    }

    public static String processQuoteOfName(String name, String quote) {
        if (quote == null || name == null) {
            return name;
        }

        String temp = name;
        StringBuilder result = new StringBuilder();
        int index = -1;
        int quetoLength = quote.length();
        while ((index = temp.indexOf(quote)) != -1) {
            result.append(temp.substring(0, index + quetoLength)).append(quote);
            temp = temp.substring(index + quetoLength);
        }
        result.append(temp);
        return result.toString();
    }

    public DamengGrantUser getUser(String userName, JDBCSession session) {
        List<DamengGrantUser> userList = getUsers(userName, true, false, 0, 0, DamengGrantUser.USER_TYPE_ALL, session);
        if (userList != null && userList.size() > 0) {
            return userList.get(0);
        }
        return null;
    }
    
}
