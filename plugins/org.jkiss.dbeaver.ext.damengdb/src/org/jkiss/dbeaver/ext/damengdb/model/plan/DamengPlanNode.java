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

import org.jkiss.dbeaver.ext.damengdb.model.DamengDataSource;
import org.jkiss.dbeaver.model.data.json.JSONUtils;
import org.jkiss.dbeaver.model.exec.plan.DBCPlanCostNode;
import org.jkiss.dbeaver.model.exec.plan.DBCPlanNodeKind;
import org.jkiss.dbeaver.model.impl.plan.AbstractExecutionPlanNode;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.IntKeyMap;

import java.util.*;

/**
 * Dameng execution plan node
 */
public class DamengPlanNode extends AbstractExecutionPlanNode implements DBCPlanCostNode {
    
	public final static String CAT_DETAILS = "Details";
    protected final List<DamengPlanNode> nested = new ArrayList<>();
    private String planName;
    private int plan_id;
    private String cardinality;
    private String cost;
    private String cupCost;
    private String additionalMessage;
    private long level;
    private String planNameDesc;
    private DamengPlanNode parent;

    public DamengPlanNode(DamengDataSource dataSource, IntKeyMap<DamengPlanNode> prevNodes,
                          Map<String, Object> attributes) {
        this.plan_id = JSONUtils.getInteger(attributes, DamengExecutionPlan.PLAN_ID, 0);
        this.planName = JSONUtils.getString(attributes, DamengExecutionPlan.PLAN_NAME);
        this.cost = JSONUtils.getString(attributes, DamengExecutionPlan.PLAN_COST);
        this.cardinality = JSONUtils.getString(attributes, DamengExecutionPlan.PLAN_CARDINALITY);
        this.cupCost = JSONUtils.getString(attributes, DamengExecutionPlan.PLAN_CPU_COST);
        this.additionalMessage = JSONUtils.getString(attributes, DamengExecutionPlan.PLAN_CONTENT);
        this.level = JSONUtils.getLong(attributes, DamengExecutionPlan.PLAN_LEVEL, 0);
        this.planNameDesc = JSONUtils.getString(attributes, DamengExecutionPlan.PLAN_DESCRIPTION);

        parent = findParent(prevNodes, this, plan_id);
    }

    @Override
    public DBCPlanNodeKind getNodeKind() {
        return DBCPlanNodeKind.DEFAULT;
    }

    /**
     * Get the parent node information of the External Function
     *
     * @param prevNodes  All previous node
     * @param node       current node
     * @param prevNodeid Id of the first previous node of the current node
     * @return
     */
    private DamengPlanNode findParent(IntKeyMap<DamengPlanNode> prevNodes, DamengPlanNode node, int plan_id) {
        int prevNodeid = plan_id - 1;
        if (prevNodeid > 0) {
            long preco = prevNodes.get(prevNodeid).getColumn();
            if (preco < node.getColumn()) {
                DamengPlanNode parent = prevNodes.get(prevNodeid);
                if (parent != null) {
                    parent.addChild(this);
                }
                return parent;
            } else if (preco == node.getColumn()) {
                DamengPlanNode parent = prevNodes.get(prevNodeid).getParent();
                if (parent != null) {
                    parent.addChild(this);
                }
                return parent;
            } else {
                return findParent(prevNodes, node, prevNodeid - 1);
            }
        } else {
            return null;
        }
    }

    private void addChild(DamengPlanNode node) {
        this.nested.add(node);
    }

    @Override
    public DamengPlanNode getParent() {
        return parent;
    }

    @Override
    public Collection<DamengPlanNode> getNested() {
        return nested;
    }

    @Override
    public String getNodeName() {
        return planName;
    }

    @Override
    public String getNodeType() {
        return null;
    }

    @Override
    public String getNodeDescription() {
        return null;
    }

    public int getPlanId() {
        return plan_id;
    }

    @Property(order = 0, viewable = true, supportsPreview = true)
    public String getPlanName() {
        return planName;
    }

    @Property(category = CAT_DETAILS, order = 1, viewable = true)
    public String getAdditionalMessage() {
        return additionalMessage;
    }

    @Property(order = 2, viewable = true)
    public String getCost() {
        return cost;
    }

    @Property(order = 3, viewable = true)
    public String getCardinality() {
        return cardinality;
    }

    @Property(category = CAT_DETAILS, order = 4, viewable = true)
    public String getCupCost() {
        return cupCost;
    }

    @Property(category = CAT_DETAILS, order = 5, viewable = true)
    public String getPlanNameDesc() {
        return planNameDesc;
    }

    @Override
    public String toString() {
        return CommonUtils.toString(planName);
    }

    @Override
    public Number getNodeCost() {
        return Long.parseLong(cost);
    }

    @Override
    public Number getNodePercent() {
        return null;
    }

    @Override
    public Number getNodeDuration() {
        return null;
    }

    @Override
    public Number getNodeRowCount() {
        return Long.parseLong(cardinality);
    }

    public long getColumn() {
        return level;
    }

    public void updateCosts() {
        if (nested != null) {
            for (DamengPlanNode child : nested) {
                child.updateCosts();
            }
        }
        if (Long.parseLong(cost) == 0 && nested != null) {
            for (DamengPlanNode child : nested) {
                this.cost += child.cost;
            }
        }
    }
    
}
