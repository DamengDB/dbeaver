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

package org.jkiss.dbeaver.model.task;

/**
 * Task event.
 * Fired whenever task is create/deleted/updated.
 * Every task run also triggers UPDATE event.
 */
public class DBTTaskEvent {

    public enum Action
    {
        TASK_ADD,
        TASK_UPDATE,
        TASK_REMOVE,
        TASK_EXECUTE,
        TASK_ACTIVATE,
    }

    private final DBTTask task;
    private final Action action;

    public DBTTaskEvent(DBTTask task, Action action) {
        this.task = task;
        this.action = action;
    }

    public Action getAction()
    {
        return action;
    }

    public DBTTask getTask() {
        return task;
    }

    @Override
    public String toString() {
        return action + " " + task;
    }

}
