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

import org.jkiss.dbeaver.model.DBConstants;
import org.jkiss.dbeaver.model.DBIcon;
import org.jkiss.dbeaver.model.data.DBDPseudoAttribute;
import org.jkiss.dbeaver.model.data.DBDPseudoAttributeType;
import org.jkiss.dbeaver.model.struct.DBSEntityConstraintType;
import org.jkiss.dbeaver.model.struct.rdb.DBSIndexType;

/**
 * Dameng constants
 */
public class DamengConstants {
	
    public static final String CMD_COMPILE = "org.jkiss.dbeaver.ext.damengdb.code.compile";

    public static final String CMD_TEST_DBLINK = "org.jkiss.dbeaver.ext.damengdb.code.testDbLink";

    public static final int DEFAULT_PORT = 5236;

    public static final String SCHEMA_SYS = "SYS";

    public static final String EMPTY = "";

    public static final String VIEW_ALL_SOURCE = "ALL_SOURCE";

    public static final String VIEW_DBA_SOURCE = "DBA_SOURCE";

    public static final String VIEW_DBA_TAB_PRIVS = "DBA_TAB_PRIVS";

    // Dameng has no Execution Plan table name
    public static final String DM_PLAN_TABLE = "";

    public static final String EXPLAIN_KEYWORD = "EXPLAIN";

    public static final int EXPLAIN_KEYWORD_LEN = 7;

    public static final String[] SYSTEM_SCHEMAS = {"CTXSYS", "SYS", "SYSAUDITOR", "SYSDBA", "SYSSSO"};

    @SuppressWarnings("deprecation")
    public static final String PROP_CONNECTION_TYPE = DBConstants.INTERNAL_PROP_PREFIX + "connection-type@";

    public static final String PROP_CONNECTION_TARGET = "connection_target";

    @SuppressWarnings("deprecation")
    public static final String PROP_DRIVER_TYPE = DBConstants.INTERNAL_PROP_PREFIX + "driver-type@";

    @SuppressWarnings("deprecation")
    public static final String PROP_INTERNAL_LOGON = DBConstants.INTERNAL_PROP_PREFIX + "internal-logon@";

    public static final String PROP_AUTH_LOGON_AS = "dm.logon-as";

    @SuppressWarnings("deprecation")
    public static final String PROP_SESSION_LANGUAGE = DBConstants.INTERNAL_PROP_PREFIX + "session-language@";

    @SuppressWarnings("deprecation")
    public static final String PROP_SESSION_TERRITORY = DBConstants.INTERNAL_PROP_PREFIX + "session-territory@";

    @SuppressWarnings("deprecation")
    public static final String PROP_SESSION_NLS_DATE_FORMAT = DBConstants.INTERNAL_PROP_PREFIX
        + "session-nls-date-format@";

    public static final String PROP_SESSION_NLS_TIMESTAMP_FORMAT = "session-nls-timestamp-format";

    public static final String PROP_SESSION_NLS_LENGTH_FORMAT = "session-nls-length-format";

    public static final String PROP_SESSION_NLS_CURRENCY_FORMAT = "session-nls-currency-format";

    public static final String PROP_SHOW_ONLY_ONE_SCHEMA = "show-only-one-schema@";

    @SuppressWarnings("deprecation")
    public static final String PROP_CHECK_SCHEMA_CONTENT = DBConstants.INTERNAL_PROP_PREFIX + "check-schema-content@";

    @SuppressWarnings("deprecation")
    public static final String PROP_ALWAYS_SHOW_DBA = DBConstants.INTERNAL_PROP_PREFIX + "always-show-dba@";

    @SuppressWarnings("deprecation")
    public static final String PROP_ALWAYS_USE_DBA_VIEWS = DBConstants.INTERNAL_PROP_PREFIX + "always-use-dba-views@";

    @SuppressWarnings("deprecation")
    public static final String PROP_USE_RULE_HINT = DBConstants.INTERNAL_PROP_PREFIX + "use-rule-hint@";

    @SuppressWarnings("deprecation")
    public static final String PROP_USE_META_OPTIMIZER = DBConstants.INTERNAL_PROP_PREFIX + "use-meta-optimizer@";

    @SuppressWarnings("deprecation")
    public static final String PROP_METADATA_USE_SYS_SCHEMA = DBConstants.INTERNAL_PROP_PREFIX + "meta-use-sys-schema@";

    @SuppressWarnings("deprecation")
    public static final String PROP_METADATA_USE_SIMPLE_CONSTRAINTS = DBConstants.INTERNAL_PROP_PREFIX
        + "meta-use-simple-constraints@";

    @SuppressWarnings("deprecation")
    public static final String PROP_METADATA_USE_ALTERNATIVE_TABLE_QUERY = DBConstants.INTERNAL_PROP_PREFIX
        + "meta-use-alternative-table-query@";

    public static final String PROP_SEARCH_METADATA_IN_SYNONYMS = "dm.meta-search-in-synonyms";

    public static final String PROP_SHOW_DATE_AS_DATE = "dm.show-date-as-date";

    public static final String USER_PUBLIC = "PUBLIC";

    public static final String AUTHID_OPTION_CURRENT_USER = "CURRENT_USER";

    public static final String AUTHID_OPTION_DEFINER = "DEFINER";

    public static final String YES = "YES";

    public static final String TYPE_NAME_XML = "XMLTYPE";

    public static final String TYPE_FQ_XML = "SYS.XMLTYPE";

    public static final String TYPE_DMGEO2 = "SYSGEO2.ST_GEOMETRY";

    public static final String TYPE_DMRASTER = "SYSRASTER.ST_RASTER";

    public static final String TYPE_DMTOPOLOGY = "SYSTOPOLOGY.TOPOGEOMETRY";

    public static final String TYPE_NAME_BFILE = "BFILE";

    public static final String TYPE_NAME_CFILE = "CFILE";

    public static final String TYPE_CONTENT_POINTER = "CONTENT POINTER";

    public static final String TYPE_NAME_DATE = "DATE";

    public static final String TYPE_NAME_ROWID = "ROWID";

    public static final String TYPE_NAME_VARCHAR2 = "VARCHAR2";

    public static final String TYPE_CLOB = "CLOB";

    public static final String TYPE_NAME_TIMESTAMP = "TIMESTAMP";

    public static final String TYPE_NUMBER = "NUMBER";

    public static final String TYPE_DECIMAL = "DECIMAL";

    public static final String TYPE_NAME_REFCURSOR = "REFCURSOR";

    public static final String TYPE_LONG = "LONG";

    public static final String TYPE_LONG_RAW = "LONG RAW";

    public static final String TYPE_OCTET = "OCTET";

    public static final String TYPE_INTERVAL_YEAR_MONTH = "INTERVAL YEAR TO MONTH";

    public static final String TYPE_INTERVAL_DAY_SECOND = "INTERVAL DAY TO SECOND";

    public static final String TYPE_NAME_BLOB = "BLOB";

    public static final String TYPE_NAME_NUMERIC = "NUMERIC";

    public static final String TYPE_UUID = "STRING AS UUID";

    public static final String TYPE_BOOLEAN = "BOOLEAN";

    public static final String OPERATION_MODIFY = "MODIFY";

    public static final int TIMESTAMP_TYPE_LENGTH = 13;

    public static final int DATE_TYPE_LENGTH = 7;

    public static final DBSIndexType INDEX_TYPE_NORMAL = new DBSIndexType("NORMAL", "Normal");

    public static final DBSIndexType INDEX_TYPE_BITMAP = new DBSIndexType("BITMAP", "Bitmap");

    public static final DBSIndexType INDEX_TYPE_FUNCTION_BASED_NORMAL = new DBSIndexType("FUNCTION-BASED NORMAL",
        "Function-based Normal");

    public static final DBSIndexType INDEX_TYPE_FUNCTION_BASED_BITMAP = new DBSIndexType("FUNCTION-BASED BITMAP",
        "Function-based Bitmap");

    public static final DBSIndexType INDEX_TYPE_DOMAIN = new DBSIndexType("DOMAIN", "Domain");

    public static final String PROP_OBJECT_DEFINITION = "objectDefinitionText";

    public static final String PROP_OBJECT_BODY_DEFINITION = "extendedDefinitionText";

    public static final String COL_OWNER = "OWNER";

    public static final String COL_TABLE_NAME = "TABLE_NAME";

    public static final String COL_CONSTRAINT_NAME = "CONSTRAINT_NAME";

    public static final String COL_CONSTRAINT_TYPE = "CONSTRAINT_TYPE";
    public static final String COLUMN_STATUS = "STATUS";
    public static final String XML_COLUMN_NAME = "XML";
    public static final String OBJECT_VALUE_COLUMN_NAME = "OBJECT_VALUE";
    public static final DBDPseudoAttribute PSEUDO_ATTR_ROWID = new DBDPseudoAttribute(DBDPseudoAttributeType.ROWID,
        "ROWID", "$alias.ROWID", null, "Unique row identifier", true,
        DBDPseudoAttribute.PropagationPolicy.TABLE_LOCAL);
    public static final String PREF_EXPLAIN_TABLE_NAME = "dm.explain.table";
    public static final String PREF_SUPPORT_ROWID = "dm.support.rowid";
    public static final String PREF_DBMS_OUTPUT = "dm.dbms.output";
    public static final String PREF_DBMS_READ_ALL_SYNONYMS = "dm.read.all.synonyms";
    public static final String PREF_DISABLE_SCRIPT_ESCAPE_PROCESSING = "dm.disable.script.escape";
    public static final String NLS_DEFAULT_VALUE = "Default";
    public static final String PREF_KEY_DDL_FORMAT = "dm.ddl.format";
    public static final int MAXIMUM_DBMS_OUTPUT_SIZE = 1000000;
    public static final String VAR_DM_HOME = "DM_HOME";
    public static final String VAR_PATH = "PATH";
    public static final int DATA_TYPE_TIMESTAMP_WITH_TIMEZONE = 101;
    public static final int DATA_TYPE_TIMESTAMP_WITH_LOCAL_TIMEZONE = 102;
    public static final DBSEntityConstraintType CONSTRAINT_WITH_CHECK_OPTION = new DBSEntityConstraintType("V",
        "With Check Option", null, false, false, false, false);
    public static final DBSEntityConstraintType CONSTRAINT_WITH_READ_ONLY = new DBSEntityConstraintType("O",
        "With Read Only", null, false, false, false, false);
    public static final DBSEntityConstraintType CONSTRAINT_HASH_EXPRESSION = new DBSEntityConstraintType("H",
        "Hash expression", null, false, false, false, false);
    public static final DBSEntityConstraintType CONSTRAINT_REF_COLUMN = new DBSEntityConstraintType("F",
        "Constraint that involves a REF column", null, false, false, false, false);
    public static final DBSEntityConstraintType CONSTRAINT_SUPPLEMENTAL_LOGGING = new DBSEntityConstraintType("S",
        "Supplemental logging", null, false, false, false, false);
    /**
     * Dm error codes
     */
    public static final int EC_FEATURE_NOT_SUPPORTED = 17023;
    public static final int EC_NO_RESULTSET_AVAILABLE = 17283;
    public static final int EC_PASSWORD_EXPIRED = 28001;
    public static final int EC_PASSWORD_WILL_EXPIRE = 28002;
    public static final int NUMERIC_MAX_PRECISION = 38;
    public static final int INTERVAL_DEFAULT_SECONDS_PRECISION = 6;
    public static final int INTERVAL_DEFAULT_YEAR_DAY_PRECISION = 2;
    public static final String XMLTYPE_CLASS_NAME = "dm.xdb.XMLType";
    public static final String BFILE_CLASS_NAME = "dm.sql.BFILE";
    public static final String TIMESTAMP_CLASS_NAME = "dm.sql.TIMESTAMP";
    public static final String TIMESTAMPTZ_CLASS_NAME = "dm.sql.TIMESTAMPTZ";
    public static final String TIMESTAMPLTZ_CLASS_NAME = "dm.sql.TIMESTAMPLTZ";
    public static final String PLAN_TABLE_DEFINITION = "create global temporary table ${TABLE_NAME} (\n"
        + "statement_id varchar2(30),\n" + "plan_id number,\n" + "timestamp date,\n" + "remarks varchar2(4000),\n"
        + "operation varchar2(30),\n" + "options varchar2(255),\n" + "object_node varchar2(128),\n"
        + "object_owner varchar2(30),\n" + "object_name varchar2(30),\n" + "object_alias varchar2(65),\n"
        + "object_instance numeric,\n" + "object_type varchar2(30),\n" + "optimizer varchar2(255),\n"
        + "search_columns number,\n" + "id numeric,\n" + "parent_id numeric,\n" + "depth numeric,\n"
        + "position numeric,\n" + "cost numeric,\n" + "cardinality numeric,\n" + "bytes numeric,\n"
        + "other_tag varchar2(255),\n" + "partition_start varchar2(255),\n" + "partition_stop varchar2(255),\n"
        + "partition_id numeric,\n" + "other long,\n" + "distribution varchar2(30),\n" + "cpu_cost numeric,\n"
        + "io_cost numeric,\n" + "temp_space numeric,\n" + "access_predicates varchar2(4000),\n"
        + "filter_predicates varchar2(4000),\n" + "projection varchar2(4000),\n" + "time numeric,\n"
        + "qblock_name varchar2(30),\n" + "other_xml clob\n" + ") on commit preserve rows";
    /**
     * Procedure/Function/External Function
     */
    public final static String PASSWORD = "******";
    public static final int OBJTYPE_PROCEDURE = 0x06;
    public static final int OBJTYPE_FUNCTION = 0x07;
    public static final int OBJTYPE_EXTERNAL_FUNCTION = 0x08;
    // Type of External Function
    public static final int JAVA_EXTERNAL_FUNCTION = 'J'; // Java
    public static final int C_EXTERNAL_FUNCTION = 'C'; // C
    public static final int PYTHON2_EXTERNAL_FUNCTION = 'T'; // Python2
    public static final int PYTHON3_EXTERNAL_FUNCTION = 'H'; // Python3
    // icon's Path: platform:/plugin/org.jkiss.dbeaver.model/icons/tree/link.png
    public static final DBIcon ICON_DOMAIN = new DBIcon("domain",
        "platform:/plugin/org.jkiss.dbeaver.ext.damengdb/icons/domain.png");
    public static final DBIcon ICON_UD_OPERATOR = new DBIcon("UD_OPERATOR",
        "platform:/plugin/org.jkiss.dbeaver.ext.damengdb/icons/ud_operator.png");
    public static final String LINE_SEPARATOR = System.getProperty("line.separator");
    public static final String LINUX_LINE_SEPARATOR = "\n";
    public final static int OBJTYPE_ALL = 0;
    public final static int OBJTYPE_SYSTEM = 1;
    public final static int OBJTYPE_USER = 2;
    public static final String[] CHARSET_NAME_ARR_JAVA = new String[] {"GB18030", "UTF-8", "EUC_KR"};
    static final String COLUMN_CREATED = "CREATED";
    static final String VIEW_CREATED = "CRTDATE";
    static final String CREATED = "CRTDATE";
    static final String COLUMN_LAST_DDL_TIME = "LAST_DDL_TIME";
    static final String COLUMN_ID = "ID";
    static final String VIEW_LAST_DDL_TIME = "LAST_REFRESH_DATE";
    static final String COLUMN_OBJECT_NAME = "OBJECT_NAME";
    static final String VIEW_NAME = "NAME";
    static final String VIEW_COMMENT = "VIEW_COMMENT";
    static final String COLUMN_OBJECT_TYPE = "OBJECT_TYPE";
    static final String COLUMN_SUB_TYPE = "SUB_TYPE";
    static final String VIEW_STATUS = "VALID";
    static final String RESULT_STATUS_VALID = "VALID";
    static final String RESULT_YES_VALUE = "Y";
    // Whether the Materialized View can be updated
    static final String RESULT_BOOL_VALUE = "1";
    static final String COLUMN_TEMPORARY = "TEMPORARY";

    /**
     * Connection type
     */
    public enum ConnectionType {
        BASIC, CUSTOM
    }

}
