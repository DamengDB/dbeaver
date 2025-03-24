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
package org.jkiss.dbeaver.model.qm.meta;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;

/**
 * QM model data source connect error info.
 */
public class QMMDataSourceConnectErrorInfo implements QMMDataSourceInfo {
    private final String containerId;
    private final String containerName;
    private final String driverId;
    private final String connectionUrl;
    private final String errorType;
    private final String errorMessage;

    public QMMDataSourceConnectErrorInfo(
        @NotNull DBPDataSourceContainer container,
        @NotNull String errorType,
        @Nullable String errorMessage
    ) {
        this.containerId = container.getId();
        this.containerName = container.getName();
        this.driverId = container.getDriver().getId();
        this.connectionUrl = container.getConnectionConfiguration().getUrl();
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }


    @Override
    public String getContainerId() {
        return containerId;
    }

    @Override
    public String getContainerName() {
        return containerName;
    }

    @Override
    public String getDriverId() {
        return driverId;
    }

    @Override
    public String getConnectionUrl() {
        return connectionUrl;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}