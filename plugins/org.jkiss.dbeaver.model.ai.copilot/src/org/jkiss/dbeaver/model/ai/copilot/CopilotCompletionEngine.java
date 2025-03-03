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
package org.jkiss.dbeaver.model.ai.copilot;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.ai.AISettingsRegistry;
import org.jkiss.dbeaver.model.ai.completion.DAIChatMessage;
import org.jkiss.dbeaver.model.ai.completion.DAIChatRole;
import org.jkiss.dbeaver.model.ai.completion.DAICompletionContext;
import org.jkiss.dbeaver.model.ai.completion.DAICompletionEngine;
import org.jkiss.dbeaver.model.ai.copilot.dto.CopilotChatRequest;
import org.jkiss.dbeaver.model.ai.copilot.dto.CopilotChatResponse;
import org.jkiss.dbeaver.model.ai.copilot.dto.CopilotMessage;
import org.jkiss.dbeaver.model.ai.copilot.dto.CopilotSessionToken;
import org.jkiss.dbeaver.model.ai.n.AIStreamingResponseHandler;
import org.jkiss.dbeaver.model.ai.n.DisposableLazyValue;
import org.jkiss.dbeaver.model.ai.openai.OpenAIModel;
import org.jkiss.dbeaver.model.data.DBDObject;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

import java.util.ArrayList;
import java.util.List;

public class CopilotCompletionEngine implements DAICompletionEngine {
    private static final Log log = Log.getLog(CopilotCompletionEngine.class);

    private static final String SYSTEM_PROMPT = """
        You are SQL assistant. You must produce SQL code for given prompt.
        You must produce valid SQL statement enclosed with Markdown code block and terminated with semicolon.
        All database object names should be properly escaped according to the SQL dialect.
        All comments MUST be placed before query outside markdown code block.
        Be polite.
        """;

    private final DisposableLazyValue<CopilotClient, DBException> client = new DisposableLazyValue<>() {
        @Override
        protected CopilotClient initialize() throws DBException {
            return new CopilotClient();
        }

        @Override
        protected void onDispose(CopilotClient disposedValue) throws DBException {
            disposedValue.close();
        }
    };

    private volatile CopilotSessionToken sessionToken;

    @Override
    public @NotNull String getEngineName() {
        return CopilotConstants.COPILOT_ENGINE;
    }

    @Override
    public int getContextWindowSize(@NotNull DBRProgressMonitor monitor) {
        return OpenAIModel.getByName(CopilotSettings.INSTANCE.modelName()).getMaxTokens();
    }

    @Override
    public void chat(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAICompletionContext context,
        @NotNull List<DAIChatMessage> messages,
        @NotNull AIStreamingResponseHandler handler
    ) {
        try {
            List<DAIChatMessage> chatMessages = new ArrayList<>();
            chatMessages.add(new DAIChatMessage(DAIChatRole.SYSTEM, SYSTEM_PROMPT));
            chatMessages.add(context.asSystemMessage(monitor, getContextWindowSize(monitor)));
            chatMessages.addAll(messages);

            CopilotChatResponse chatResponse = requestChatCompletion(monitor, chatMessages);

            String completion = chatResponse.choices().stream()
                .findFirst().orElseThrow()
                .message()
                .content();

            handler.onNext(completion);
            handler.onComplete();
        } catch (Exception e) {
            handler.onError(e);
        }
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
    public String translateTextToSql(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAICompletionContext context,
        @NotNull String text
    ) throws DBException {
        List<DAIChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new DAIChatMessage(DAIChatRole.SYSTEM, SYSTEM_PROMPT));
        chatMessages.add(context.asSystemMessage(monitor, getContextWindowSize(monitor)));
        chatMessages.add(new DAIChatMessage(DAIChatRole.USER, text));


        CopilotChatResponse chatResponse = requestChatCompletion(
            monitor,
            chatMessages
        );

        return chatResponse.choices().stream()
            .findFirst().orElseThrow()
            .message()
            .content();
    }

    @NotNull
    @Override
    public String command(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAICompletionContext context,
        @NotNull String text
    ) throws DBException {
        List<DAIChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new DAIChatMessage(DAIChatRole.SYSTEM, SYSTEM_PROMPT));
        chatMessages.add(context.asSystemMessage(monitor, getContextWindowSize(monitor)));
        chatMessages.add(new DAIChatMessage(DAIChatRole.USER, text));


        CopilotChatResponse chatResponse = requestChatCompletion(
            monitor,
            chatMessages
        );

        return chatResponse.choices().stream()
            .findFirst().orElseThrow()
            .message()
            .content();
    }

    @Override
    public boolean hasValidConfiguration() {
        return CopilotSettings.INSTANCE.isValidConfiguration();
    }

    @Override
    public void onSettingsUpdate(AISettingsRegistry registry) {

        try {
            client.dispose();
        } catch (DBException e) {
            log.error("Error disposing client", e);
        }

        synchronized (this) {
            sessionToken = null;
        }
    }

    private CopilotChatResponse requestChatCompletion(
        @NotNull DBRProgressMonitor monitor,
        List<DAIChatMessage> messages
    ) throws DBException {
        CopilotChatRequest chatRequest = CopilotChatRequest.builder()
            .withModel(CopilotSettings.INSTANCE.modelName())
            .withMessages(messages.stream().map(CopilotMessage::from).toList())
            .withTemperature(CopilotSettings.INSTANCE.temperature())
            .withStream(false)
            .withIntent(false)
            .withTopP(1)
            .withN(1)
            .build();

        return client.evaluate().chat(monitor, requestSessionToken(monitor).token(), chatRequest);
    }

    private CopilotSessionToken requestSessionToken(@NotNull DBRProgressMonitor monitor) throws DBException {
        if (sessionToken != null) {
            return sessionToken;
        }

        synchronized (this) {
            if (sessionToken != null) {
                return sessionToken;
            }

            return client.evaluate().sessionToken(monitor, CopilotSettings.INSTANCE.accessToken());
        }
    }
}
