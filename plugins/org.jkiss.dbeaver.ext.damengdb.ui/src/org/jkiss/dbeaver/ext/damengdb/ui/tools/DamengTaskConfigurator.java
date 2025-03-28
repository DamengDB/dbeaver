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
package org.jkiss.dbeaver.ext.damengdb.ui.tools;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.ext.damengdb.DamengDataSourceProvider;
import org.jkiss.dbeaver.ext.damengdb.tasks.DamengScriptExecuteSettings;
import org.jkiss.dbeaver.ext.damengdb.tasks.DamengTasks;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.runtime.DBRRunnableContext;
import org.jkiss.dbeaver.model.task.DBTTask;
import org.jkiss.dbeaver.model.task.DBTTaskType;
import org.jkiss.dbeaver.tasks.ui.DBTTaskConfigPanelProvider;
import org.jkiss.dbeaver.tasks.ui.DBTTaskConfigurator;
import org.jkiss.dbeaver.tasks.ui.nativetool.NativeToolConfigPanel;
import org.jkiss.dbeaver.tasks.ui.wizard.TaskConfigurationWizard;

/**
 * Dameng task configurator
 */
public class DamengTaskConfigurator implements DBTTaskConfigurator, DBTTaskConfigPanelProvider {
    
	@Override
    public ConfigPanel createInputConfigurator(DBRRunnableContext runnableContext, @NotNull DBTTaskType taskType) {
        return new ConfigPanel(runnableContext, taskType);
    }

    @Override
    public TaskConfigurationWizard<DamengScriptExecuteSettings> createTaskConfigWizard(
        @NotNull DBTTask taskConfiguration) {
        switch (taskConfiguration.getType().getId()) {
            case DamengTasks.TASK_SCRIPT_EXECUTE:
                return new DamengScriptExecuteWizard(taskConfiguration);
        }
        return null;
    }

    private static class ConfigPanel extends NativeToolConfigPanel<DBPDataSourceContainer> {
        ConfigPanel(DBRRunnableContext runnableContext, DBTTaskType taskType) {
            super(runnableContext, taskType, DBPDataSourceContainer.class, DamengDataSourceProvider.class);
        }
    }
    
}
