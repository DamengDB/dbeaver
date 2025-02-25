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

package org.jkiss.dbeaver.model.data;

public interface DBDConstants {


    String RESULT_NATIVE_DATETIME_FORMAT = "resultset.format.datetime.native"; //$NON-NLS-1$
    String RESULT_NATIVE_NUMERIC_FORMAT = "resultset.format.numeric.native"; //$NON-NLS-1$
    String RESULT_SCIENTIFIC_NUMERIC_FORMAT = "resultset.format.numeric.scientific"; //$NON-NLS-1$
    String RESULT_TRANSFORM_COMPLEX_TYPES = "resultset.transform.complex.type"; //$NON-NLS-1$
    String RESULT_SET_BINARY_PRESENTATION = "resultset.binary.representation"; //$NON-NLS-1$
    String RESULT_SET_BINARY_STRING_MAX_LEN = "resultset.binary.stringMaxLength"; //$NON-NLS-1$
    // This will ignore label in result set metadata and will use names always (some buggy drivers return description or other crap in labels - #1952)
    String RESULT_SET_IGNORE_COLUMN_LABEL = "resultset.column.label.ignore"; //$NON-NLS-1$
    String RESULT_SET_REREAD_ON_SCROLLING = "resultset.reread.on.scroll"; //$NON-NLS-1$
    String RESULT_SET_MAX_ROWS = "resultset.maxrows"; //$NON-NLS-1$
    String RESULT_SET_USE_DATETIME_EDITOR = "resultset.datetime.editor";
}