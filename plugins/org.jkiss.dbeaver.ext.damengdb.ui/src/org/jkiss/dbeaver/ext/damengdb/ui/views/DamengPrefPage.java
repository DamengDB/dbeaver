/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp and others
 * Copyright (C) 2011-2012 Eugene Fradkin (eugene.fradkin@gmail.com)
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
package org.jkiss.dbeaver.ext.damengdb.ui.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;
import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.ext.damengdb.model.DamengConstants;
import org.jkiss.dbeaver.ext.damengdb.ui.internal.DamengUIMessages;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.preferences.DBPPreferenceStore;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.preferences.PreferenceStoreDelegate;
import org.jkiss.dbeaver.ui.preferences.TargetPrefPage;
import org.jkiss.dbeaver.utils.PrefUtils;

/**
 * DamengPrefPage
 */
public class DamengPrefPage extends TargetPrefPage {
	
    public static final String PAGE_ID = "org.jkiss.dbeaver.preferences.damengdb.general"; //$NON-NLS-1$

    private Text explainTableText;

    private Button rowidSupportCheck;

    private Button enableDbmsOutputCheck;

    private Button readAllSynonymsCheck;

    private Button disableScriptEscapeProcessingCheck;

    private Button useRuleHint;

    private Button useOptimizerHint;

    private Button useSimpleConstraints;

    private Button useAlternativeTableMetadataQuery;

    private Button searchInSynonyms;

    private Button showDateAsDate;

    public DamengPrefPage() {
        super();
        setPreferenceStore(new PreferenceStoreDelegate(DBWorkbench.getPlatform().getPreferenceStore()));
    }

    @Override
    protected boolean hasDataSourceSpecificOptions(DBPDataSourceContainer dataSourceDescriptor) {
        DBPPreferenceStore store = dataSourceDescriptor.getPreferenceStore();
        return store.contains(DamengConstants.PREF_EXPLAIN_TABLE_NAME)
            || store.contains(DamengConstants.PREF_SUPPORT_ROWID)
            || store.contains(DamengConstants.PREF_DBMS_OUTPUT)
            || store.contains(DamengConstants.PREF_DBMS_READ_ALL_SYNONYMS)
            || store.contains(DamengConstants.PREF_DISABLE_SCRIPT_ESCAPE_PROCESSING)
            || store.contains(DamengConstants.PROP_USE_RULE_HINT)
            || store.contains(DamengConstants.PROP_USE_META_OPTIMIZER)
            || store.contains(DamengConstants.PROP_METADATA_USE_SIMPLE_CONSTRAINTS)
            || store.contains(DamengConstants.PROP_METADATA_USE_ALTERNATIVE_TABLE_QUERY)
            || store.contains(DamengConstants.PROP_SEARCH_METADATA_IN_SYNONYMS)
            || store.contains(DamengConstants.PROP_SHOW_DATE_AS_DATE);
    }

    @Override
    protected boolean supportsDataSourceSpecificOptions() {
        return true;
    }

    @NotNull
    @Override
    protected Control createPreferenceContent(@NotNull Composite parent) {
        Composite composite = UIUtils.createPlaceholder(parent, 1);

        {
            Group planGroup = UIUtils.createControlGroup(composite,
                DamengUIMessages.pref_page_dameng_legend_execution_plan, 2, GridData.FILL_HORIZONTAL, 0);

            Label descLabel = new Label(planGroup, SWT.WRAP);
            descLabel.setText(DamengUIMessages.pref_page_dameng_label_by_default_plan_table);
            GridData gd = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
            gd.horizontalSpan = 2;
            descLabel.setLayoutData(gd);

            explainTableText = UIUtils.createLabelText(planGroup, DamengUIMessages.pref_page_dameng_label_plan_table,
                "", SWT.BORDER, new GridData(GridData.FILL_HORIZONTAL)); // $NON-NLS-2$
        }

        {
            Group miscGroup = UIUtils.createControlGroup(composite, DamengUIMessages.pref_page_dameng_legend_misc, 1,
                GridData.FILL_HORIZONTAL, 0);
            rowidSupportCheck = UIUtils.createCheckbox(miscGroup,
                DamengUIMessages.pref_page_dameng_checkbox_use_rowid_to_identify_rows, true);
            enableDbmsOutputCheck = UIUtils.createCheckbox(miscGroup,
                DamengUIMessages.pref_page_dameng_checkbox_enable_dbms_output, true);
            readAllSynonymsCheck = UIUtils.createCheckbox(miscGroup,
                DamengUIMessages.pref_page_dameng_checkbox_read_all_synonyms,
                DamengUIMessages.pref_page_dameng_label_if_unchecked_java_classes, true, 1);
            disableScriptEscapeProcessingCheck = UIUtils.createCheckbox(miscGroup,
                DamengUIMessages.pref_page_dameng_checkbox_disable_escape_processing,
                DamengUIMessages.pref_page_dameng_label_disable_client_side_parser, true, 1);
        }

        DBPPreferenceStore globalPreferences = DBWorkbench.getPlatform().getPreferenceStore();

        {
            Composite performanceGroup = UIUtils.createControlGroup(composite,
                DamengUIMessages.pref_page_dameng_legend_performance, 1, GridData.FILL_HORIZONTAL, 0);

            useRuleHint = UIUtils.createCheckbox(performanceGroup, DamengUIMessages.edit_create_checkbox_group_use_rule,
                globalPreferences.getBoolean(DamengConstants.PROP_USE_RULE_HINT));
            useRuleHint.setToolTipText(DamengUIMessages.edit_create_checkbox_adds_rule_tool_tip_text);

            useOptimizerHint = UIUtils.createCheckbox(performanceGroup,
                DamengUIMessages.edit_create_checkbox_group_use_metadata_optimizer,
                globalPreferences.getBoolean(DamengConstants.PROP_USE_META_OPTIMIZER));
            useOptimizerHint.setToolTipText(DamengUIMessages.edit_create_checkbox_group_use_metadata_optimizer_tip);

            useSimpleConstraints = UIUtils.createCheckbox(performanceGroup,
                DamengUIMessages.edit_create_checkbox_content_group_use_simple_constraints,
                DamengUIMessages.edit_create_checkbox_content_group_use_simple_constraints_description,
                globalPreferences.getBoolean(DamengConstants.PROP_METADATA_USE_SIMPLE_CONSTRAINTS), 1);

            useAlternativeTableMetadataQuery = UIUtils.createCheckbox(performanceGroup,
                DamengUIMessages.edit_create_checkbox_content_group_use_another_table_query,
                globalPreferences.getBoolean(DamengConstants.PROP_METADATA_USE_ALTERNATIVE_TABLE_QUERY));
            useAlternativeTableMetadataQuery.setToolTipText(
                DamengUIMessages.edit_create_checkbox_content_group_use_another_table_query_description);

            searchInSynonyms = UIUtils.createCheckbox(performanceGroup,
                DamengUIMessages.edit_create_checkbox_content_group_search_metadata_in_synonyms,
                globalPreferences.getBoolean(DamengConstants.PROP_SEARCH_METADATA_IN_SYNONYMS));
            searchInSynonyms.setToolTipText(
                DamengUIMessages.edit_create_checkbox_content_group_search_metadata_in_synonyms_tooltip);
        }

        {
            final Group dataGroup = UIUtils.createControlGroup(composite, DamengUIMessages.pref_page_dameng_group_data,
                1, GridData.HORIZONTAL_ALIGN_BEGINNING, 0);

            showDateAsDate = UIUtils.createCheckbox(dataGroup,
                DamengUIMessages.pref_page_dameng_checkbox_show_date_as_date,
                DamengUIMessages.pref_page_dameng_checkbox_show_date_as_date_tip, false, 1);
        }

        return composite;
    }

    @Override
    protected void loadPreferences(DBPPreferenceStore store) {
        explainTableText.setText(store.getString(DamengConstants.PREF_EXPLAIN_TABLE_NAME));
        rowidSupportCheck.setSelection(store.getBoolean(DamengConstants.PREF_SUPPORT_ROWID));
        enableDbmsOutputCheck.setSelection(store.getBoolean(DamengConstants.PREF_DBMS_OUTPUT));
        readAllSynonymsCheck.setSelection(store.getBoolean(DamengConstants.PREF_DBMS_READ_ALL_SYNONYMS));
        disableScriptEscapeProcessingCheck
            .setSelection(store.getBoolean(DamengConstants.PREF_DISABLE_SCRIPT_ESCAPE_PROCESSING));

        useRuleHint.setSelection(store.getBoolean(DamengConstants.PROP_USE_RULE_HINT));
        useOptimizerHint.setSelection(store.getBoolean(DamengConstants.PROP_USE_META_OPTIMIZER));
        useSimpleConstraints.setSelection(store.getBoolean(DamengConstants.PROP_METADATA_USE_SIMPLE_CONSTRAINTS));
        useAlternativeTableMetadataQuery
            .setSelection(store.getBoolean(DamengConstants.PROP_METADATA_USE_ALTERNATIVE_TABLE_QUERY));
        searchInSynonyms.setSelection(store.getBoolean(DamengConstants.PROP_SEARCH_METADATA_IN_SYNONYMS));

        showDateAsDate.setSelection(store.getBoolean(DamengConstants.PROP_SHOW_DATE_AS_DATE));
    }

    @Override
    protected void savePreferences(DBPPreferenceStore store) {
        store.setValue(DamengConstants.PREF_EXPLAIN_TABLE_NAME, explainTableText.getText());
        store.setValue(DamengConstants.PREF_SUPPORT_ROWID, rowidSupportCheck.getSelection());
        store.setValue(DamengConstants.PREF_DBMS_OUTPUT, enableDbmsOutputCheck.getSelection());
        store.setValue(DamengConstants.PREF_DBMS_READ_ALL_SYNONYMS, readAllSynonymsCheck.getSelection());
        store.setValue(DamengConstants.PREF_DISABLE_SCRIPT_ESCAPE_PROCESSING,
            disableScriptEscapeProcessingCheck.getSelection());

        store.setValue(DamengConstants.PROP_USE_RULE_HINT, useRuleHint.getSelection());
        store.setValue(DamengConstants.PROP_USE_META_OPTIMIZER, useOptimizerHint.getSelection());
        store.setValue(DamengConstants.PROP_METADATA_USE_SIMPLE_CONSTRAINTS, useSimpleConstraints.getSelection());
        store.setValue(DamengConstants.PROP_METADATA_USE_ALTERNATIVE_TABLE_QUERY,
            useAlternativeTableMetadataQuery.getSelection());
        store.setValue(DamengConstants.PROP_SEARCH_METADATA_IN_SYNONYMS, searchInSynonyms.getSelection());

        store.setValue(DamengConstants.PROP_SHOW_DATE_AS_DATE, showDateAsDate.getSelection());

        PrefUtils.savePreferenceStore(store);
    }

    @Override
    protected void clearPreferences(DBPPreferenceStore store) {
        store.setToDefault(DamengConstants.PREF_EXPLAIN_TABLE_NAME);
        store.setToDefault(DamengConstants.PREF_SUPPORT_ROWID);
        store.setToDefault(DamengConstants.PREF_DBMS_OUTPUT);
        store.setToDefault(DamengConstants.PREF_DBMS_READ_ALL_SYNONYMS);
        store.setToDefault(DamengConstants.PREF_DISABLE_SCRIPT_ESCAPE_PROCESSING);

        store.setToDefault(DamengConstants.PROP_USE_RULE_HINT);
        store.setToDefault(DamengConstants.PROP_USE_META_OPTIMIZER);
        store.setToDefault(DamengConstants.PROP_METADATA_USE_SIMPLE_CONSTRAINTS);
        store.setToDefault(DamengConstants.PROP_METADATA_USE_ALTERNATIVE_TABLE_QUERY);
        store.setToDefault(DamengConstants.PROP_SEARCH_METADATA_IN_SYNONYMS);

        store.setToDefault(DamengConstants.PROP_SHOW_DATE_AS_DATE);
    }

    @Override
    protected String getPropertyPageID() {
        return PAGE_ID;
    }
    
}