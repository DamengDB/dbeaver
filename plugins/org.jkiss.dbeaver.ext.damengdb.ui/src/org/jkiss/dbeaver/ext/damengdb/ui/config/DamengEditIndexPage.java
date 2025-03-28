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
package org.jkiss.dbeaver.ext.damengdb.ui.config;

import java.util.Collection;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.jkiss.dbeaver.ext.damengdb.ui.internal.DamengUIMessages;
import org.jkiss.dbeaver.model.struct.rdb.DBSIndexType;
import org.jkiss.dbeaver.model.struct.rdb.DBSTableIndex;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.editors.object.struct.EditIndexPage;

public class DamengEditIndexPage extends EditIndexPage {
	
    private boolean spatial = false;

    private boolean bitMap = false;

    private boolean clustered = false;

    public DamengEditIndexPage(String title, DBSTableIndex index, Collection<DBSIndexType> indexTypes) {
        super(title, index, indexTypes, true);
    }

    @Override
    protected void createContentsBeforeColumns(Composite panel) {
        super.createContentsBeforeColumns(panel);

        final Button clusteredButton = UIUtils.createLabelCheckbox(panel,
            DamengUIMessages.index_dialog_check_clustered_index, false);
        clusteredButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                clustered = clusteredButton.getSelection();
            }
        });

        final Button uniqueButton = (Button) panel.getChildren()[panel.getChildren().length - 3];

        final Combo typeCombo = (Combo) panel.getChildren()[3];

        typeCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (typeCombo.getSelectionIndex() == 0) {
                    uniqueButton.setEnabled(true);
                    clusteredButton.setEnabled(true);
                    spatial = false;
                    bitMap = false;
                } else {
                    uniqueButton.setEnabled(false);
                    uniqueButton.setSelection(false);
                    uniqueButton.notifyListeners(SWT.Selection, null);

                    clusteredButton.setEnabled(false);
                    clusteredButton.setSelection(false);
                    clusteredButton.notifyListeners(SWT.Selection, null);

                    if (typeCombo.getSelectionIndex() == 1) {
                        bitMap = true;
                        spatial = false;
                    } else if (typeCombo.getSelectionIndex() == 2) {
                        spatial = true;
                        bitMap = false;
                    }
                }
            }
        });

        clustered = clusteredButton.getSelection();
    }

    public boolean isSpatial() {
        return spatial;
    }

    public boolean isBitMap() {
        return bitMap;
    }

    public boolean isClustered() {
        return clustered;
    }
    
}
