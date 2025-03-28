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
package org.jkiss.dbeaver.ext.damengdb.ui.tools;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbench;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.ext.damengdb.model.DamengDataSource;
import org.jkiss.dbeaver.ext.damengdb.tasks.DamengScriptExecuteSettings;
import org.jkiss.dbeaver.ext.damengdb.tasks.DamengTasks;
import org.jkiss.dbeaver.ext.damengdb.ui.internal.DamengUIMessages;
import org.jkiss.dbeaver.model.runtime.DBRRunnableContext;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.task.DBTTask;
import org.jkiss.dbeaver.registry.task.TaskPreferenceStore;
import org.jkiss.dbeaver.tasks.ui.nativetool.AbstractNativeScriptExecuteWizard;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

class DamengScriptExecuteWizard
    extends AbstractNativeScriptExecuteWizard<DamengScriptExecuteSettings, DBSObject, DamengDataSource> {
    
	private DamengScriptExecuteWizardPageSettings mainPage;

    DamengScriptExecuteWizard(DBTTask task) {
        super(task);
    }

    DamengScriptExecuteWizard(DamengDataSource dmSchema) {
        super(Collections.singleton(dmSchema), DamengUIMessages.tools_script_execute_wizard_page_name);
    }

    DamengScriptExecuteWizard(@NotNull DamengDataSource dmSchema, @Nullable Path sourceFile) {
        super(Collections.singleton(dmSchema), DamengUIMessages.tools_script_execute_wizard_page_name, sourceFile);
    }

    @Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
        super.init(workbench, selection);

        this.mainPage = new DamengScriptExecuteWizardPageSettings(this);
    }

    @Override
    public String getTaskTypeId() {
        return DamengTasks.TASK_SCRIPT_EXECUTE;
    }

    @Override
    public void saveTaskState(DBRRunnableContext runnableContext, DBTTask task, Map<String, Object> state) {
        mainPage.saveState();

        getSettings().saveSettings(runnableContext, new TaskPreferenceStore(state));
    }

    @Override
    protected DamengScriptExecuteSettings createSettings() {
        return new DamengScriptExecuteSettings();
    }

    @Override
    public void addPages() {
        addTaskConfigPages();
        addPage(mainPage);
        super.addPages();
    }
    
}
