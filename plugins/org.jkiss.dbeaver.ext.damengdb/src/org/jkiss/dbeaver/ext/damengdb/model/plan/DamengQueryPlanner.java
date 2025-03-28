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

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.damengdb.model.DamengDataSource;
import org.jkiss.dbeaver.model.exec.DBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.exec.plan.DBCPlan;
import org.jkiss.dbeaver.model.exec.plan.DBCPlanNode;
import org.jkiss.dbeaver.model.exec.plan.DBCPlanStyle;
import org.jkiss.dbeaver.model.exec.plan.DBCQueryPlanner;
import org.jkiss.dbeaver.model.exec.plan.DBCQueryPlannerConfiguration;
import org.jkiss.dbeaver.model.exec.plan.DBCQueryPlannerSerialInfo;
import org.jkiss.dbeaver.model.impl.plan.AbstractExecutionPlanSerializer;
import org.jkiss.dbeaver.model.impl.plan.ExecutionPlanDeserializer;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.IntKeyMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Dameng execution plan node
 */
public class DamengQueryPlanner extends AbstractExecutionPlanSerializer implements DBCQueryPlanner {
    
	private final static String FORMAT_VERSION = "1";

    private final DamengDataSource dataSource;

    public DamengQueryPlanner(DamengDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public DamengDataSource getDataSource() {
        return dataSource;
    }

    @NotNull
    @Override
    public DBCPlan planQueryExecution(@NotNull DBCSession session, @NotNull String query,
                                      @NotNull DBCQueryPlannerConfiguration configuration) throws DBException {
        DamengExecutionPlan plan = new DamengExecutionPlan(dataSource, (JDBCSession) session, query);
        plan.explain();
        return plan;
    }

    @NotNull
    @Override
    public DBCPlanStyle getPlanStyle() {
        return DBCPlanStyle.PLAN;
    }

    private JsonObject createAttr(String key, String value) {
        JsonObject attr = new JsonObject();
        attr.add(key, new JsonPrimitive(CommonUtils.notEmpty(value)));
        return attr;
    }

    private JsonObject createAttr(String key, long value) {
        JsonObject attr = new JsonObject();
        attr.add(key, new JsonPrimitive(value));
        return attr;
    }

    @Override
    public void serialize(Writer planData, DBCPlan plan) throws IOException {
        serializeJson(planData, plan, dataSource.getInfo().getDriverName(), new DBCQueryPlannerSerialInfo() {
            @Override
            public String version() {
                return FORMAT_VERSION;
            }

            @Override
            public void addNodeProperties(DBCPlanNode node, JsonObject nodeJson) {

                JsonArray attributes = new JsonArray();
                if (node instanceof DamengPlanNode) {
                    DamengPlanNode dmNode = (DamengPlanNode) node;
                    attributes.add(createAttr(DamengExecutionPlan.PLAN_ID, dmNode.getPlanId()));
                    attributes.add(createAttr(DamengExecutionPlan.PLAN_NAME, dmNode.getPlanName()));
                    attributes.add(createAttr(DamengExecutionPlan.PLAN_COST, dmNode.getCost()));
                    attributes.add(createAttr(DamengExecutionPlan.PLAN_CARDINALITY, dmNode.getCardinality()));
                    attributes.add(createAttr(DamengExecutionPlan.PLAN_CPU_COST, dmNode.getCupCost()));
                    attributes.add(createAttr(DamengExecutionPlan.PLAN_CONTENT, dmNode.getAdditionalMessage()));
                    attributes.add(createAttr(DamengExecutionPlan.PLAN_DESCRIPTION, dmNode.getPlanNameDesc()));
                }
                nodeJson.add(PROP_ATTRIBUTES, attributes);
            }
        });
    }

    @SuppressWarnings("deprecation")
    @Override
    public DBCPlan deserialize(@NotNull Reader planData) throws IOException, InvocationTargetException {
        try {
            JsonObject jo = new JsonParser().parse(planData).getAsJsonObject();

            String query = getQuery(jo);

            ExecutionPlanDeserializer<DamengPlanNode> loader = new ExecutionPlanDeserializer<>();

            IntKeyMap<DamengPlanNode> allNodes = new IntKeyMap<>();

            List<DamengPlanNode> rootNodes = loader.loadRoot(dataSource, jo, (datasource, node, parent) -> {
                DamengPlanNode nodeDm = new DamengPlanNode(dataSource, allNodes, getNodeAttributes(node));
                allNodes.put(nodeDm.getPlanId(), nodeDm);
                return nodeDm;
            });
            return new DamengExecutionPlan(query, rootNodes);

        } catch (Throwable e) {
            throw new InvocationTargetException(e);
        }
    }

}