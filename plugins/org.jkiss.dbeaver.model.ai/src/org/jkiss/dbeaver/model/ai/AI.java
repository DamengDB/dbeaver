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
import org.jkiss.dbeaver.HttpException;
import org.jkiss.dbeaver.model.ai.completion.*;
import org.jkiss.dbeaver.model.ai.utils.AIUtils;
import org.jkiss.dbeaver.model.ai.utils.ThrowableSupplier;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.sql.SQLUtils;

public class AI {
    public static final AI INSTANCE = new AI();
    private static final int MAX_RETRIES = 3;

    private final AIEngineRegistry engineRegistry = AIEngineRegistry.getInstance();

    private AI() {
    }

    /**
     * Chat with the AI assistant.
     *
     * @param monitor the progress monitor
     * @param context the completion context
     * @param session the completion session
     * @param handler the response handler
     */
    public void chat(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAICompletionContext context,
        @NotNull DAICompletionSession session,
        @NotNull AIStreamingResponseHandler handler
    ) {
        try {
            DAICompletionEngine activeEngine = getActiveEngine();
            AIStreamingResponseHandler chatHandler = new AIStreamingResponseHandler() {
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

            executeWithRetry(
                monitor,
                context,
                h -> activeEngine.chat(monitor, context, session.getMessages(), h),
                chatHandler,
                0
            );
        } catch (DBException e) {
            handler.onError(e);
        }
    }

    /**
     * Describe the specified object.
     *
     * @param monitor         the progress monitor
     * @param context         the completion context
     * @param describeRequest the describe request
     * @param handler         the response handler
     */
    public void describe(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAICompletionContext context,
        @NotNull AIDescribeRequest describeRequest,
        @NotNull AIStreamingResponseHandler handler
    ) {
        try {
            DAICompletionEngine activeEngine = getActiveEngine();
            executeWithRetry(
                monitor,
                context,
                h -> activeEngine.describe(monitor, context, describeRequest, h),
                handler,
                0
            );
        } catch (DBException e) {
            handler.onError(e);
        }
    }

    /**
     * Translate the specified text to SQL.
     *
     * @param monitor the progress monitor
     * @param context the completion context
     * @param text    the text to translate
     * @return the translated SQL
     * @throws DBException if an error occurs
     */
    @NotNull
    public String translateTextToSql(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAICompletionContext context,
        @NotNull String text
    ) throws DBException {
        DAICompletionEngine activeEngine = getActiveEngine();
        String completion = activeEngine.translateTextToSql(monitor, context, text);
        MessageChunk[] messageChunks = processAndSplitCompletion(monitor, context, completion);

        return AITextUtils.convertToSQL(
            new DAIChatMessage(DAIChatRole.USER, text),
            messageChunks,
            context.getExecutionContext().getDataSource()
        );
    }

    /**
     * Translate the specified user command to SQL.
     *
     * @param monitor the progress monitor
     * @param context the completion context
     * @param text    the command text
     * @return the command result
     * @throws DBException if an error occurs
     */
    @NotNull
    public CommandResult command(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAICompletionContext context,
        @NotNull String text
    ) throws DBException {
        DAICompletionEngine activeEngine = getActiveEngine();
        String completion = activeEngine.command(monitor, context, text);
        MessageChunk[] messageChunks = processAndSplitCompletion(monitor, context, completion);

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

    /**
     * Check if the AI assistant has valid configuration.
     *
     * @return true if the AI assistant has valid configuration, false otherwise
     * @throws DBException if an error occurs
     */
    public boolean hasValidConfiguration() throws DBException {
        return getActiveEngine().hasValidConfiguration();
    }

    private MessageChunk[] processAndSplitCompletion(
        DBRProgressMonitor monitor,
        DAICompletionContext context,
        String completion
    ) throws DBException {
        String processedCompletion = ruwWithRetries(() -> AIUtils.processCompletion(
            monitor,
            context.getExecutionContext(),
            context.getScopeObject(),
            completion,
            context.getFormatter(),
            true
        ));
        return AITextUtils.splitIntoChunks(
            SQLUtils.getDialectFromDataSource(context.getExecutionContext().getDataSource()),
            processedCompletion
        );
    }

    private void executeWithRetry(
        @NotNull DBRProgressMonitor monitor,
        DAICompletionContext context,
        AIRequestExecutor executor,
        AIStreamingResponseHandler handler,
        int retryCount
    ) {
        executor.execute(new AIStreamingResponseHandler() {
            @Override
            public void onNext(String partialResponse) {
                handler.onNext(partialResponse);
            }

            @Override
            public void onComplete() {
                handler.onComplete();
            }

            @Override
            public void onError(Throwable error) {
                if (error instanceof HttpException && ((HttpException) error).statusCode() == 429 && retryCount < MAX_RETRIES) {
                    executeWithRetry(monitor, context, executor, handler, retryCount + 1);
                } else {
                    handler.onError(error);
                }
            }
        });
    }

    private static <T> T ruwWithRetries(ThrowableSupplier<T, DBException> supplier) throws DBException {
        int retry = 0;
        while (retry < MAX_RETRIES) {
            try {
                return supplier.get();
            } catch (HttpException e) {
                if (e.statusCode() == 429) {
                    retry++;
                } else {
                    throw e;
                }
            }
        }
        throw new DBException("Request failed after " + MAX_RETRIES + " attempts");
    }

    private DAICompletionEngine getActiveEngine() throws DBException {
        String activeEngine = AISettingsRegistry.getInstance().getSettings().getActiveEngine();
        return engineRegistry.getCompletionEngine(activeEngine);
    }

    @FunctionalInterface
    private interface AIRequestExecutor {
        void execute(AIStreamingResponseHandler handler);
    }
}
