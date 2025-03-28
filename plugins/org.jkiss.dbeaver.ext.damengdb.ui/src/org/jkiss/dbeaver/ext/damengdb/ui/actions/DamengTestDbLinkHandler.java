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
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.menus.UIElement;
import org.jkiss.dbeaver.ext.damengdb.model.DamengDBLink;
import org.jkiss.dbeaver.ext.damengdb.model.DamengObjectPersistAction;
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
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObjectState;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.editors.entity.EntityEditor;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.dbeaver.utils.RuntimeUtils;
import org.jkiss.utils.ArrayUtils;
import org.jkiss.utils.CommonUtils;

public class DamengTestDbLinkHandler extends DamengTaskHandler {
	
    /**
     * DBLink Test
     */
    public static boolean testDblink(DBRProgressMonitor monitor, DBCCompileLog compileLog, DamengDBLink dblink)
        throws DBCException {
        final DBEPersistAction[] testActions = dblink.getRunActions();
        if (ArrayUtils.isEmpty(testActions)) {
            return true;
        }

        try (JDBCSession session = DBUtils.openUtilSession(monitor, dblink, "Test '" + dblink.getName() + "'")) {
            boolean success = true;
            for (DBEPersistAction action : testActions) {
                final String script = action.getScript();
                compileLog.trace(script);

                if (monitor.isCanceled()) {
                    break;
                }
                try {
                    try (DBCStatement dbStat = session.prepareStatement(DBCStatementType.SCRIPT, script, false, false,
                        false)) {
                        action.beforeExecute(session);
                        dbStat.executeStatement();
                    }
                    action.afterExecute(session, null);
                } catch (DBCException e) {
                    action.afterExecute(session, e);
                    throw e;
                }
                if (action instanceof DamengObjectPersistAction) {

                    success = false;

                }
            }
            final DBSObjectState oldState = dblink.getObjectState();
            dblink.refreshObjectState(monitor);
            if (dblink.getObjectState() != oldState) {
                dblink.getDataSource().getContainer().fireEvent(new DBPEvent(DBPEvent.Action.OBJECT_UPDATE, dblink));
            }

            return success;
        }
    }

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        final IWorkbenchPart activePart = HandlerUtil.getActiveEditor(event);
        final List<DamengDBLink> objects = getSelectedLinks(event);
        if (!objects.isEmpty()) {
            if (activePart instanceof EntityEditor) {
                // Save editor before run
                // Use null monitor as entity editor has its own detached job
                // for save
                EntityEditor entityEditor = (EntityEditor) activePart;
                if (entityEditor.isDirty()) {
                    NullProgressMonitor monitor = new NullProgressMonitor();
                    entityEditor.doSave(monitor);
                    if (monitor.isCanceled()) {
                        // Save failed - doesn't make sense to compile
                        return null;
                    }
                }
            }
            final Shell activeShell = HandlerUtil.getActiveShell(event);
            if (objects.size() == 1) {
                final DamengDBLink dbLink = objects.get(0);

                final DBCCompileLog compileLog = new DBCCompileLogBase();
                compileLog.clearLog();
                Throwable error = null;
                try {
                    UIUtils.runInProgressService(monitor -> {
                        // monitor.beginTask("Test", 1);
                        try {
                            testDblink(monitor, compileLog, dbLink);

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
                    DBWorkbench.getPlatformUI().showError("Unexpected test DBlink error", null, error);
                } else if (!CommonUtils.isEmpty(compileLog.getErrorStack())) {
                    // Show compile errors
                    @SuppressWarnings("unused")
                    int line = -1, position = -1;
                    StringBuilder fullMessage = new StringBuilder();
                    for (DBCCompileError oce : compileLog.getErrorStack()) {
                        fullMessage.append(oce.toString()).append(GeneralUtils.getDefaultLineSeparator());
                        if (line < 0) {
                            line = oce.getLine();
                            position = oce.getPosition();
                        }
                    }

                    String errorTitle = " Test DBlink " + dbLink.getName() + " failed";
                    DBWorkbench.getPlatformUI().showError(errorTitle, fullMessage.toString());
                } else {
                    String message = dbLink.getName() + " tested successfully";
                    UIUtils.showMessageBox(activeShell, "Done", message, SWT.ICON_INFORMATION);
                }
            }

        }
        return null;
    }

    private List<DamengDBLink> getSelectedLinks(ExecutionEvent event) {
        List<DamengDBLink> objects = new ArrayList<>();
        final ISelection currentSelection = HandlerUtil.getCurrentSelection(event);
        if (currentSelection instanceof IStructuredSelection && !currentSelection.isEmpty()) {
            for (Iterator<?> iter = ((IStructuredSelection) currentSelection).iterator(); iter.hasNext(); ) {
                final Object element = iter.next();
                final DamengDBLink sourceLink = RuntimeUtils.getObjectAdapter(element, DamengDBLink.class);
                if (sourceLink != null) {
                    objects.add(sourceLink);
                }
            }
        }
        if (objects.isEmpty()) {
            final IWorkbenchPart activePart = HandlerUtil.getActivePart(event);
            final DamengDBLink sourceLink = RuntimeUtils.getObjectAdapter(activePart, DamengDBLink.class);
            if (sourceLink != null) {
                objects.add(sourceLink);
            }

        }
        return objects;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void updateElement(UIElement element, Map parameters) {
        List<DamengSourceObject> objects = getDmSourceObjects(element);
        if (!objects.isEmpty()) {
            element.setText(DamengUIMessages.mouse_click_actions_test_dblink);
        }
    }
    
}
