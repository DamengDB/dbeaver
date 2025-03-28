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
package org.jkiss.dbeaver.ext.damengdb.ui.actions;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.FindReplaceDocumentAdapter;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.damengdb.model.DamengPackage;
import org.jkiss.dbeaver.ext.damengdb.model.DamengProcedureArgument;
import org.jkiss.dbeaver.ext.damengdb.model.DamengProcedurePackaged;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.runtime.AbstractJob;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.rdb.DBSProcedureParameterKind;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.editors.entity.EntityEditor;
import org.jkiss.dbeaver.ui.editors.sql.SQLEditorBase;
import org.jkiss.dbeaver.ui.editors.sql.SQLEditorNested;
import org.jkiss.dbeaver.ui.navigator.actions.NavigatorHandlerObjectOpen;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.dbeaver.utils.RuntimeUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PackageNavigateHandler extends AbstractHandler {
	
    private static final Log log = Log.getLog(PackageNavigateHandler.class);

    public PackageNavigateHandler() {
    }

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        final DamengProcedurePackaged procedure = getSelectedProcedure(event);
        if (procedure != null) {
            DamengPackage procPackage = procedure.getParentObject();
            IEditorPart entityEditor = NavigatorHandlerObjectOpen.openEntityEditor(procPackage);
            if (entityEditor instanceof EntityEditor) {
                ((EntityEditor) entityEditor).switchFolder("source.definition");
                SQLEditorBase sqlEditor = entityEditor.getAdapter(SQLEditorBase.class);
                if (sqlEditor != null) {
                    new NavigateJob(procedure, sqlEditor).schedule();
                }
            }
        }
        return null;
    }

    private DamengProcedurePackaged getSelectedProcedure(ExecutionEvent event) {
        final ISelection currentSelection = HandlerUtil.getCurrentSelection(event);
        if (currentSelection instanceof IStructuredSelection && !currentSelection.isEmpty()) {
            Object firstElement = ((IStructuredSelection) currentSelection).getFirstElement();
            return RuntimeUtils.getObjectAdapter(firstElement, DamengProcedurePackaged.class);
        }
        return null;
    }

    static class NavigateJob extends AbstractJob {
        private final DamengProcedurePackaged procedure;

        private final SQLEditorBase sqlEditor;

        public NavigateJob(DamengProcedurePackaged procedure, SQLEditorBase sqlEditor) {
            super("Navigate procedure '" + procedure.getFullyQualifiedName(DBPEvaluationContext.UI));
            this.procedure = procedure;
            this.sqlEditor = sqlEditor;
        }

        @Override
        protected IStatus run(DBRProgressMonitor monitor) {
            try {
                navigate(monitor);
            } catch (InterruptedException e) {
                return Status.CANCEL_STATUS;
            } catch (DBException e) {
                return GeneralUtils.makeExceptionStatus(e);
            }
            return Status.OK_STATUS;
        }

        @SuppressWarnings("rawtypes")
        private void navigate(DBRProgressMonitor monitor) throws InterruptedException, DBException {
            if (sqlEditor instanceof SQLEditorNested) {
                int checkAttempts = 0;
                while (!((SQLEditorNested) sqlEditor).isDocumentLoaded() && checkAttempts < 10) {
                    Thread.sleep(500);
                    checkAttempts++;
                }
            }
            final IDocument document = sqlEditor.getDocument();
            if (document != null) {
                String procRegex = procedure.getProcedureType().name() + "\\s+" + procedure.getName();
                final Collection<DamengProcedureArgument> parameters = procedure.getParameters(monitor);
                if (parameters != null) {
                    List<DamengProcedureArgument> inParams = new ArrayList<>();
                    for (DamengProcedureArgument arg : parameters) {
                        if (arg.getParameterKind() != DBSProcedureParameterKind.OUT && !arg.isResultArgument()) {
                            inParams.add(arg);
                        }
                    }
                    if (!inParams.isEmpty()) {
                        procRegex += "\\s*\\([^\\)]+\\)";
                    }
                }
                final FindReplaceDocumentAdapter findAdapter = new FindReplaceDocumentAdapter(document);
                try {
                    final IRegion procRegion = findAdapter.find(0, procRegex, true, false, false, true);
                    if (procRegion != null) {
                        UIUtils.asyncExec(new Runnable() {
                            @Override
                            public void run() {
                                sqlEditor.selectAndReveal(procRegion.getOffset(), procRegion.getLength());
                            }
                        });
                    }
                } catch (BadLocationException e) {
                    log.error("Error finding procedure source", e);
                }
            }
        }

    }
    
}