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
package org.jkiss.dbeaver.ext.damengdb.ui.views;

import java.util.Locale;

import org.eclipse.jface.dialogs.IDialogPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;
import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.ext.damengdb.model.DamengConstants;
import org.jkiss.dbeaver.ext.damengdb.ui.internal.DamengUIMessages;
import org.jkiss.dbeaver.model.DBConstants;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.connection.DBPDriverConfigurationType;
import org.jkiss.dbeaver.registry.DBConnectionConstants;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.ui.IDialogPageProvider;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.dialogs.connection.ClientHomesSelector;
import org.jkiss.dbeaver.ui.dialogs.connection.ConnectionPageWithAuth;
import org.jkiss.dbeaver.ui.dialogs.connection.DriverPropertiesDialogPage;
import org.jkiss.utils.CommonUtils;

/**
 * DamengConnectionPage
 */
public class DamengConnectionPage extends ConnectionPageWithAuth implements IDialogPageProvider {

    private Text hostText;

    private Text portText;

    private TabFolder connectionTypeFolder;

    private ClientHomesSelector oraHomeSelector;

    private Text connectionUrlText;

    private ControlsListener controlModifyListener;

    private DamengConstants.ConnectionType connectionType = DamengConstants.ConnectionType.BASIC;

    private boolean activated = false;

    private Image logoImage;

    public DamengConnectionPage() {
        logoImage = createImage("icons/dameng_logo.png");
    }

    @Override
    public void dispose() {
        super.dispose();
        UIUtils.dispose(logoImage);
    }

    @Override
    public Image getImage() {
        return logoImage;
    }

    @Override
    public void createControl(Composite composite) {
        controlModifyListener = new ControlsListener();

        Composite addrGroup = new Composite(composite, SWT.NONE);
        addrGroup.setLayout(new GridLayout(1, false));
        addrGroup.setLayoutData(new GridData(GridData.FILL_BOTH));

        UIUtils.createControlLabel(addrGroup, DamengUIMessages.dialog_connection_connection_type_group);

        connectionTypeFolder = new TabFolder(addrGroup, SWT.TOP | SWT.MULTI);
        connectionTypeFolder.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        createBasicConnectionControls(connectionTypeFolder);
        createCustomConnectionControls(connectionTypeFolder);
        connectionTypeFolder.setSelection(connectionType.ordinal());
        connectionTypeFolder.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                connectionType = (DamengConstants.ConnectionType) connectionTypeFolder.getSelection()[0].getData();
                site.getActiveDataSource().getConnectionConfiguration()
                    .setProviderProperty(DamengConstants.PROP_CONNECTION_TYPE, connectionType.name());
                updateUI();
            }
        });

        createAuthPanel(addrGroup, 1);
        Composite bottomControls = UIUtils.createPlaceholder(addrGroup, 3);
        bottomControls.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        if (DBWorkbench.hasFeature(DBConnectionConstants.PRODUCT_FEATURE_ADVANCED_DATABASE_ADMINISTRATION)) {
            createClientHomeGroup(bottomControls);
        }

        createDriverPanel(addrGroup);
        setControl(addrGroup);
    }

    private void createBasicConnectionControls(TabFolder protocolFolder) {
        TabItem protocolTabBasic = new TabItem(protocolFolder, SWT.NONE);
        protocolTabBasic.setText(DamengUIMessages.dialog_connection_basic_tab);
        protocolTabBasic.setData(DamengConstants.ConnectionType.BASIC);

        Composite targetContainer = new Composite(protocolFolder, SWT.NONE);
        targetContainer.setLayout(new GridLayout(5, false));
        targetContainer.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        protocolTabBasic.setControl(targetContainer);

        Label hostLabel = UIUtils.createControlLabel(targetContainer, DamengUIMessages.dialog_connection_host);
        GridData gd = new GridData(GridData.HORIZONTAL_ALIGN_END);
        hostLabel.setLayoutData(gd);

        hostText = new Text(targetContainer, SWT.BORDER);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 2;
        hostText.setLayoutData(gd);
        hostText.addModifyListener(controlModifyListener);

        UIUtils.createControlLabel(targetContainer, DamengUIMessages.dialog_connection_port);

        portText = new Text(targetContainer, SWT.BORDER);
        gd = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
        gd.widthHint = UIUtils.getFontHeight(portText) * 5;
        portText.setLayoutData(gd);
        portText.addVerifyListener(UIUtils.getIntegerVerifyListener(Locale.getDefault()));
        portText.addModifyListener(controlModifyListener);
    }

    private void createCustomConnectionControls(TabFolder protocolFolder) {
        TabItem protocolTabCustom = new TabItem(protocolFolder, SWT.NONE);
        protocolTabCustom.setText(DamengUIMessages.dialog_connection_custom_tab);
        protocolTabCustom.setData(DamengConstants.ConnectionType.CUSTOM);

        Composite targetContainer = new Composite(protocolFolder, SWT.NONE);
        targetContainer.setLayout(new GridLayout(2, false));
        targetContainer.setLayoutData(new GridData(GridData.FILL_BOTH));
        protocolTabCustom.setControl(targetContainer);

        final Label urlLabel = UIUtils.createControlLabel(targetContainer, "JDBC URL Template"); //$NON-NLS-1$
        urlLabel.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));

        connectionUrlText = new Text(targetContainer, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData gd = new GridData(GridData.FILL_BOTH);
        gd.widthHint = UIUtils.getFontHeight(connectionUrlText) * 30;
        gd.heightHint = UIUtils.getFontHeight(connectionUrlText) * 3;
        connectionUrlText.setLayoutData(gd);
        connectionUrlText.addModifyListener(controlModifyListener);
    }

    private void createClientHomeGroup(Composite bottomControls) {
        oraHomeSelector = new ClientHomesSelector(bottomControls, DamengUIMessages.dialog_connection_dameng_home);
        GridData gd = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
        gd.widthHint = UIUtils.getFontHeight(bottomControls) * 30;
        oraHomeSelector.getPanel().setLayoutData(gd);

        Label ph = new Label(bottomControls, SWT.NONE);
        ph.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    }

    @Override
    public boolean isComplete() {
        if (!super.isComplete()) {
            return false;
        }
        switch (connectionType) {
            case BASIC:
                return !CommonUtils.isEmpty(hostText.getText());
            case CUSTOM:
                return !CommonUtils.isEmpty(connectionUrlText.getText());
            default:
                return false;
        }
    }

    @Override
    protected boolean isCustomURL() {
        return this.connectionType == DamengConstants.ConnectionType.CUSTOM;
    }

    @Override
    public void loadSettings() {
        super.loadSettings();

        // Load values from new connection info
        DBPConnectionConfiguration connectionInfo = site.getActiveDataSource().getConnectionConfiguration();

        if (oraHomeSelector != null) {
            oraHomeSelector.populateHomes(site.getDriver(), connectionInfo.getClientHomeId(), site.isNew());
        }

        String conTypeProperty = connectionInfo.getProviderProperty(DamengConstants.PROP_CONNECTION_TYPE);
        if (conTypeProperty != null) {
            connectionType = DamengConstants.ConnectionType.valueOf(CommonUtils.toString(conTypeProperty));
        } else {
            connectionType = DamengConstants.ConnectionType.BASIC;
        }
        connectionTypeFolder.setSelection(connectionType.ordinal());
        if (site.isNew() && CommonUtils.isEmpty(connectionInfo.getHostName())) {
            hostText.setText(DBConstants.HOST_LOCALHOST);
        } else {
            hostText.setText(connectionInfo.getHostName());
        }
        if (!CommonUtils.isEmpty(connectionInfo.getHostPort())) {
            portText.setText(connectionInfo.getHostPort());
        } else {
            portText.setText(CommonUtils.notEmpty(site.getDriver().getDefaultPort()));
        }
        connectionUrlText.setText(CommonUtils.notEmpty(connectionInfo.getUrl()));
        activated = true;
    }

    @NotNull
    @Override
    protected String getDefaultAuthModelId(DBPDataSourceContainer dataSource) {
        return "dm_native";
    }

    @Override
    public void saveSettings(DBPDataSourceContainer dataSource) {
        DBPConnectionConfiguration connectionInfo = dataSource.getConnectionConfiguration();
        if (oraHomeSelector != null) {
            connectionInfo.setClientHomeId(oraHomeSelector.getSelectedHome());
        }

        connectionInfo.setProviderProperty(DamengConstants.PROP_CONNECTION_TYPE, connectionType.name());
        switch (connectionType) {
            case BASIC:
                connectionInfo.setHostName(hostText.getText().trim());
                connectionInfo.setHostPort(portText.getText().trim());
                connectionInfo.setConfigurationType(DBPDriverConfigurationType.MANUAL);
                break;
            case CUSTOM:
                connectionInfo.setUrl(connectionUrlText.getText().trim());
                connectionInfo.setHostName(hostText.getText().trim());
                connectionInfo.setHostPort(portText.getText().trim());
                connectionInfo.setConfigurationType(DBPDriverConfigurationType.URL);
                break;
        }
        super.saveSettings(dataSource);
    }

    private void updateUI() {
        if (activated) {
            site.updateButtons();
        }
    }

    @Override
    public IDialogPage[] getDialogPages(boolean extrasOnly, boolean forceCreate) {
        return new IDialogPage[] {new DriverPropertiesDialogPage(this)};
    }

    private class ControlsListener implements ModifyListener, SelectionListener {
        @Override
        public void modifyText(ModifyEvent e) {
            updateUI();
        }

        @Override
        public void widgetSelected(SelectionEvent e) {
            updateUI();
        }

        @Override
        public void widgetDefaultSelected(SelectionEvent e) {
            updateUI();
        }
    }
    
}
