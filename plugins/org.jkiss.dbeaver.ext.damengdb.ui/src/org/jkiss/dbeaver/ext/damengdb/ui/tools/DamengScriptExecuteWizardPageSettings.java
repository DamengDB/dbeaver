/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp and others
 * Copyright (C) 2011-2012 Eugene Fradkin (eugene.fradkin@gmail.com)
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
package org.jkiss.dbeaver.ext.damengdb.ui.tools;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.jkiss.dbeaver.ext.damengdb.tasks.DamengScriptExecuteSettings;
import org.jkiss.dbeaver.ext.damengdb.ui.internal.DamengUIMessages;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.tasks.ui.nativetool.AbstractNativeToolWizardPage;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.controls.TextWithOpenFile;
import org.jkiss.dbeaver.utils.GeneralUtils;

class DamengScriptExecuteWizardPageSettings extends AbstractNativeToolWizardPage<DamengScriptExecuteWizard> {
    
	private TextWithOpenFile inputFileText;

    private Combo consoleEncodingCombo;

    DamengScriptExecuteWizardPageSettings(DamengScriptExecuteWizard wizard) {
        super(wizard, DamengUIMessages.tools_script_execute_wizard_page_settings_page_name);
        setTitle(DamengUIMessages.tools_script_execute_wizard_page_settings_page_name);
        setDescription(DamengUIMessages.tools_script_execute_wizard_page_settings_page_description);
    }

    @Override
    protected boolean determinePageCompletion() {
        if (wizard.getSettings().getInputFile() == null) {
            setErrorMessage("Input file not specified");
            return false;
        }
        return super.determinePageCompletion();
    }

    @Override
    public void createControl(Composite parent) {
        Composite composite = UIUtils.createPlaceholder(parent, 1);

        Group outputGroup = UIUtils.createControlGroup(composite,
            DamengUIMessages.tools_script_execute_wizard_page_settings_group_input, 3, GridData.FILL_HORIZONTAL, 0);
        outputGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        inputFileText = new TextWithOpenFile(outputGroup,
            DamengUIMessages.tools_script_execute_wizard_page_settings_label_input_file,
            new String[] {"*.sql", "*.txt", "*"});
        inputFileText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        Group consoleEncodingGroup = UIUtils.createControlGroup(composite,
            DamengUIMessages.task_script_execute_encoding, 1, GridData.FILL_HORIZONTAL, 0);
        consoleEncodingGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        consoleEncodingCombo = new Combo(consoleEncodingGroup, SWT.READ_ONLY);
        consoleEncodingCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        consoleEncodingCombo.add("GBK");
        consoleEncodingCombo.add("UTF-8");
        consoleEncodingCombo.select(1);
        setControl(composite);

        // updateState();
    }

    @Override
    public void activatePage() {
        if (wizard.getSettings().getInputFile() != null) {
            inputFileText.setText(wizard.getSettings().getInputFile());
        }

        if (((DamengScriptExecuteSettings) wizard.getSettings()).getConsoleEncoding() != null) {
            consoleEncodingCombo.setText(((DamengScriptExecuteSettings) wizard.getSettings()).getConsoleEncoding());
        } else {
            consoleEncodingCombo.setText(GeneralUtils.getDefaultConsoleEncoding());
        }

        updateState();
    }

    @Override
    public void deactivatePage() {
        super.deactivatePage();
        saveState();
    }

    @Override
    public void saveState() {
        super.saveState();

        DamengScriptExecuteSettings settings = wizard.getSettings();
        List<DBSObject> selectedConnections = settings.getDatabaseObjects();
        settings.setDataSourceContainer(
            selectedConnections.isEmpty() ? null : selectedConnections.get(0).getDataSource().getContainer());
        settings.setInputFile(inputFileText.getText());
        if (consoleEncodingCombo.getText() != null) {
            settings.setConsoleEncoding(consoleEncodingCombo.getText());
        }
    }

    @Override
    protected void updateState() {
        saveState();

        getContainer().updateButtons();
    }
    
}
