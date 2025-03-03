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
package org.jkiss.dbeaver.model.ai;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.ai.completion.*;
import org.jkiss.dbeaver.model.ai.n.AIStreamingResponseHandler;
import org.jkiss.dbeaver.model.ai.n.AIUtils;
import org.jkiss.dbeaver.model.ai.n.CommandResult;
import org.jkiss.dbeaver.model.data.DBDObject;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.sql.SQLUtils;

import java.util.ArrayList;
import java.util.List;

public class AI {
    public static final AI INSTANCE = new AI();

    private final AIEngineRegistry engineRegistry = AIEngineRegistry.getInstance();

    private AI() {
    }

    public void chat(
        @NotNull DBRProgressMonitor monitor,
        DAICompletionContext context,
        DAICompletionSession session,
        AIStreamingResponseHandler handler
    ) {
        AIStreamingResponseHandler responseHandler = new AIStreamingResponseHandler() {
            private final StringBuilder response = new StringBuilder();

            @Override
            public void onNext(String partialResponse) {
                response.append(partialResponse);

                handler.onNext(partialResponse);
            }

            @Override
            public void onComplete() {
                session.add(new DAIChatMessage(DAIChatRole.ASSISTANT, response.toString()));

                handler.onComplete();
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
            }
        };

        try {
            getActiveEngine().chat(monitor, context, session.getMessages(), responseHandler);
        } catch (Exception e) {
            handler.onError(e);
        }
    }

    public void describe(
        @NotNull DBRProgressMonitor monitor,
        DAICompletionContext context,
        DBDObject toDescribe,
        AIStreamingResponseHandler handler
    ) {

    }

    public String translateTextToSql(@NotNull DBRProgressMonitor monitor, DAICompletionContext context, String text) throws DBException {
        String completion = getActiveEngine().translateTextToSql(monitor, context, text);

        String processedCompletion = AIUtils.processCompletion(
            monitor,
            context.getExecutionContext(),
            context.getScopeObject(),
            completion,
            context.getFormatter(),
            true
        );

        MessageChunk[] messageChunks = AITextUtils.splitIntoChunks(
            SQLUtils.getDialectFromDataSource(context.getExecutionContext().getDataSource()),
            processedCompletion
        );

        return AITextUtils.convertToSQL(
            new DAIChatMessage(DAIChatRole.USER, text),
            messageChunks,
            context.getExecutionContext().getDataSource()
        );
    }

    @NotNull
    public CommandResult command(
        @NotNull DBRProgressMonitor monitor, DAICompletionContext context, String text
    ) throws DBException {
        String completion = getActiveEngine().command(monitor, context, text);

        String processedCompletion = AIUtils.processCompletion(
            monitor,
            context.getExecutionContext(),
            context.getScopeObject(),
            completion,
            context.getFormatter(),
            true
        );

        MessageChunk[] messageChunks = AITextUtils.splitIntoChunks(
            SQLUtils.getDialectFromDataSource(context.getExecutionContext().getDataSource()),
            processedCompletion
        );

        String finalSQL = null;
        StringBuilder messages = new StringBuilder();
        for (MessageChunk chunk : messageChunks) {
            if (chunk instanceof MessageChunk.Code code) {
                finalSQL = code.text();
            } else if (chunk instanceof MessageChunk.Text textChunk) {
                messages.append(textChunk.text());
            }
        }

        return new CommandResult(finalSQL, messages.toString());
    }

    public boolean hasValidConfiguration() throws DBException {
        return getActiveEngine().hasValidConfiguration();
    }

    private DAICompletionEngine getActiveEngine() throws DBException {
        String activeEngine = AISettingsRegistry.getInstance().getSettings().getActiveEngine();
        return engineRegistry.getCompletionEngine(activeEngine);
    }

    @NotNull
    protected static List<DAIChatMessage> truncateMessages(
        boolean chatMode,
        @NotNull List<DAIChatMessage> messages,
        int maxTokens
    ) {
        final List<DAIChatMessage> pending = new ArrayList<>(messages);
        final List<DAIChatMessage> truncated = new ArrayList<>();
        int remainingTokens = maxTokens - 20; // Just to be sure

        if (!pending.isEmpty()) {
            if (pending.get(0).role() == DAIChatRole.SYSTEM) {
                // Always append main system message and leave space for the next one
                DAIChatMessage msg = pending.remove(0);
                DAIChatMessage truncatedMessage = truncateMessage(msg, remainingTokens - 50);
                remainingTokens -= countContentTokens(truncatedMessage.content());
                truncated.add(msg);
            }
        }

        for (DAIChatMessage message : pending) {
            final int messageTokens = message.content().length();

            if (remainingTokens < 0 || messageTokens > remainingTokens) {
                // Exclude old messages that don't fit into given number of tokens
                if (chatMode) {
                    break;
                } else {
                    // Truncate message itself
                }
            }

            DAIChatMessage truncatedMessage = truncateMessage(message, remainingTokens);
            remainingTokens -= countContentTokens(truncatedMessage.content());
            truncated.add(message);
        }

        return truncated;
    }

    /**
     * 1 token = 2 bytes
     * It is sooooo approximately
     * We should use https://github.com/knuddelsgmbh/jtokkit/ or something similar
     */
    protected static DAIChatMessage truncateMessage(DAIChatMessage message, int remainingTokens) {
        String content = message.content();
        int contentTokens = countContentTokens(content);

        if (contentTokens <= remainingTokens) {
            return message;
        }

        int tokensToRemove = contentTokens - remainingTokens;
        content = removeContentTokens(content, tokensToRemove);

        return new DAIChatMessage(message.role(), content);
    }


    protected static String removeContentTokens(String content, int tokensToRemove) {
        int charsToRemove = tokensToRemove * 2;
        if (charsToRemove >= content.length()) {
            return "";
        }
        return content.substring(0, content.length() - charsToRemove) + "..";
    }

    protected static int countContentTokens(String content) {
        return content.length() / 2;
    }
}
