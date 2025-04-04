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
package org.jkiss.dbeaver.ext.damengdb.model;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.data.DBDAttributeBinding;
import org.jkiss.dbeaver.model.data.DBDAttributeContentTypeProvider;
import org.jkiss.dbeaver.utils.MimeTypes;

public class DamengAttributeContentTypeProvider implements DBDAttributeContentTypeProvider {
    
	public static final DamengAttributeContentTypeProvider INSTANCE = new DamengAttributeContentTypeProvider();

    private DamengAttributeContentTypeProvider() {
        // prevents instantiation
    }

    @Nullable
    @Override
    public String getContentType(@NotNull DBDAttributeBinding binding) {
        switch (binding.getTypeName()) {
            case DamengConstants.TYPE_NAME_XML:
            case DamengConstants.TYPE_FQ_XML:
                return MimeTypes.TEXT_XML;
            default:
                return null;
        }
    }
    
}
