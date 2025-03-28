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
package org.jkiss.dbeaver.ext.damengdb.model;

import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

/**
 * Dameng data type attribute
 */
public class DamengDataTypeDefineType extends DamengDataTypeMember {
	
    public DamengDataTypeDefineType(DBRProgressMonitor monitor, DamengDataType dataType, DamengSubType type,
                                    int number) {
        super(dataType);
        this.name = type.getName();
        this.number = number;
    }

    public DamengDataTypeDefineType(DBRProgressMonitor monitor, DamengPackage dmPackage, DamengSubType type,
                                    int number) {
        super(dmPackage);
        this.name = type.getName();
        this.number = number;
    }

    @Property(viewable = true, order = 2)
    public int getOrdinalPosition() {
        // Number is 0 based
        return number;
    }

}
