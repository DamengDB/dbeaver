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
package org.jkiss.dbeaver.model.sql.parser.tokens.predicates;

import org.jkiss.code.NotNull;

/**
 * Represents node of token predicate describing a sequence of some tokens
 */
public class SequenceTokenPredicateNode extends GroupTokenPredicatesNode {
    public SequenceTokenPredicateNode(TokenPredicateNode... childs) {
        super(childs);
    }

    @Override
    @NotNull
    protected <T, R> R applyImpl(@NotNull TokenPredicateNodeVisitor<T, R> visitor, @NotNull T arg) {
        return visitor.visitSequence(this, arg);
    }
}
