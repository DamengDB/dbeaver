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

import org.eclipse.core.runtime.Assert;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.ai.AIConstants;
import org.jkiss.dbeaver.model.ai.format.IAIFormatter;
import org.jkiss.dbeaver.model.ai.metadata.MetadataProcessor;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCExecutionContextDefaults;
import org.jkiss.dbeaver.model.logical.DBSLogicalDataSource;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSEntity;
import org.jkiss.dbeaver.model.struct.DBSObjectContainer;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class DAICompletionContext {
    private final DAICompletionScope scope;
    private final List<DBSEntity> customEntities;
    private final DBSLogicalDataSource dataSource;
    private final DBCExecutionContext executionContext;
    private final IAIFormatter formatter;

    private DAICompletionContext(
        @NotNull DAICompletionScope scope,
        @Nullable List<DBSEntity> customEntities,
        @NotNull DBSLogicalDataSource dataSource,
        @NotNull DBCExecutionContext executionContext,
        @NotNull IAIFormatter formatter
    ) {
        this.scope = scope;
        this.customEntities = customEntities;
        this.dataSource = dataSource;
        this.executionContext = executionContext;
        this.formatter = formatter;
    }

    @NotNull
    public DAICompletionScope getScope() {
        return scope;
    }

    @NotNull
    public List<DBSEntity> getCustomEntities() {
        return Collections.unmodifiableList(Objects.requireNonNull(customEntities, "Scope is not custom"));
    }

    @NotNull
    public DBSLogicalDataSource getDataSource() {
        return dataSource;
    }

    @NotNull
    public DBCExecutionContext getExecutionContext() {
        return executionContext;
    }

    @NotNull
    public IAIFormatter getFormatter() {
        return formatter;
    }

    public static class Builder {
        private DAICompletionScope scope;
        private List<DBSEntity> customEntities;
        private DBSLogicalDataSource dataSource;
        private DBCExecutionContext executionContext;
        private IAIFormatter formatter;

        @NotNull
        public Builder setScope(@NotNull DAICompletionScope scope) {
            this.scope = scope;
            return this;
        }

        @NotNull
        public Builder setCustomEntities(@NotNull List<DBSEntity> customEntities) {
            this.customEntities = customEntities;
            return this;
        }

        @NotNull
        public Builder setDataSource(@NotNull DBSLogicalDataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        @NotNull
        public Builder setExecutionContext(@NotNull DBCExecutionContext executionContext) {
            this.executionContext = executionContext;
            return this;
        }

        @NotNull
        public Builder setFormatter(@NotNull IAIFormatter formatter) {
            this.formatter = formatter;
            return this;
        }

        @NotNull
        public DAICompletionContext build() {
            Assert.isLegal(
                scope != null,
                "Scope must be specified"
            );
            Assert.isLegal(
                scope != DAICompletionScope.CUSTOM || customEntities != null,
                "Custom entities must be specified when using custom scope"
            );
            Assert.isLegal(
                dataSource != null,
                "Data source must be specified"
            );
            Assert.isLegal(
                executionContext != null,
                "Execution context must be specified"
            );
            Assert.isLegal(
                formatter != null,
                "Formatter must be specified"
            );

            return new DAICompletionContext(scope, customEntities, dataSource, executionContext, formatter);
        }
    }

    public DAIChatMessage asSystemMessage(
        @NotNull DBRProgressMonitor monitor,
        int maxTokens
    ) throws DBException {
        return MetadataProcessor.INSTANCE.createMetadataMessage(
            monitor,
            this,
            getScopeObject(),
            formatter,
            maxTokens - AIConstants.MAX_RESPONSE_TOKENS
        );
    }

    public DBSObjectContainer getScopeObject() {
        DBCExecutionContextDefaults<?, ?> contextDefaults = executionContext.getContextDefaults();
        if (contextDefaults == null) {
            return (DBSObjectContainer) executionContext.getDataSource();
        }

        DBSObjectContainer scoped = switch (getScope()) {
            case CURRENT_SCHEMA:
                if (contextDefaults.getDefaultSchema() != null) {
                    yield contextDefaults.getDefaultSchema();
                } else {
                    yield contextDefaults.getDefaultCatalog();
                }
            case CURRENT_DATABASE:
                yield contextDefaults.getDefaultCatalog();
            default:
                yield null;
        };

        return scoped != null ? scoped : (DBSObjectContainer) executionContext.getDataSource();
    }
}
