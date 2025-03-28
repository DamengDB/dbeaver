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
package org.jkiss.dbeaver.ui.preferences;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.IWorkbenchPropertyPage;
import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.core.CoreMessages;
import org.jkiss.dbeaver.model.exec.DBCExecutionPurpose;
import org.jkiss.dbeaver.model.preferences.DBPPreferenceStore;
import org.jkiss.dbeaver.model.qm.QMConstants;
import org.jkiss.dbeaver.model.qm.QMObjectType;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.dialogs.DialogUtils;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.dbeaver.utils.PrefUtils;
import org.jkiss.utils.CommonUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * PrefPageQueryManager
 */
public class PrefPageQueryManager extends AbstractPrefPage implements IWorkbenchPreferencePage, IWorkbenchPropertyPage
{
    public static final String PAGE_ID = "org.jkiss.dbeaver.preferences.main.qm"; //$NON-NLS-1$
    private Button checkObjectTypeSessions;
    private Button checkObjectTypeTxn;
    private Button checkObjectTypeQueries;
    private Button checkQueryTypeUser;
    private Button checkQueryTypeUserFiltered;
    private Button checkQueryTypeScript;
    private Button checkQueryTypeUtil;
    private Button checkQueryTypeMeta;
    private Button checkQueryTypeDDL;
    private Text textHistoryDays;
    private Text textEntriesPerPage;
    private Button checkStoreLog;
    private Text textOutputFolder;


    @Override
    public void init(IWorkbench workbench)
    {

    }

    @NotNull
    @Override
    protected Control createPreferenceContent(@NotNull Composite parent) {
        Composite composite = UIUtils.createPlaceholder(parent, 1);

        Composite filterSettings = UIUtils.createPlaceholder(composite, 2, 5);
        filterSettings.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        DBPPreferenceStore store = DBWorkbench.getPlatform().getPreferenceStore();

        Group groupQueryTypes = UIUtils.createControlGroup(filterSettings, CoreMessages.pref_page_query_manager_group_query_types, 1, GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING, 0);
        checkQueryTypeUser = UIUtils.createCheckbox(groupQueryTypes, CoreMessages.pref_page_query_manager_checkbox_user_queries, false);
        checkQueryTypeUserFiltered = UIUtils.createCheckbox(groupQueryTypes, CoreMessages.pref_page_query_manager_checkbox_user_filtered, false);
        checkQueryTypeScript = UIUtils.createCheckbox(groupQueryTypes, CoreMessages.pref_page_query_manager_checkbox_user_scripts, false);
        checkQueryTypeUtil = UIUtils.createCheckbox(groupQueryTypes, CoreMessages.pref_page_query_manager_checkbox_utility_functions, false);
        checkQueryTypeMeta = UIUtils.createCheckbox(groupQueryTypes, CoreMessages.pref_page_query_manager_checkbox_metadata_read, false);
        checkQueryTypeDDL = UIUtils.createCheckbox(groupQueryTypes, CoreMessages.pref_page_query_manager_checkbox_metadata_write, false);

        Group groupObjects = UIUtils.createControlGroup(filterSettings, CoreMessages.pref_page_query_manager_group_object_types, 1, GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING, 0);
        checkObjectTypeSessions = UIUtils.createCheckbox(groupObjects, CoreMessages.pref_page_query_manager_checkbox_sessions, false);
        checkObjectTypeTxn = UIUtils.createCheckbox(groupObjects, CoreMessages.pref_page_query_manager_checkbox_transactions, false);
        //checkObjectTypeScripts = UIUtils.createCheckbox(groupObjects, CoreMessages.pref_page_query_manager_checkbox_scripts, false);
        checkObjectTypeQueries = UIUtils.createCheckbox(groupObjects, CoreMessages.pref_page_query_manager_checkbox_queries, false);

        {
            Group viewSettings = UIUtils.createControlGroup(composite, CoreMessages.pref_page_query_manager_group_settings, 2, GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING, 0);
            textEntriesPerPage = UIUtils.createLabelText(
                viewSettings,
                CoreMessages.pref_page_query_manager_label_entries_per_page,
                store.getString(QMConstants.PROP_ENTRIES_PER_PAGE),
                SWT.BORDER,
                new GridData(50, SWT.DEFAULT));
        }

        {
            Group storageSettings = UIUtils.createControlGroup(composite, CoreMessages.pref_page_query_manager_group_storage, 2, GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING, 0);
            checkStoreLog = UIUtils.createCheckbox(
                storageSettings,
                CoreMessages.pref_page_query_manager_checkbox_store_log_file,
                store.getBoolean(QMConstants.PROP_STORE_LOG_FILE));
            GridData gd = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
            gd.horizontalSpan = 2;
            checkStoreLog.setLayoutData(gd);
            checkStoreLog.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    UIUtils.enableWithChildren(textOutputFolder.getParent(), checkStoreLog.getSelection());
                    UIUtils.enableWithChildren(textHistoryDays, checkStoreLog.getSelection());
                }
            });
            textOutputFolder = DialogUtils.createOutputFolderChooser(storageSettings, CoreMessages.pref_page_query_manager_logs_folder, null, false, null);

            textOutputFolder.setText(getQMLogStorePath(store));
            textHistoryDays = UIUtils.createLabelText(
                storageSettings,
                CoreMessages.pref_page_query_manager_label_days_to_store_log,
                store.getString(QMConstants.PROP_HISTORY_DAYS),
                SWT.BORDER,
                new GridData(50, SWT.DEFAULT));

            Control infoLabel = UIUtils.createInfoLabel(storageSettings, CoreMessages.pref_page_query_manager_log_file_hint);
            infoLabel.setLayoutData(new GridData(GridData.FILL, GridData.BEGINNING, true, false, 2, 1));
        }
        setSettings();

        return composite;
    }

    private void setSettings() {
        DBPPreferenceStore store = DBWorkbench.getPlatform().getPreferenceStore();
        Collection<QMObjectType> objectTypes = QMObjectType.fromString(store.getString(QMConstants.PROP_OBJECT_TYPES));
        Collection<String> queryTypes = CommonUtils.splitString(store.getString(QMConstants.PROP_QUERY_TYPES), ',');
        checkObjectTypes(objectTypes);
        checkQueryTypes(queryTypes);
        UIUtils.enableWithChildren(textOutputFolder.getParent(), checkStoreLog.getSelection());
        UIUtils.enableWithChildren(textHistoryDays, checkStoreLog.getSelection());
    }

    private void checkObjectTypes(Collection<QMObjectType> objectTypes) {
        checkObjectTypeSessions.setSelection(objectTypes.contains(QMObjectType.session));
        checkObjectTypeTxn.setSelection(objectTypes.contains(QMObjectType.txn));
        checkObjectTypeQueries.setSelection(objectTypes.contains(QMObjectType.query));
    }

    private void checkQueryTypes(Collection<String> queryTypes) {
        checkQueryTypeUser.setSelection(queryTypes.contains(DBCExecutionPurpose.USER.name()));
        checkQueryTypeUserFiltered.setSelection(queryTypes.contains(DBCExecutionPurpose.USER_FILTERED.name()));
        checkQueryTypeScript.setSelection(queryTypes.contains(DBCExecutionPurpose.USER_SCRIPT.name()));
        checkQueryTypeUtil.setSelection(queryTypes.contains(DBCExecutionPurpose.UTIL.name()));
        checkQueryTypeMeta.setSelection(queryTypes.contains(DBCExecutionPurpose.META.name()));
        checkQueryTypeDDL.setSelection(queryTypes.contains(DBCExecutionPurpose.META_DDL.name()));
    }

    @Override
    protected void performDefaults() {
        Collection<QMObjectType> objectTypes = Arrays.asList(QMObjectType.txn, QMObjectType.query);
        Collection<String> queryTypes = Arrays.asList(
            DBCExecutionPurpose.USER.name(),
            DBCExecutionPurpose.USER_FILTERED.name(),
            DBCExecutionPurpose.USER_SCRIPT.name());

        checkObjectTypes(objectTypes);
        checkQueryTypes(queryTypes);

        DBPPreferenceStore store = DBWorkbench.getPlatform().getPreferenceStore();
        textHistoryDays.setText(store.getDefaultString(QMConstants.PROP_HISTORY_DAYS));
        textEntriesPerPage.setText(store.getDefaultString(QMConstants.PROP_ENTRIES_PER_PAGE));

        checkStoreLog.setSelection(store.getDefaultBoolean(QMConstants.PROP_STORE_LOG_FILE));
        textOutputFolder.setText(store.getDefaultString(QMConstants.PROP_LOG_DIRECTORY));
        UIUtils.enableWithChildren(textOutputFolder.getParent(), checkStoreLog.getSelection());
        UIUtils.enableWithChildren(textHistoryDays, checkStoreLog.getSelection());

        super.performDefaults();
    }

    @Override
    public boolean performOk()
    {
        List<QMObjectType> objectTypes = new ArrayList<>();
        List<String> queryTypes = new ArrayList<>();
        if (checkObjectTypeSessions.getSelection()) objectTypes.add(QMObjectType.session);
        if (checkObjectTypeTxn.getSelection()) objectTypes.add(QMObjectType.txn);
        if (checkObjectTypeQueries.getSelection()) objectTypes.add(QMObjectType.query);

        if (checkQueryTypeUser.getSelection()) queryTypes.add(DBCExecutionPurpose.USER.name());
        if (checkQueryTypeUserFiltered.getSelection()) queryTypes.add(DBCExecutionPurpose.USER_FILTERED.name());
        if (checkQueryTypeScript.getSelection()) queryTypes.add(DBCExecutionPurpose.USER_SCRIPT.name());
        if (checkQueryTypeUtil.getSelection()) queryTypes.add(DBCExecutionPurpose.UTIL.name());
        if (checkQueryTypeMeta.getSelection()) queryTypes.add(DBCExecutionPurpose.META.name());
        if (checkQueryTypeDDL.getSelection()) queryTypes.add(DBCExecutionPurpose.META_DDL.name());

        Integer historyDays = UIUtils.getTextInteger(textHistoryDays);
        Integer entriesPerPage = UIUtils.getTextInteger(textEntriesPerPage);

        DBPPreferenceStore store = DBWorkbench.getPlatform().getPreferenceStore();
        store.setValue(QMConstants.PROP_OBJECT_TYPES, QMObjectType.toString(objectTypes));
        store.setValue(QMConstants.PROP_QUERY_TYPES, CommonUtils.makeString(queryTypes, ','));
        if (historyDays != null) {
            store.setValue(QMConstants.PROP_HISTORY_DAYS, Math.max(1, historyDays));
        }
        if (entriesPerPage != null) {
            store.setValue(QMConstants.PROP_ENTRIES_PER_PAGE, Math.max(1, entriesPerPage));
        }
        store.setValue(QMConstants.PROP_STORE_LOG_FILE, checkStoreLog.getSelection());
        store.setValue(QMConstants.PROP_LOG_DIRECTORY, textOutputFolder.getText());
        PrefUtils.savePreferenceStore(store);

        return super.performOk();
    }

    @Override
    public IAdaptable getElement()
    {
        return null;
    }

    @Override
    public void setElement(IAdaptable element)
    {

    }

    private String getQMLogStorePath(@NotNull DBPPreferenceStore store) {
        String qmStorePath = store.getString(QMConstants.PROP_LOG_DIRECTORY);
        if (CommonUtils.isEmpty(qmStorePath)) {
            qmStorePath = GeneralUtils.getMetadataFolder().toAbsolutePath().toString();
        }
        return qmStorePath;
    }
}