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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import org.jkiss.dbeaver.model.ai.AIException;
import org.jkiss.dbeaver.model.ai.copilot.dto.CopilotChatRequest;
import org.jkiss.dbeaver.model.ai.copilot.dto.CopilotChatResponse;
import org.jkiss.dbeaver.model.ai.copilot.dto.CopilotSessionToken;
import org.jkiss.dbeaver.model.ai.n.AIUtils;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

public class CopilotClient implements AutoCloseable {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Gson GSON = new GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .serializeNulls()
        .create();

    private static final String COPILOT_SESSION_TOKEN_URL = "https://api.github.com/copilot_internal/v2/token";
    private static final String CHAT_REQUEST_URL = "https://api.githubcopilot.com/chat/completions";
    private static final String EDITOR_VERSION = "Neovim/0.6.1"; // TODO replace after partnership
    private static final String EDITOR_PLUGIN_VERSION = "copilot.vim/1.16.0"; // TODO replace after partnership
    private static final String USER_AGENT = "GithubCopilot/1.155.0";
    private static final String CHAT_EDITOR_VERSION = "vscode/1.80.1"; // TODO replace after partnership

    private final HttpClient client = HttpClient.newBuilder().build();
    private final AtomicReference<CopilotSessionToken> sessionTokenRef = new AtomicReference<>();

    public CopilotSessionToken sessionToken(
        DBRProgressMonitor monitor,
        String accessToken
    ) throws AIException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(COPILOT_SESSION_TOKEN_URL))
                .header("authorization", "token " + accessToken)
                .header("editor-version", EDITOR_VERSION)
                .header("editor-plugin-version", EDITOR_PLUGIN_VERSION)
                .header("user-agent", USER_AGENT)
                .GET()
                .timeout(TIMEOUT)
                .build();

            HttpResponse<String> response = AIUtils.awaitFuture(
                monitor,
                client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            );

            return GSON.fromJson(response.body(), CopilotSessionToken.class);
        } catch (URISyntaxException e) {
            throw new AIException("Invalid URI", e);
        } catch (InterruptedException e) {
            throw new AIException("Interrupted", e);
        }
    }

    public CopilotChatResponse chat(
        DBRProgressMonitor monitor,
        String token,
        CopilotChatRequest chatRequest
    ) throws AIException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .header("Content-type", "application/json")
                .uri(new URI(CHAT_REQUEST_URL))
                .header("authorization", "Bearer " + token)
                .header("Editor-Version", CHAT_EDITOR_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(chatRequest)))
                .timeout(TIMEOUT)
                .build();

            HttpResponse<String> response = AIUtils.awaitFuture(
                monitor,
                client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            );

            return GSON.fromJson(response.body(), CopilotChatResponse.class);
        } catch (URISyntaxException e) {
            throw new AIException("Invalid URI", e);
        } catch (InterruptedException e) {
            throw new AIException("Interrupted", e);
        }
    }

    @Override
    public void close() {
        client.close();
    }
}
