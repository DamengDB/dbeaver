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

import java.io.Serializable;

/**
 * Base class
 */
public class DamengDatabaseObject implements Serializable {
	
    public final static String EMPTY = "";
    public final static String DEFAULT = "<--DEFAULT-->";
    public final static String UNNAMED_TABLE_NAME_PREFIX = "TABLE_";
    public final static String UNNAMED_COLUMN_NAME_PREFIX = "COLUMN_";
    public final static String UNNAMED_PARAMETER_NAME_PREFIX = "PARAM_";
    public final static String UNNAMED_PARTITION_NAME_PREFIX = "PART_";
    public final static String UNNAMED_PARTITION_GROUP_NAME_PREFIX = "DAMENG_";
    public final static int OBJTYPE_ALL = 0;
    public final static int OBJTYPE_SYSTEM = 1;
    public final static int OBJTYPE_USER = 2;
    // storage
    public final static String EXTENTS_INIT = "1";
    public final static String EXTENTS_NEXT = "1";
    public final static String EXTENTS_MIN = "1";
    public final static String FILL_FACTOR = "0";
    public final static String IDENTITY_SEED = "1";
    public final static String IDENTITY_INCREAMENT = "1";
    private static final long serialVersionUID = -422592868212848719L;
    protected String id = null;

    protected String name = null;

    protected String createDate = null;

    protected boolean valid = true;

    protected boolean enable = true;

    protected boolean isAddDoubleQuote = true;

    protected final static String trim(String str) {
        return str == null ? null : str.trim();
    }

    protected final static String rightTrim(String str) {
        if (str == null) {
            return null;
        }
        return ("r" + str).trim().substring(1);
    }

    protected final static String trimToEmpty(String str) {
        return str == null ? "" : str.trim();
    }

    protected final static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

    protected final static boolean isNotEmpty(String str) {
        return str != null && str.length() > 0;
    }

    protected final static boolean equals(String str1, String str2) {
        return isEmpty(str1) ? isEmpty(str2) : str1.equals(str2);
    }

    protected final static boolean equalsIgnoreCase(String str1, String str2) {
        return isEmpty(str1) ? isEmpty(str2) : str1.equalsIgnoreCase(str2);
    }

    protected final static String processSingleQuoteOfName(String name) {
        return processQuoteOfName(name, "'");
    }

    protected final static String processDoubleQuoteOfName(String name) {
        return processQuoteOfName(name, "\"");
    }

    // Deal with the quote escape
    private final static String processQuoteOfName(String name, String quote) {
        if (DamengDatabaseObject.isEmpty(quote) || name == null) {
            return name;
        }
        String temp = name;
        StringBuilder result = new StringBuilder();
        int index = -1;
        int quetoLength = quote.length();
        while ((index = temp.indexOf(quote)) != -1) {
            result.append(temp.substring(0, index + quetoLength)).append(quote);
            temp = temp.substring(index + quetoLength);
        }
        result.append(temp);
        return result.toString();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCreateDate() {
        return createDate;
    }

    public void setCreateDate(String createDate) {
        this.createDate = createDate;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public boolean isAddDoubleQuote() {
        return isAddDoubleQuote;
    }

    public void setAddDoubleQuote(boolean isAddDoubleQuote) {
        this.isAddDoubleQuote = isAddDoubleQuote;
    }

    @Override
    public String toString() {
        return name == null ? "" : name;
    }

    public String getFullName() {
        String objName = (name != null ? name : "");
        StringBuilder fullObjName = new StringBuilder();

        if (isAddDoubleQuote) {
            fullObjName.append('\"');
            fullObjName.append(processDoubleQuoteOfName(objName));
            fullObjName.append('\"');
        } else {
            fullObjName.append(objName);
        }

        return fullObjName.toString();
    }

    public void reset(DamengDatabaseObject dbobj) {
        this.id = dbobj.id;
        this.name = dbobj.name;
        this.createDate = dbobj.createDate;
        this.valid = dbobj.valid;
        this.enable = dbobj.enable;
        this.isAddDoubleQuote = true;
    }
    
}
