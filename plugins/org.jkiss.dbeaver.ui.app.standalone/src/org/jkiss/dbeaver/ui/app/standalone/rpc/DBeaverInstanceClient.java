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

package org.jkiss.dbeaver.ui.app.standalone.rpc;

import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.Log;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.rest.RestClient;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * DBeaver instance client.
 */
public final class DBeaverInstanceClient {

    private static final Log log = Log.getLog(DBeaverInstanceClient.class);

    @Nullable
    public static IInstanceController createClient() {
        return createClient(null);
    }

    @Nullable
    public static IInstanceController createClient(@Nullable String workspacePath) {
        final Path path = DBeaverInstanceUtils.getConfigPath(workspacePath);

        if (Files.notExists(path)) {
            log.trace("No instance controller is available");
            return null;
        }

        final Properties properties = new Properties();

        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        } catch (IOException e) {
            log.error("Error reading instance controller configuration: " + e.getMessage());
            return null;
        }

        final String port = properties.getProperty("port");

        if (CommonUtils.isEmptyTrimmed(port)) {
            log.error("No port specified for the instance controller to connect to");
            return null;
        }

        final IInstanceController instance = RestClient
            .builder(URI.create("http://localhost:" + port), IInstanceController.class)
            .create();

        try {
            final long payload = System.currentTimeMillis();
            final long response = instance.ping(payload);

            if (response != payload) {
                throw new IllegalStateException("Invalid ping response: " + response + ", was expecting " + payload);
            }
        } catch (Throwable e) {
            log.error("Error accessing instance server: " + e.getMessage());
            return null;
        }

        return instance;
    }


}