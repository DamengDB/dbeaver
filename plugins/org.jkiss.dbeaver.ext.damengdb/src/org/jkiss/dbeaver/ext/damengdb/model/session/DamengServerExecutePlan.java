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
package org.jkiss.dbeaver.ext.damengdb.model.session;

import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.DBPObjectWithDescription;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Property;

import java.sql.ResultSet;

/**
 * Plan
 */
public class DamengServerExecutePlan implements DBPObjectWithDescription {
	
    private String plan;

    public DamengServerExecutePlan(ResultSet dbResult) {
        this.plan = JDBCUtils.safeGetString(dbResult, "PLAN_TABLE_OUTPUT");
    }

    public DamengServerExecutePlan(String plan) {
        this.plan = plan;
    }

    @Property(viewable = true, order = 1)
    public String getPlan() {
        return plan;
    }

    @Nullable
    @Override
    public String getDescription() {
        return plan;
    }
    
}
