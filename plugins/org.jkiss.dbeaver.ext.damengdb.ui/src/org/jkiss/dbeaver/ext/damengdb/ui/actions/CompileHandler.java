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
package org.jkiss.dbeaver.ext.damengdb.ui.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.menus.UIElement;
import org.jkiss.dbeaver.ext.damengdb.model.DamengSequence;
import org.jkiss.dbeaver.ext.damengdb.model.source.DamengSourceObject;
import org.jkiss.dbeaver.ext.damengdb.ui.internal.DamengUIMessages;
import org.jkiss.dbeaver.model.DBPEvent;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCStatement;
import org.jkiss.dbeaver.model.exec.DBCStatementType;
import org.jkiss.dbeaver.model.exec.compile.DBCCompileError;
import org.jkiss.dbeaver.model.exec.compile.DBCCompileLog;
import org.jkiss.dbeaver.model.exec.compile.DBCCompileLogBase;
import org.jkiss.dbeaver.model.exec.compile.DBCSourceHost;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectState;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.editors.IDatabaseEditorInput;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.dbeaver.utils.RuntimeUtils;
import org.jkiss.utils.ArrayUtils;
import org.jkiss.utils.CommonUtils;

public class CompileHandler extends DamengTaskHandler {
	
    public static boolean compileUnit(DBRProgressMonitor monitor, DBCCompileLog compileLog, DamengSourceObject unit)
        throws DBCException {
        final DBEPersistAction[] compileActions = unit.getCompileActions(monitor);
        if (ArrayUtils.isEmpty(compileActions)) {
            if (unit instanceof DamengSequence) {
                // Sequence can not be compiled
                return false;
            }
            throw new DBCException("No compile actions associated with " + unit.getSourceType().name());
        }

        try (JDBCSession session = DBUtils.openUtilSession(monitor, unit, "Compile '" + unit.getName() + "'")) {
            boolean success = true;
            for (DBEPersistAction action : compileActions) {
                final String script = action.getScript();
                compileLog.trace(script);

                if (monitor.isCanceled()) {
                    break;
                }
                try {
                    try (DBCStatement dbStat = session.prepareStatement(DBCStatementType.QUERY, script, false, false,
                        false)) {
                        action.beforeExecute(session);
                        dbStat.executeStatement();
                    }
                    action.afterExecute(session, null);
                } catch (DBCException e) {
                    action.afterExecute(session, e);
                    throw e;
                }
            }
            final DBSObjectState oldState = unit.getObjectState();
            unit.refreshObjectState(monitor);
            if (unit.getObjectState() != oldState) {
                unit.getDataSource().getContainer().fireEvent(new DBPEvent(DBPEvent.Action.OBJECT_UPDATE, unit));
            }

            return success;
        }
    }

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        final IWorkbenchPart activePart = HandlerUtil.getActiveEditor(event);
        final List<DamengSourceObject> objects = getSelectedObjects(event);
        if (!objects.isEmpty()) {
            final Shell activeShell = HandlerUtil.getActiveShell(event);
            if (objects.size() == 1) {
                final DamengSourceObject unit = objects.get(0);

                DBCSourceHost sourceHost = null;
                if (activePart != null) {
                    sourceHost = RuntimeUtils.getObjectAdapter(activePart, DBCSourceHost.class);
                    if (sourceHost == null) {
                        sourceHost = activePart.getAdapter(DBCSourceHost.class);
                    }
                }
                if (sourceHost != null && sourceHost.getSourceObject() != unit) {
                    sourceHost = null;
                }

                final DBCCompileLog compileLog = sourceHost == null ? new DBCCompileLogBase()
                    : sourceHost.getCompileLog();
                compileLog.clearLog();
                Throwable error = null;
                try {
                    UIUtils.runInProgressService(monitor -> {
                        monitor.beginTask("Compile", 1);
                        try {
                            compileUnit(monitor, compileLog, unit);
                        } catch (DBCException e) {
                            throw new InvocationTargetException(e);
                        } finally {
                            monitor.done();
                        }
                    });
                    if (compileLog.getError() != null) {
                        error = compileLog.getError();
                    }
                } catch (InvocationTargetException e) {
                    error = e.getTargetException();
                } catch (InterruptedException e) {
                    return null;
                }
                if (error != null) {
                    DBWorkbench.getPlatformUI().showError("Unexpected compilation error", null, error);
                } else if (!CommonUtils.isEmpty(compileLog.getErrorStack())) {
                    int line = -1, position = -1;
                    StringBuilder fullMessage = new StringBuilder();
                    for (DBCCompileError oce : compileLog.getErrorStack()) {
                        fullMessage.append(oce.toString()).append(GeneralUtils.getDefaultLineSeparator());
                        if (line < 0) {
                            line = oce.getLine();
                            position = oce.getPosition();
                        }
                    }

                    // If compiled object is currently open in editor - try to
                    // position on error line
                    if (sourceHost != null && sourceHost.getSourceObject() == unit && line > 0 && position >= 0) {
                        sourceHost.positionSource(line, position);
                        activePart.getSite().getPage().activate(activePart);
                    }

                    String errorTitle = unit.getName() + " compilation failed";
                    if (sourceHost != null) {
                        sourceHost.setCompileInfo(errorTitle, true);
                        sourceHost.showCompileLog();
                    }
                    DBWorkbench.getPlatformUI().showError(errorTitle, fullMessage.toString());
                } else {
                    String message = unit.getName() + " compiled successfully";
                    if (sourceHost != null) {
                        sourceHost.setCompileInfo(message, true);
                    }
                    UIUtils.showMessageBox(activeShell, "Done", message, SWT.ICON_INFORMATION);
                }
            } else {
                DamengCompilerDialog dialog = new DamengCompilerDialog(activeShell, objects);
                dialog.open();
            }
        }
        return null;
    }

    private List<DamengSourceObject> getSelectedObjects(ExecutionEvent event) {
        List<DamengSourceObject> objects = new ArrayList<>();
        final ISelection currentSelection = HandlerUtil.getCurrentSelection(event);
        if (currentSelection instanceof IStructuredSelection && !currentSelection.isEmpty()) {
            for (Iterator<?> iter = ((IStructuredSelection) currentSelection).iterator(); iter.hasNext(); ) {
                final Object element = iter.next();
                final DamengSourceObject sourceObject = RuntimeUtils.getObjectAdapter(element,
                    DamengSourceObject.class);
                if (sourceObject != null) {
                    objects.add(sourceObject);
                }
            }
        }
        if (objects.isEmpty()) {
            final IWorkbenchPart activePart = HandlerUtil.getActivePart(event);
            if (activePart instanceof IEditorPart) {
                if (((IEditorPart) activePart).getEditorInput() instanceof IDatabaseEditorInput) {
                    DBSObject dbsObject = ((IDatabaseEditorInput) ((IEditorPart) activePart).getEditorInput())
                        .getDatabaseObject();
                    if (dbsObject instanceof DamengSourceObject) {
                        objects.add((DamengSourceObject) dbsObject);
                    }
                }
            }
        }
        return objects;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void updateElement(UIElement element, Map parameters) {
        List<DamengSourceObject> objects = getDmSourceObjects(element);

        if (!objects.isEmpty()) {
            if (objects.size() > 1) {
                element.setText("Compile " + objects.size() + " objects");
            } else {
                final DamengSourceObject sourceObject = objects.get(0);
                String objectType = sourceObject.getSourceType().name();
                switch (objectType) {
                    case "CLASS":
                        objectType = DamengUIMessages.compile_class;
                        break;
                    case "PROCEDURE":
                        objectType = DamengUIMessages.compile_procedure;
                        break;
                    case "FUNCTION":
                        objectType = DamengUIMessages.compile_function;
                        break;
                    case "PACKAGE":
                        objectType = DamengUIMessages.compile_package;
                        break;
                    case "TRIGGER":
                        objectType = DamengUIMessages.compile_trigger;
                        break;
                    case "VIEW":
                        objectType = DamengUIMessages.compile_view;
                        break;
                }
                element.setText(DamengUIMessages.mouse_click_actions_compile + " "
                    + objectType/* + " '" + sourceObject.getName() + "'" */);
            }
        }
    }

}