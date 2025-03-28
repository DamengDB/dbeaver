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
package org.jkiss.dbeaver.ext.damengdb;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.FileLocator;
import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.damengdb.model.DamengConstants;
import org.jkiss.dbeaver.ext.damengdb.model.DamengDataSource;
import org.jkiss.dbeaver.ext.damengdb.util.DamengEnvUtils;
import org.jkiss.dbeaver.ext.damengdb.util.DamengHomeDescriptor;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBPInformationProvider;
import org.jkiss.dbeaver.model.DBPObject;
import org.jkiss.dbeaver.model.DatabaseURL;
import org.jkiss.dbeaver.model.access.DBAUserCredentialsProvider;
import org.jkiss.dbeaver.model.app.DBPPlatform;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.connection.DBPDriver;
import org.jkiss.dbeaver.model.connection.DBPDriverConfigurationType;
import org.jkiss.dbeaver.model.connection.DBPDriverLibrary;
import org.jkiss.dbeaver.model.connection.DBPNativeClientLocation;
import org.jkiss.dbeaver.model.connection.DBPNativeClientLocationManager;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCDataSourceProvider;
import org.jkiss.dbeaver.model.net.DBWUtils;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.registry.driver.DriverLibraryAbstract;
import org.jkiss.utils.CommonUtils;

public class DamengDataSourceProvider extends JDBCDataSourceProvider implements DBAUserCredentialsProvider, DBPNativeClientLocationManager, DBPInformationProvider {
	
    public DamengDataSourceProvider() {
    }

    public static Integer getDmVersion(DBPNativeClientLocation location) {
        return 8;
    }

    @Override
    public void init(@NotNull DBPPlatform platform) {
        super.init(platform);

        try {
            DBPDriver driver = platform.getDataSourceProviderRegistry().getDataSourceProvider("damengdb").getDrivers()
                .get(0);
            initDriverPath(driver);
        } catch (Exception e) {
        }
    }

    private void initDriverPath(DBPDriver driver) {
        List<? extends DBPDriverLibrary> driverLibraries = driver.getDriverLibraries();
        for (DBPDriverLibrary lib : driverLibraries) {
            if (!lib.isCustom() && lib.getType().equals(DBPDriverLibrary.FileType.jar)) {
                URL url = ((DriverLibraryAbstract) lib).getDriver().getProviderDescriptor().getContributorBundle()
                    .getEntry(((DriverLibraryAbstract) lib).getPath());
                if (url != null) {
                    try {
                        url = FileLocator.toFileURL(url);
                    } catch (IOException ex) {
                        log.warn(ex);
                    }
                    if (url != null) {
                        try {
                            ((DriverLibraryAbstract) lib).setPath(new File(url.toURI()).getAbsolutePath());
                            break;
                        } catch (URISyntaxException e) {
                        }
                    }
                }
            }
        }
    }

    @Override
    public long getFeatures() {
        return FEATURE_SCHEMAS;
    }

    @Override
    public String getConnectionURL(DBPDriver driver, DBPConnectionConfiguration connectionInfo) {
        if (connectionInfo.getConfigurationType() == DBPDriverConfigurationType.URL) {
            return connectionInfo.getUrl();
        }
        if (driver.isSampleURLApplicable()) {
            return DatabaseURL.generateUrlByTemplate(driver, connectionInfo);
        }

        StringBuilder url = new StringBuilder(100);
        url.append("jdbc:dm://");

        url.append(connectionInfo.getHostName());
        if (!CommonUtils.isEmpty(connectionInfo.getHostPort())) {
            url.append(":").append(connectionInfo.getHostPort());
        }

        return url.toString();
    }

    @Override
    public DBPDataSource openDataSource(@NotNull DBRProgressMonitor monitor, @NotNull DBPDataSourceContainer container)
        throws DBException {
        DBPDriver driver = container.getDriver();
        initDriverPath(driver);

        return new DamengDataSource(monitor, container);
    }

    @NotNull
    private DamengConstants.ConnectionType getConnectionType(DBPConnectionConfiguration connectionInfo) {
        DamengConstants.ConnectionType connectionType;
        String conTypeProperty = connectionInfo.getProviderProperty(DamengConstants.PROP_CONNECTION_TYPE);
        if (conTypeProperty != null) {
            connectionType = DamengConstants.ConnectionType.valueOf(CommonUtils.toString(conTypeProperty));
        } else {
            connectionType = DamengConstants.ConnectionType.BASIC;
        }
        return connectionType;
    }

    @Override
    public List<DBPNativeClientLocation> findLocalClientLocations() {
        List<DBPNativeClientLocation> homeIds = new ArrayList<>();
        for (DamengHomeDescriptor home : DamengEnvUtils.getDmHomes()) {
            homeIds.add(home);
        }
        return homeIds;
    }

    @Override
    public DBPNativeClientLocation getDefaultLocalClientLocation() {
        return DamengEnvUtils.getDefaultDmHome();
    }

    @Override
    public String getProductName(DBPNativeClientLocation location) {
        Integer dmVersion = getDmVersion(location);
        return "DM" + (dmVersion == null ? "" : " " + dmVersion);
    }

    @Override
    public String getProductVersion(DBPNativeClientLocation location) {
        return DamengEnvUtils.getFullDmVersion(location.getName());
    }

    @Override
    public String getConnectionUserName(@NotNull DBPConnectionConfiguration connectionInfo) {
        return connectionInfo.getUserName();
    }

    @Override
    public String getConnectionUserPassword(@NotNull DBPConnectionConfiguration connectionInfo) {
        return connectionInfo.getUserPassword();
    }

    @Override
    public String getObjectInformation(@NotNull DBPObject object, @NotNull String infoType) {
        if (object instanceof DBPDataSourceContainer ds && infoType.equals(INFO_TARGET_ADDRESS)) {
            DBPConnectionConfiguration connectionInfo = ds.getConnectionConfiguration();
            DamengConstants.ConnectionType connectionType = getConnectionType(connectionInfo);
            if (connectionType == DamengConstants.ConnectionType.CUSTOM) {
                return DatabaseURL.generateUrlByTemplate(connectionInfo.getUrl(), connectionInfo);
            }

            String hostName = DBWUtils.getTargetTunnelHostName(ds, connectionInfo);
            String hostPort = connectionInfo.getHostPort();
            if (CommonUtils.isEmpty(hostName)) {
                return null;
            } else if (CommonUtils.isEmpty(hostPort)) {
                return hostName;
            } else {
                return hostName + ":" + hostPort;
            }
        }
        return null;
    }
    
}
