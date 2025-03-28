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
package org.jkiss.dbeaver.ext.damengdb.ui.config;

import org.jkiss.dbeaver.ext.damengdb.model.DamengTableColumn;
import org.jkiss.dbeaver.ext.damengdb.model.DamengTableConstraint;
import org.jkiss.dbeaver.ext.damengdb.model.DamengTableForeignKey;
import org.jkiss.dbeaver.ext.damengdb.model.DamengTableForeignKeyColumn;
import org.jkiss.dbeaver.ext.damengdb.ui.internal.DamengUIMessages;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEObjectConfigurator;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.rdb.DBSForeignKeyModifyRule;
import org.jkiss.dbeaver.ui.UITask;
import org.jkiss.dbeaver.ui.editors.object.struct.EditForeignKeyPage;

import java.util.Map;

/**
 * Dameng foreign key manager
 */
public class DamengForeignKeyConfigurator implements DBEObjectConfigurator<DamengTableForeignKey> {

    @Override
    public DamengTableForeignKey configureObject(DBRProgressMonitor monitor, DBECommandContext commandContext,
                                                 Object table, DamengTableForeignKey foreignKey, Map<String, Object> options) {
        return UITask.run(() -> {
            EditForeignKeyPage editPage = new EditForeignKeyPage(
                DamengUIMessages.edit_dameng_foreign_key_manager_dialog_title, foreignKey,
                new DBSForeignKeyModifyRule[] {DBSForeignKeyModifyRule.NO_ACTION, DBSForeignKeyModifyRule.CASCADE,
                    DBSForeignKeyModifyRule.RESTRICT, DBSForeignKeyModifyRule.SET_NULL,
                    DBSForeignKeyModifyRule.SET_DEFAULT},
                options);
            editPage.setSupportsCustomName(true);
            if (!editPage.edit()) {
                return null;
            }

            foreignKey.setReferencedConstraint((DamengTableConstraint) editPage.getUniqueConstraint());
            foreignKey.setName(editPage.getName());
            foreignKey.setDeleteRule(editPage.getOnDeleteRule());
            int colIndex = 1;
            for (EditForeignKeyPage.FKColumnInfo tableColumn : editPage.getColumns()) {
                foreignKey.addColumn(new DamengTableForeignKeyColumn(foreignKey,
                    (DamengTableColumn) tableColumn.getOwnColumn(), colIndex++));
            }
            return foreignKey;
        });
    }
    
}
