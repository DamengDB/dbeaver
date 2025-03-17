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
import org.jkiss.dbeaver.HttpException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.ai.AIConstants;
import org.jkiss.dbeaver.model.ai.AIDescribeRequest;
import org.jkiss.dbeaver.model.ai.AISettingsRegistry;
import org.jkiss.dbeaver.model.ai.AIStreamingResponseHandler;
import org.jkiss.dbeaver.model.ai.completion.DAIChatMessage;
import org.jkiss.dbeaver.model.ai.completion.DAIChatRole;
import org.jkiss.dbeaver.model.ai.completion.DAICompletionContext;
import org.jkiss.dbeaver.model.ai.completion.DAICompletionEngine;
import org.jkiss.dbeaver.model.ai.utils.AIPrompt;
import org.jkiss.dbeaver.model.ai.utils.AIUtils;
import org.jkiss.dbeaver.model.ai.utils.DisposableLazyValue;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

public class OpenAICompletionEngine implements DAICompletionEngine {
    private static final Log log = Log.getLog(OpenAICompletionEngine.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

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
        return OpenAISettings.INSTANCE.model().getMaxTokens();
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
                DAIChatMessage.systemMessage(AIPrompt.SYSTEM_PROMPT),
                context.asSystemMessage(monitor, getContextWindowSize(monitor))
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
        @NotNull AIDescribeRequest describeRequest,
        @NotNull AIStreamingResponseHandler handler
    ) {

    }

    @NotNull
    @Override
    public String translateTextToSql(@NotNull DBRProgressMonitor monitor, @NotNull DAICompletionContext context, @NotNull String text) throws DBException {
        List<DAIChatMessage> chatMessages = Stream.of(
            DAIChatMessage.systemMessage(AIPrompt.SYSTEM_PROMPT),
            context.asSystemMessage(monitor, getContextWindowSize(monitor)),
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
            DAIChatMessage.systemMessage(AIPrompt.SYSTEM_PROMPT),
            context.asSystemMessage(monitor, getContextWindowSize(monitor)),
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
        List<DAIChatMessage> truncatedMessages = AIUtils.truncateMessages(true, messages, getContextWindowSize(monitor));
        ChatCompletionRequest request = ChatCompletionRequest.builder()
            .messages(fromMessages(truncatedMessages))
            .temperature(OpenAISettings.INSTANCE.temperature())
            .frequencyPenalty(0.0)
            .presencePenalty(0.0)
            .maxTokens(maxTokens)
            .n(1)
            .model(OpenAISettings.INSTANCE.model().getName())
            .build();

        return openAiService.evaluate().createChatCompletion(monitor, request);
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
            public ChatCompletionResult createChatCompletion(
                @NotNull DBRProgressMonitor monitor,
                ChatCompletionRequest request
            ) throws HttpException {
                try {
                    return aiService.createChatCompletion(request);
                } catch (retrofit2.HttpException e) {
                    throw new HttpException("Error executing OpenAI request", e);
                }
            }

            @Override
            public void close() {
                aiService.shutdownExecutor();
            }
        };
    }
}
