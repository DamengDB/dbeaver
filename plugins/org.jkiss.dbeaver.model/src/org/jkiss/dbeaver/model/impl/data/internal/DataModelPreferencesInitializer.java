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
package org.jkiss.dbeaver.model.impl.data.internal;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.jkiss.dbeaver.model.data.DBDConstants;
import org.jkiss.dbeaver.model.data.DBDValueFormatting;
import org.jkiss.dbeaver.model.preferences.DBPPreferenceStore;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.utils.PrefUtils;

public class DataModelPreferencesInitializer extends AbstractPreferenceInitializer {

    public DataModelPreferencesInitializer() {
    }

    @Override
    public void initializeDefaultPreferences() {
        // Init default preferences
        DBPPreferenceStore store = DBWorkbench.getPlatform().getPreferenceStore();

        PrefUtils.setDefaultPreferenceValue(store, DBDConstants.RESULT_NATIVE_DATETIME_FORMAT, false);
        PrefUtils.setDefaultPreferenceValue(store, DBDConstants.RESULT_NATIVE_NUMERIC_FORMAT, false);
        PrefUtils.setDefaultPreferenceValue(store, DBDConstants.RESULT_SCIENTIFIC_NUMERIC_FORMAT, false);
        PrefUtils.setDefaultPreferenceValue(store, DBDConstants.RESULT_TRANSFORM_COMPLEX_TYPES, true);
        PrefUtils.setDefaultPreferenceValue(store, DBDConstants.RESULT_SET_REREAD_ON_SCROLLING, true);
        PrefUtils.setDefaultPreferenceValue(store, DBDConstants.RESULT_SET_MAX_ROWS, 200);

        // ResultSet
        PrefUtils.setDefaultPreferenceValue(store, DBDConstants.RESULT_SET_BINARY_PRESENTATION, DBDValueFormatting.BINARY_FORMATS[0].getId());
        PrefUtils.setDefaultPreferenceValue(store, DBDConstants.RESULT_SET_BINARY_STRING_MAX_LEN, 32);
        PrefUtils.setDefaultPreferenceValue(store, DBDConstants.RESULT_SET_IGNORE_COLUMN_LABEL, false);
    }

}
