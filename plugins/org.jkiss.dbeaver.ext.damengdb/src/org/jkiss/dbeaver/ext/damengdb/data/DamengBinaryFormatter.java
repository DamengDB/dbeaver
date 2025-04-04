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
package org.jkiss.dbeaver.ext.damengdb.data;

import org.jkiss.dbeaver.model.impl.data.formatters.BinaryFormatterHex;

/**
 * DamengBinaryFormatter
 */
public class DamengBinaryFormatter extends BinaryFormatterHex {

    public static final DamengBinaryFormatter INSTANCE = new DamengBinaryFormatter();

    private static final String HEX_PREFIX = "'";

    private static final String HEX_POSTFIX = "'";

    @Override
    public String getId() {
        return "dmhex";
    }

    @Override
    public String getTitle() {
        return "Dameng Hex";
    }

    @Override
    public String toString(byte[] bytes, int offset, int length) {
        return HEX_PREFIX + super.toString(bytes, offset, length) + HEX_POSTFIX;
    }

    @Override
    public byte[] toBytes(String string) {
        if (string.startsWith(HEX_PREFIX)) {
            string = string.substring(HEX_PREFIX.length(), string.length() - HEX_POSTFIX.length());
        }
        return super.toBytes(string);
    }

}
