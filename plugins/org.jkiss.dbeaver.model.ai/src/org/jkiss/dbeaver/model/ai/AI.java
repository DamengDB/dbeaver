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

import java.util.List;
import java.util.concurrent.Flow;
import java.util.stream.Stream;

public class AI {
    public static final AI INSTANCE = new AI();

    private static final int MAX_RETRIES = 3;
    private static final String SYSTEM_PROMPT = """
        You are SQL assistant. You must produce SQL code for given prompt.
        You must produce valid SQL statement enclosed with Markdown code block and terminated with semicolon.
        All database object names should be properly escaped according to the SQL dialect.
        All comments MUST be placed before query outside markdown code block.
        Be polite.
        """;

    private final AIEngineRegistry engineRegistry = AIEngineRegistry.getInstance();

    private AI() {
    }

    /**
     * Chat with the AI assistant.
     *
     * @param monitor the progress monitor
     * @param chatCompletionRequest the chat completion request
     * @throws DBException if an error occurs
     */
    public Flow.Publisher<DAICompletionChunk> chat(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAIChatRequest chatCompletionRequest
    ) throws DBException {
        DAICompletionEngine activeEngine = getActiveEngine();

        List<DAIChatMessage> chatMessages = Stream.concat(
            Stream.of(
                DAIChatMessage.systemMessage(SYSTEM_PROMPT),
                chatCompletionRequest.context().asSystemMessage(monitor, activeEngine.getContextWindowSize(monitor))
            ),
            chatCompletionRequest.session().getMessages().stream()
        ).toList();

        List<DAIChatMessage> truncatedMessages = AIUtils.truncateMessages(
            true,
            chatMessages,
            activeEngine.getContextWindowSize(monitor)
        );

        return callWithRetry(() -> activeEngine.chatStream(
            monitor,
            new DAICompletionRequest(
                truncatedMessages
            )
        ));
    }

    /**
     * Translate the specified text to SQL.
     *
     * @param monitor the progress monitor
     * @param request the translate request
     * @return the translated SQL
     * @throws DBException if an error occurs
     */
    @NotNull
    public String translateTextToSql(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAITranslateRequest request
    ) throws DBException {
        DAICompletionEngine activeEngine = getActiveEngine();
        DAIChatMessage userMessage = new DAIChatMessage(DAIChatRole.USER, request.text());

        List<DAIChatMessage> chatMessages = List.of(
            DAIChatMessage.systemMessage(SYSTEM_PROMPT),
            request.context().asSystemMessage(monitor, activeEngine.getContextWindowSize(monitor)),
            userMessage
        );

        DAICompletionRequest completionRequest = new DAICompletionRequest(
            AIUtils.truncateMessages(true, chatMessages, activeEngine.getContextWindowSize(monitor))
        );

        DAICompletionResponse completionResponse = callWithRetry(() -> activeEngine.chat(
            monitor,
            completionRequest
        ));

        MessageChunk[] messageChunks = processAndSplitCompletion(
            monitor,
            request.context(),
            completionResponse.text()
        );

        return AITextUtils.convertToSQL(
            userMessage,
            messageChunks,
            request.context().getExecutionContext().getDataSource()
        );
    }

    /**
     * Translate the specified user command to SQL.
     *
     * @param monitor the progress monitor
     * @param request the command request
     * @return the command result
     * @throws DBException if an error occurs
     */
    @NotNull
    public CommandResult command(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAICommandRequest request
    ) throws DBException {
        DAICompletionEngine activeEngine = getActiveEngine();

        List<DAIChatMessage> chatMessages = List.of(
            DAIChatMessage.systemMessage(SYSTEM_PROMPT),
            request.context().asSystemMessage(monitor, activeEngine.getContextWindowSize(monitor)),
            DAIChatMessage.userMessage(request.text())
        );

        DAICompletionRequest completionRequest = new DAICompletionRequest(
            AIUtils.truncateMessages(true, chatMessages, activeEngine.getContextWindowSize(monitor))
        );

        DAICompletionResponse completionResponse = callWithRetry(() -> activeEngine.chat(
            monitor,
            completionRequest
        ));

        MessageChunk[] messageChunks = processAndSplitCompletion(monitor, request.context(), completionResponse.text());

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
        String processedCompletion = callWithRetry(() -> AIUtils.processCompletion(
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

    private static <T> T callWithRetry(ThrowableSupplier<T, DBException> supplier) throws DBException {
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
}
