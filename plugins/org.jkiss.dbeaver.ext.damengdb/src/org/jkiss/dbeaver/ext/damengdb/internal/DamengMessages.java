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
package org.jkiss.dbeaver.ext.damengdb.internal;

import org.eclipse.osgi.util.NLS;

public class DamengMessages extends NLS {
	
    static final String BUNDLE_NAME = "org.jkiss.dbeaver.ext.damengdb.internal.DamengMessages"; //$NON-NLS-1$
    public static String edit_dameng_dependencies_dependency_name;
    public static String edit_dameng_dependencies_dependency_description;
    public static String edit_dameng_dependencies_dependent_name;
    public static String edit_dameng_dependencies_dependent_description;
    public static String dameng_password_will_expire_warn_name;
    public static String dameng_password_will_expire_warn_description;
    public static String dameng_server_session_manager_details_name;
    public static String dameng_server_session_manager_details_description;
    public static String dameng_server_session_manager_display_exec_plan_name;
    public static String dameng_server_session_manager_display_exec_plan_description;
    // Dblink
    public static String dameng_dblink_is_public;
    public static String dameng_dblink_is_unpublic;
    // Function/External Function
    public static String dameng_procedures_type_is_procedure;
    public static String dameng_procedures_type_is_function;
    public static String dameng_procedures_type_is_external_function;
    // Index
    public static String dameng_index_property_dialog_uncluster_bitmap_join;
    public static String dameng_index_property_dialog_spatial;
    public static String dameng_index_property_dialog_array;
    public static String dameng_index_property_dialog_cluster;
    public static String dameng_index_property_dialog_uncluster;
    public static String dameng_index_property_dialog_unique;
    public static String dameng_index_property_dialog_bitmap;
    public static String dameng_index_property_dialog_btree;
    public static String dameng_index_property_dialog_function;
    public static String dameng_index_property_dialog_global;
    public static String dameng_index_property_dialog_local;
    public static String dameng_index_property_dialog_index_type_str;
    // User Type
    public static String dameng_user_type_name_dba;
    public static String dameng_user_type_name_audit;
    public static String dameng_user_type_name_policy;
    public static String dameng_user_type_name_dbo;
    public static String dameng_user_type_name_sys;
    // Password Policy
    public static String dameng_sql_util_passwordPolicy1;
    public static String dameng_sql_util_passwordPolicy2;
    public static String dameng_sql_util_passwordPolicy3;
    public static String dameng_sql_util_passwordPolicy4;
    public static String dameng_sql_util_passwordPolicy5;
    public static String dameng_sql_util_passwordPolicy6;
    public static String dameng_sql_util__val_def;
    // The error of the system index ddl
    public static String dameng_system_index_ddl_error;
    // Label of the execution plan's result set
    public static String dameng_execution_plan_id_title;
    public static String dameng_execution_plan_name_title;
    public static String dameng_execution_plan_content_title;
    public static String dameng_execution_plan_cost_title;
    public static String dameng_execution_plan_cardinality_title;
    public static String dameng_execution_plan_cpu_cost_title;
    public static String dameng_execution_plan_description_title;

    static {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, DamengMessages.class);
    }

    private DamengMessages() {
    }

}
