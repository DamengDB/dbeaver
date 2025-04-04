/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp and others
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

package org.jkiss.dbeaver.ui.navigator;

import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Tree;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.navigator.DBNNode;

/**
 * Tree item renderer
 */
public interface INavigatorItemRenderer {

    void paintNodeDetails(DBNNode node, Tree tree, GC gc, Event event);

    void performAction(DBNNode node, Tree tree, Event event, boolean defaultAction);

    @Nullable
    String getToolTipText(@NotNull DBNNode node, @NotNull Tree tree, @NotNull Event event);

    @Nullable
    Cursor getCursor(@NotNull DBNNode node, @NotNull Tree tree, @NotNull Event event);

}
