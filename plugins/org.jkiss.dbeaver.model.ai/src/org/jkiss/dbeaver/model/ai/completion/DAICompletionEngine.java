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

package org.jkiss.dbeaver.model.ai.completion;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.ai.AISettingsEventListener;
import org.jkiss.dbeaver.model.ai.n.AIStreamingResponseHandler;
import org.jkiss.dbeaver.model.data.DBDObject;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

import java.util.List;

/**
 * Completion engine
 */
public interface DAICompletionEngine extends AISettingsEventListener {

    /**
     * Completion engine name
     */
    @NotNull
    String getEngineName();

    int getContextWindowSize(@NotNull DBRProgressMonitor monitor);

    void chat(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAICompletionContext context,
        @NotNull List<DAIChatMessage> messages,
        @NotNull AIStreamingResponseHandler handler
    );

    void describe(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAICompletionContext context,
        @NotNull DBDObject toDescribe,
        @NotNull AIStreamingResponseHandler handler
    );

    String translateTextToSql(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAICompletionContext context,
        @NotNull String text
    ) throws DBException;

    @NotNull
    String command(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAICompletionContext context,
        @NotNull String text
    ) throws DBException;

    boolean hasValidConfiguration();
}
