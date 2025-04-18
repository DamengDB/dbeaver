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

package org.jkiss.dbeaver.ui.controls.resultset;

import org.eclipse.jface.action.IMenuManager;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.data.DBDAttributeBinding;

import java.util.Map;

/**
 * Result set renderer.
 * Visualizes result set viewer/editor.
 *
 * May additionally implement IResultSetEditor, ISelectionProvider, IStatefulControl
 */
public interface IResultSetPresentation {

    enum PresentationType {
        COLUMNS(true),
        DOCUMENT(true),
        CUSTOM(true),
        TRANSFORMER(false);

        /**
         * Persistent presentation will be reused next time user will show the same resultset.
         */
        public boolean isPersistent() {
            return persistent;
        }

        private final boolean persistent;

        PresentationType(boolean persistent) {
            this.persistent = persistent;
        }
    }

    enum RowPosition {
        FIRST,
        PREVIOUS,
        NEXT,
        LAST,
        CURRENT
    }

    /**
     * A predicate that decides whether the presentation can be shown or not.
     * <p>
     * An implementation may opt not to be opened. This can be useful if
     * an interactive confirmation is shown with an option to cancel the operation.
     *
     * @param controller associated result set controller
     * @return {@code true} if the presentation can be shown, or {@code false} if not
     */
    default boolean canShowPresentation(@NotNull IResultSetController controller) {
        return true;
    }

    void createPresentation(@NotNull IResultSetController controller, @NotNull Composite parent);

    IResultSetController getController();

    Control getControl();

    /**
     * Refreshes data
     * @param refreshMetadata    true if contents structure should be reloaded
     * @param append             appends data
     * @param keepState          commands to keep current presentation state even if refreshMetadata is true (usually this means data refresh/reorder).
     */
    void refreshData(boolean refreshMetadata, boolean append, boolean keepState);

    /**
     * Called after results refresh
     * @param refreshData data was refreshed
     */
    void formatData(boolean refreshData);

    void clearMetaData();

    void updateValueView();

    boolean isDirty();

    void applyChanges();

    /**
     *  Changes in the presentation will be canceled, the presentation will return in the initial state.
     */
    void rejectChanges();

    /**
     * Called by controller to fill context menu.
     * Note: context menu invocation must be initiated by presentation, then it should call controller's
     * {@link org.jkiss.dbeaver.ui.controls.resultset.IResultSetController#fillContextMenu} which then will
     * call this function.
     * Cool, huh?
     * @param menu    menu
     */
    void fillMenu(@NotNull IMenuManager menu);

    void changeMode(boolean recordMode);

    void scrollToRow(@NotNull RowPosition position);

    @Nullable
    DBDAttributeBinding getCurrentAttribute();

    @Nullable
    DBDAttributeBinding getFocusAttribute();

    void setCurrentAttribute(@NotNull DBDAttributeBinding attribute);

    void showAttribute(@NotNull DBDAttributeBinding attribute);

    @Nullable
    int[] getCurrentRowIndexes();

    @Nullable
    Point getCursorLocation();

    /**
     * Copies selected cells in supported Transfer formats.
     */
    @NotNull
    Map<Transfer, Object> copySelection(ResultSetCopySettings settings);

    void printResultSet();

    /**
     * Retrieves font identifier to increase or decrease its size as the user zooms in/out on the presentation.
     *
     * @return identifier of the font
     * @see org.eclipse.jface.resource.FontRegistry
     */
    @NotNull
    String getFontId();

    void dispose();

}
