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

import java.util.Arrays;
import java.util.Map;

import org.jkiss.dbeaver.ext.damengdb.model.DamengTableColumn;
import org.jkiss.dbeaver.ext.damengdb.model.DamengTableIndex;
import org.jkiss.dbeaver.ext.damengdb.model.DamengTableIndexColumn;
import org.jkiss.dbeaver.ext.damengdb.ui.internal.DamengUIMessages;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEObjectConfigurator;
import org.jkiss.dbeaver.model.impl.DBObjectNameCaseTransformer;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSEntityAttribute;
import org.jkiss.dbeaver.ui.UITask;
import org.jkiss.dbeaver.ui.editors.object.struct.EditIndexPage;
import org.jkiss.utils.CommonUtils;

/**
 * Dameng index manager
 */
public class DamengIndexConfigurator implements DBEObjectConfigurator<DamengTableIndex> {
    
	@Override
    public DamengTableIndex configureObject(DBRProgressMonitor monitor, DBECommandContext commandContext,
                                            Object container, DamengTableIndex index, Map<String, Object> options) {
        return UITask.run(() -> {
            DamengEditIndexPage editPage = new DamengEditIndexPage(
                DamengUIMessages.edit_dameng_index_manager_dialog_title, index,
                Arrays.asList(DamengDBSIndexType.BTREE_INDEX, DamengDBSIndexType.BITMAP_INDEX,
                    DamengDBSIndexType.SPATIAL_INDEX));
            if (!editPage.edit()) {
                return null;
            }

            StringBuilder idxName = new StringBuilder(64);
            idxName.append(CommonUtils.escapeIdentifier(index.getTable().getName())).append("_")
                .append(CommonUtils.escapeIdentifier(editPage.getSelectedAttributes().iterator().next().getName()))
                .append("_IDX");
            index.setName(DBObjectNameCaseTransformer.transformName(index.getDataSource(), idxName.toString()));
            index.setUnique(editPage.isUnique());
            index.setClustered(editPage.isClustered());
            index.setSpatial(editPage.isSpatial());
            index.setBitMap(editPage.isBitMap());
            index.setIndexType(editPage.getIndexType());
            int colIndex = 1;
            for (DBSEntityAttribute tableColumn : editPage.getSelectedAttributes()) {
                index.addColumn(new DamengTableIndexColumn(index, (DamengTableColumn) tableColumn, colIndex++,
                    !Boolean.TRUE.equals(editPage.getAttributeProperty(tableColumn, EditIndexPage.PROP_DESC))));
            }
            return index;
        });
    }
	
}
