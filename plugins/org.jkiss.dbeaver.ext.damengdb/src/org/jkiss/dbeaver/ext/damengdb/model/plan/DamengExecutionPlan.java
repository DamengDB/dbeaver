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
package org.jkiss.dbeaver.ext.damengdb.model.plan;

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.damengdb.model.DamengConstants;
import org.jkiss.dbeaver.ext.damengdb.model.DamengDataSource;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.exec.plan.DBCPlanCostNode;
import org.jkiss.dbeaver.model.exec.plan.DBCPlanNode;
import org.jkiss.dbeaver.model.impl.plan.AbstractExecutionPlan;
import org.jkiss.utils.IntKeyMap;

/**
 * Dameng execution plan analyser
 */
public class DamengExecutionPlan extends AbstractExecutionPlan {
	
    public static final String PLAN_ID = "PLAN_ID";
    public static final String PLAN_NAME = "PLAN_NAME";
    public static final String PLAN_CONTENT = "PLAN_CONTENT";
    public static final String PLAN_COST = "COST";
    public static final String PLAN_CARDINALITY = "CARDINALITY";
    public static final String PLAN_CPU_COST = "CPU_COST";
    public static final String PLAN_DESCRIPTION = "PLAN_DESCRIPTION";
    public static final String PLAN_LEVEL = "LEVEL";
    private DamengDataSource dataSource;
    private JDBCSession session;
    private String query;
    private List<DamengPlanNode> rootNodes;
    private Map<String, String> nodeDescmap;

    public DamengExecutionPlan(DamengDataSource dataSource, JDBCSession session, String query) {
        this.dataSource = dataSource;
        this.session = session;
        this.query = query;
    }

    public DamengExecutionPlan(String query, List<DamengPlanNode> nodes) {
        this.query = query;
        this.rootNodes = nodes;
    }

    /**
     * Get Execution Plan
     *
     * @param conn
     * @param sql
     * @return
     * @throws SQLException
     */
    public static String getExplainInfo(Connection conn, String sql) throws SQLException {
        try {
            Method method = conn.getClass().getDeclaredMethod("getExplainInfo", new Class[] {String.class});
            method.setAccessible(true);
            Object result = method.invoke(conn, new Object[] {sql});
            return (String) result;
        } catch (Exception e) {
            if (e instanceof SQLException) {
                throw (SQLException) e;
            }
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object getPlanFeature(String feature) {
        if (DBCPlanCostNode.FEATURE_PLAN_COST.equals(feature) || DBCPlanCostNode.FEATURE_PLAN_DURATION.equals(feature)
            || DBCPlanCostNode.FEATURE_PLAN_ROWS.equals(feature)) {
            return true;
        } else if (DBCPlanCostNode.PLAN_DURATION_MEASURE.equals(feature)) {
            return "KC";
        }

        return super.getPlanFeature(feature);
    }

    @Override
    public String getQueryString() {
        return query;
    }

    @Override
    public String getPlanQueryString() throws DBException {
        String sql = getQueryString().trim();
        String sqlTemp = sql.toUpperCase();
        if (sqlTemp.contains(DamengConstants.EXPLAIN_KEYWORD)) {
            int position = sqlTemp.indexOf(DamengConstants.EXPLAIN_KEYWORD);
            if (position != -1) {
                sql = sql.substring(DamengConstants.EXPLAIN_KEYWORD_LEN);
            }
        }

        return sql;
    }

    @Override
    public List<? extends DBCPlanNode> getPlanNodes(Map<String, Object> options) {
        return rootNodes;
    }

    public void explain() throws DBException {
        String sql = getPlanQueryString();
        try {
            Connection con = session.getOriginal();
            // Get Execution Plan
            String planString = getExplainInfo(con, sql);
            try {
                // Set Execution Plan to node
                readPlanNodes(planString);
            } catch (Exception e) {
                // do nothing
            }
        } catch (SQLException e) {
            // do nothing
        }
    }

    /**
     * Get all description of the Execution Plan and add them to the Map
     */
    public Map<String, String> getDescMap() throws SQLException {
        // Load the descMap once
        if (this.nodeDescmap == null) {
            nodeDescmap = new HashMap<String, String>();
            String sql = "SELECT NAME,DESC_CONTENT from SYS.V$SQL_NODE_NAME";
            JDBCPreparedStatement dbStat = session.prepareStatement(sql);
            JDBCResultSet dbResult = dbStat.executeQuery();
            while (dbResult.next()) {
                nodeDescmap.put(dbResult.getString(1), dbResult.getString(2));
            }
            dbStat.close();
        }
        return nodeDescmap;
    }

    /**
     * Explain Execution Plan by line
     *
     * @param line
     * @param isIndentation Whether keeps the indentation of the planName
     * @return
     * @throws Exception
     */
    public Map<String, Object> parsePlanString2PlanMap(String line, boolean isIndentation) throws Exception {
        if (line.isEmpty()) {
            return null;
        }

        String currentPlan = null;
        String planName = null;
        String planName_desc = null;
        String planContent = null;
        String planStatistics = null;
        String cost = null;
        String card = null;
        String cpuTime = null;
        int column = -1;
        int wellMarkIndex = -1;
        int colonIndex = -1;
        int semicolonIndex = -1;
        int commaIndex1 = -1;
        int commaIndex2 = -1;
        // Get plan_id from line
        int plan_id = Integer.parseInt(line.replaceAll("[^0-9].*", ""));

        Map<String, Object> planNodeMap = new HashMap<String, Object>();

        wellMarkIndex = line.indexOf('#');
        if (wellMarkIndex != -1) {
            currentPlan = line.substring(wellMarkIndex + 1);
            column = wellMarkIndex + 1;
            colonIndex = currentPlan.indexOf(':');
            if (colonIndex != -1) {
                // Keep the indentation
                if (isIndentation) {

                    String indentation = null;
                    String planWName = line.substring(wellMarkIndex, line.indexOf(':'));

                    int indentationNum = (column - 5) * 3;
                    if (indentationNum > 0) {
                        indentation = String.format("%" + indentationNum + "s", " ");
                        planName = planWName.replace("#", indentation);
                    } else {
                        // First line doesn't need indentation
                        planName = currentPlan.substring(0, colonIndex);
                    }
                } else {
                    planName = currentPlan.substring(0, colonIndex);
                }

                semicolonIndex = currentPlan.indexOf(';');
                if (semicolonIndex != -1) {
                    planStatistics = currentPlan.substring(colonIndex + 1, semicolonIndex);
                    planContent = currentPlan.substring(semicolonIndex + 1);
                } else {
                    // Deal with the Execution Plan lacks the semicolon
                    semicolonIndex = currentPlan.indexOf(']');
                    if (semicolonIndex != -1) {
                        planStatistics = currentPlan.substring(colonIndex + 1, semicolonIndex + 1);
                        planContent = currentPlan.substring(semicolonIndex + 1);
                    } else {
                        planStatistics = currentPlan.substring(colonIndex + 1);
                        planContent = null;
                    }
                }
                planStatistics = planStatistics.trim();
                commaIndex1 = planStatistics.indexOf(',');
                commaIndex2 = planStatistics.indexOf(',', commaIndex1 + 1);
                cost = planStatistics.substring(1, commaIndex1);
                card = planStatistics.substring(commaIndex1 + 1, commaIndex2);
                cpuTime = planStatistics.substring(commaIndex2 + 1, planStatistics.length() - 1);

            } else {
                planName = currentPlan;
                planStatistics = "";
                planContent = "";
            }
        }

        // Get the Execution Plan description
        // Remove the indentation space
        planName_desc = getDescMap().get(planName.trim());

        planNodeMap.put(PLAN_ID, plan_id);
        planNodeMap.put(PLAN_NAME, planName);
        if (planName_desc == null) {
            planNodeMap.put(PLAN_DESCRIPTION, planName.trim());
        } else {
            planNodeMap.put(PLAN_DESCRIPTION, planName_desc);
        }
        planNodeMap.put(PLAN_CONTENT, planContent);
        planNodeMap.put(PLAN_COST, cost);
        planNodeMap.put(PLAN_CARDINALITY, card);
        planNodeMap.put(PLAN_CPU_COST, cpuTime);
        planNodeMap.put("PARENT_ID", null);
        planNodeMap.put(PLAN_LEVEL, column);

        return planNodeMap;
    }

    /**
     * Explain the Execution Plan
     *
     * @param planText
     */
    private void readPlanNodes(String planText) throws Exception {
        // PlanName doesn't keep the indentation
        boolean isIndentation = false;
        String line = null;
        Map<String, Object> planmap = null;
        rootNodes = new ArrayList<>();
        IntKeyMap<DamengPlanNode> allNodes = new IntKeyMap<>();
        BufferedReader reader = new BufferedReader(new StringReader(planText));
        // First line is unnecessary
        reader.readLine();
        while ((line = reader.readLine()) != null) {
            planmap = parsePlanString2PlanMap(line, isIndentation);
            DamengPlanNode node = new DamengPlanNode(dataSource, allNodes, planmap);
            allNodes.put(node.getPlanId(), node);
            if (node.getParent() == null) {
                rootNodes.add(node);
            }
        }

        // Update costs
        for (DamengPlanNode node : rootNodes) {
            node.updateCosts();
        }
    }
    
}
