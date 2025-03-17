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
import org.jkiss.dbeaver.model.ai.AIDescribeRequest;
import org.jkiss.dbeaver.model.ai.AISettingsEventListener;
import org.jkiss.dbeaver.model.ai.AIStreamingResponseHandler;
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

    /**
     * Returns the context window size for the completion engine.
     *
     * @param monitor progress monitor
     * @return the context window size
     */
    int getContextWindowSize(@NotNull DBRProgressMonitor monitor);

    /**
     * Sends a chat request to the completion engine.
     *
     * @param monitor  progress monitor
     * @param context  completion context
     * @param messages chat messages
     * @param handler  response handler
     */
    void chat(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAICompletionContext context,
        @NotNull List<DAIChatMessage> messages,
        @NotNull AIStreamingResponseHandler handler
    );

    /**
     * Sends a describe request to the completion engine.
     *
     * @param monitor         progress monitor
     * @param context         completion context
     * @param describeRequest describe request
     * @param handler         response handler
     */
    void describe(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAICompletionContext context,
        @NotNull AIDescribeRequest describeRequest,
        @NotNull AIStreamingResponseHandler handler
    );

    /**
     * Translates text to SQL.
     *
     * @param monitor progress monitor
     * @param context completion context
     * @param text    text to translate
     * @return translated SQL
     */
    @NotNull
    String translateTextToSql(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAICompletionContext context,
        @NotNull String text
    ) throws DBException;

    /**
     * Translates a user command to SQL.
     *
     * @param monitor progress monitor
     * @param context completion context
     * @param text    command text
     * @return command response
     */
    @NotNull
    String command(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAICompletionContext context,
        @NotNull String text
    ) throws DBException;

    /**
     * Checks if the completion engine has a valid configuration.
     *
     * @return true if the completion engine has a valid configuration
     */
    boolean hasValidConfiguration();
}
