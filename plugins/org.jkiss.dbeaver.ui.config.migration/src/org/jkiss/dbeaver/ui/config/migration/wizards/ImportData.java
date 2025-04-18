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

package org.jkiss.dbeaver.ui.config.migration.wizards;

import org.jkiss.dbeaver.model.DBPDataSourceFolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Import data
 */
public class ImportData {

    private final List<ImportDriverInfo> drivers = new ArrayList<>();
    private final List<ImportConnectionInfo> connections = new ArrayList<>();
    private DBPDataSourceFolder dataSourceFolder;

    public List<ImportDriverInfo> getDrivers()
    {
        return drivers;
    }

    public ImportDriverInfo getDriver(String name)
    {
        for (ImportDriverInfo driver : drivers) {
            if (name.equals(driver.getName())) {
                return driver;
            }
        }
        return null;
    }

    public ImportDriverInfo getDriverByID(String id)
    {
        for (ImportDriverInfo driver : drivers) {
            if (id.equals(driver.getId())) {
                return driver;
            }
        }
        return null;
    }

    public void addDriver(ImportDriverInfo driverInfo)
    {
        drivers.add(driverInfo);
    }

    public List<ImportConnectionInfo> getConnections()
    {
        return connections;
    }

    public void addConnection(ImportConnectionInfo connectionInfo)
    {
        connections.add(connectionInfo);
    }

    public DBPDataSourceFolder getDataSourceFolder() {
        return dataSourceFolder;
    }

    public void setDataSourceFolder(DBPDataSourceFolder dataSourceFolder) {
        this.dataSourceFolder = dataSourceFolder;
    }
}
