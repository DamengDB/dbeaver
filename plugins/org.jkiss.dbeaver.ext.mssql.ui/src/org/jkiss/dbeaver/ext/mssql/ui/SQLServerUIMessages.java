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
package org.jkiss.dbeaver.ext.mssql.ui;

import org.jkiss.dbeaver.utils.NLS;

public class SQLServerUIMessages extends NLS {
    static final String BUNDLE_NAME = "org.jkiss.dbeaver.ext.mssql.ui.SQLServerUIMessages"; //$NON-NLS-1$

    public static String dialog_setting_connection_settings;

    public static String dialog_connection_windows_authentication_button;
    public static String dialog_connection_authentication_combo;
    public static String dialog_connection_authentication_combo_tip;
    public static String dialog_connection_adp_authentication_button;
    public static String dialog_connection_database_schema_label;
    public static String dialog_connection_host_label;
    public static String dialog_connection_password_label;
    public static String dialog_connection_port_label;
    public static String dialog_connection_user_name_label;
    public static String dialog_setting_show_all_databases;
    public static String dialog_setting_show_all_databases_tip;
    public static String dialog_setting_show_all_schemas;
    public static String dialog_setting_show_all_schemas_tip;
    public static String dialog_setting_encrypt_password;
    public static String dialog_setting_encrypt_password_tip;
    public static String dialog_setting_trust_server_certificate;
    public static String dialog_setting_trust_server_certificate_tip;
    public static String dialog_setting_ssl_advanced_hostname_label;
    public static String dialog_setting_ssl_advanced_hostname_tip;

    public static String dialog_create_db_group_general;
    public static String dialog_create_db_label_db_name;
    public static String dialog_create_db_title;

    public static String dialog_create_login_shell_title;

    public static String dialog_create_check_constraint_title;

    public static String dialog_create_procedure_title;

    static {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, SQLServerUIMessages.class);
    }

    private SQLServerUIMessages() {
    }


}
