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
package org.jkiss.dbeaver.model.ai.openai;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.ai.AIConstants;
import org.jkiss.dbeaver.model.ai.AISettingsRegistry;
import org.jkiss.dbeaver.model.ai.completion.DAIChatMessage;
import org.jkiss.dbeaver.model.ai.completion.DAIChatRole;
import org.jkiss.dbeaver.model.ai.completion.DAICompletionContext;
import org.jkiss.dbeaver.model.ai.completion.DAICompletionEngine;
import org.jkiss.dbeaver.model.ai.n.AIStreamingResponseHandler;
import org.jkiss.dbeaver.model.ai.n.DisposableLazyValue;
import org.jkiss.dbeaver.model.data.DBDObject;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import retrofit2.HttpException;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

public class OpenAICompletionEngine implements DAICompletionEngine {
    private static final Log log = Log.getLog(OpenAICompletionEngine.class);

    private static final int MAX_RETRIES = 3;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String SYSTEM_PROMPT = """
        You are SQL assistant. You must produce SQL code for given prompt.
        You must produce valid SQL statement enclosed with Markdown code block and terminated with semicolon.
        All database object names should be properly escaped according to the SQL dialect.
        All comments MUST be placed before query outside markdown code block.
        Be polite.
        """;

    private final DisposableLazyValue<OpenAIClient, DBException> openAiService = new DisposableLazyValue<>() {
        @Override
        protected OpenAIClient initialize() {
            return createClient();
        }

        @Override
        protected void onDispose(OpenAIClient disposedValue) throws DBException {
            disposedValue.close();
        }
    };

    @Override
    public @NotNull String getEngineName() {
        return "OpenAI GPT";
    }

    @Override
    public int getContextWindowSize(@NotNull DBRProgressMonitor monitor) {
        return getMaxTokens(monitor);
    }

    @Override
    public void chat(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAICompletionContext context,
        @NotNull List<DAIChatMessage> messages,
        @NotNull AIStreamingResponseHandler handler
    ) {
        try {
            doChat(monitor, context, messages, handler);
        } catch (Exception e) {
            handler.onError(e);
        }
    }

    @Override
    public void onSettingsUpdate(AISettingsRegistry registry) {
        try {
            openAiService.dispose();
        } catch (DBException e) {
            log.error("Error disposing OpenAI service", e);
        }
    }

    private void doChat(
        @NotNull DBRProgressMonitor monitor,
        DAICompletionContext context,
        List<DAIChatMessage> messages,
        AIStreamingResponseHandler handler
    ) throws DBException {
        List<DAIChatMessage> chatMessages = Stream.concat(
            Stream.of(
                DAIChatMessage.systemMessage(SYSTEM_PROMPT),
                context.asSystemMessage(monitor, getMaxTokens(monitor))
            ),
            messages.stream()
        ).toList();

        ChatCompletionResult completionResult = complete(
            monitor,
            chatMessages,
            AIConstants.MAX_RESPONSE_TOKENS
        );

        handler.onNext(completionResult.getChoices().get(0).getMessage().getContent());
        handler.onComplete();
    }

    @Override
    public void describe(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAICompletionContext context,
        @NotNull DBDObject toDescribe,
        @NotNull AIStreamingResponseHandler handler
    ) {

    }

    @Override
    public String translateTextToSql(@NotNull DBRProgressMonitor monitor, @NotNull DAICompletionContext context, @NotNull String text) throws DBException {
        List<DAIChatMessage> chatMessages = Stream.of(
            DAIChatMessage.systemMessage(SYSTEM_PROMPT),
            context.asSystemMessage(monitor, OpenAISettings.INSTANCE.model().getMaxTokens()),
            new DAIChatMessage(DAIChatRole.USER, text)
        ).toList();

        ChatCompletionResult completionResult = complete(
            monitor,
            chatMessages,
            AIConstants.MAX_RESPONSE_TOKENS
        );

        return completionResult.getChoices().get(0).getMessage().getContent();
    }

    @NotNull
    @Override
    public String command(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAICompletionContext context,
        @NotNull String text
    ) throws DBException {
        List<DAIChatMessage> chatMessages = Stream.of(
            DAIChatMessage.systemMessage(SYSTEM_PROMPT),
            context.asSystemMessage(monitor, OpenAISettings.INSTANCE.model().getMaxTokens()),
            new DAIChatMessage(DAIChatRole.USER, text)
        ).toList();

        ChatCompletionResult completionResult = complete(
            monitor,
            chatMessages,
            AIConstants.MAX_RESPONSE_TOKENS
        );

        return completionResult.getChoices().get(0).getMessage().getContent();
    }

    @Override
    public boolean hasValidConfiguration() {
        return OpenAISettings.INSTANCE.isValidConfiguration();
    }

    @NotNull
    protected ChatCompletionResult complete(
        @NotNull DBRProgressMonitor monitor,
        List<DAIChatMessage> messages,
        int maxTokens
    ) throws DBException {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
            .messages(fromMessages(messages))
            .temperature(OpenAISettings.INSTANCE.temperature())
            .frequencyPenalty(0.0)
            .presencePenalty(0.0)
            .maxTokens(maxTokens)
            .n(1)
            .model(OpenAISettings.INSTANCE.model().getName())
            .build();

        int retry = 0;
        Exception lastError = null;
        while (retry < MAX_RETRIES && !monitor.isCanceled()) {
            try {
                retry++;
                return openAiService.evaluate().createChatCompletion(monitor, request);
            } catch (HttpException e) {
                if (e.code() == 429) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    lastError = e;
                    break;
                }
            } catch (Exception e) {
                lastError = e;
                break;
            }
        }

        throw new DBException("Error completing chat", lastError);
    }

    private static List<ChatMessage> fromMessages(List<DAIChatMessage> messages) {
        return messages.stream()
            .map(m -> new ChatMessage(mapRole(m.role()), m.content()))
            .toList();
    }

    private static String mapRole(DAIChatRole role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }

    protected OpenAIClient createClient() {
        OpenAiService aiService = new OpenAiService(OpenAISettings.INSTANCE.token(), TIMEOUT);

        return new OpenAIClient() {
            @NotNull
            @Override
            public ChatCompletionResult createChatCompletion(@NotNull DBRProgressMonitor monitor, ChatCompletionRequest request) {
                return aiService.createChatCompletion(request);
            }

            @Override
            public void close() {
                aiService.shutdownExecutor();
            }
        };
    }

    protected int getMaxTokens(@NotNull DBRProgressMonitor monitor) {
        return OpenAISettings.INSTANCE.model().getMaxTokens();
    }
}
