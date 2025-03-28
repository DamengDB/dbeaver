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
package org.jkiss.dbeaver.ext.damengdb.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.damengdb.model.DamengConstants;
import org.jkiss.dbeaver.utils.RuntimeUtils;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.IOUtils;
import org.jkiss.utils.StandardConstants;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

public class DamengEnvUtils {
	
    public static final String WIN_REG_DM = "SOFTWARE\\DAMENG";
    public static final String WIN_REG_DM_HOME = "DM_HOME";
    public static final String WIN_REG_DM_HOME_NAME = "NAME";
    private static final Log log = Log.getLog(DamengEnvUtils.class);
    private static final List<DamengHomeDescriptor> dmHomes = new ArrayList<>();

    private static boolean dmHomesSearched = false;

    public static List<DamengHomeDescriptor> getDmHomes() {
        checkDmHomes();
        return dmHomes;
    }

    @Nullable
    public static DamengHomeDescriptor getDefaultDmHome() {
        List<DamengHomeDescriptor> dmHomes = getDmHomes();
        return dmHomes.isEmpty() ? null : dmHomes.get(0);
    }

    @Nullable
    public static File getDefaultDmHomePath() {
        DamengHomeDescriptor defaultDmHome = getDefaultDmHome();
        return defaultDmHome == null ? null : defaultDmHome.getPath();
    }

    private static boolean checkDmHomes() {
        if (!dmHomesSearched) {
            findDmHomes();
            dmHomesSearched = true;
        }
        return !dmHomes.isEmpty();
    }

    public static DamengHomeDescriptor getDmHome(String dmHome) {
        if (CommonUtils.isEmpty(dmHome) || !checkDmHomes()) {
            return null;
        }
        for (DamengHomeDescriptor home : dmHomes) {
            if (equalsFileName(home.getName(), dmHome)) {
                return home;
            }
        }
        return null;
    }

    public static DamengHomeDescriptor getDmHomeByName(String dmHomeName) {
        if (CommonUtils.isEmpty(dmHomeName) || !checkDmHomes()) {
            return null;
        }
        for (DamengHomeDescriptor home : dmHomes) {
            if (equalsFileName(home.getDisplayName(), dmHomeName)) {
                return home;
            }
        }
        return null;
    }

    private static boolean equalsFileName(String file1, String file2) {
        if (RuntimeUtils.isWindows()) {
            return file1.equalsIgnoreCase(file2);
        } else {
            return file1.equals(file2);
        }
    }

    public static DamengHomeDescriptor addDmHome(String dmHome) throws DBException {
        if (CommonUtils.isEmpty(dmHome)) {
            return null;
        }

        dmHome = CommonUtils.removeTrailingSlash(dmHome);

        boolean contains = false;
        for (DamengHomeDescriptor home : dmHomes) {
            if (equalsFileName(home.getName(), dmHome)) {
                contains = true;
                break;
            }
        }
        if (!contains) {
            DamengHomeDescriptor homeDescriptor = new DamengHomeDescriptor(dmHome);
            dmHomes.add(0, homeDescriptor);
            return homeDescriptor;
        }
        return null;
    }

    private static void findDmHomes() {
        String path = System.getenv(DamengConstants.VAR_PATH);
        if (path != null) {
            for (String token : path.split(System.getProperty(StandardConstants.ENV_PATH_SEPARATOR))) {
                if (token.toLowerCase().contains("dmdbms")) {
                    token = CommonUtils.removeTrailingSlash(token);
                    if (token.toLowerCase().endsWith("bin")) {
                        String dmHome = token.substring(0, token.length() - 3);
                        try {
                            addDmHome(dmHome);
                        } catch (DBException ex) {
                            log.warn("Wrong DM_HOME: " + dmHome, ex);
                        }
                    }
                }
            }
        }

        String dmHome = System.getenv(DamengConstants.VAR_DM_HOME);
        if (dmHome != null) {
            try {
                addDmHome(dmHome);
            } catch (DBException ex) {
                log.warn("Wrong DM_HOME: " + dmHome, ex);
            }
        }

        // find dm home in Windows registry
        if (RuntimeUtils.isWindows()) {
            try {
                if (Advapi32Util.registryKeyExists(WinReg.HKEY_LOCAL_MACHINE, WIN_REG_DM)) {
                    String[] dmKeys = Advapi32Util.registryGetKeys(WinReg.HKEY_LOCAL_MACHINE, WIN_REG_DM);
                    if (dmKeys != null) {
                        for (String dmKey : dmKeys) {
                            Map<String, Object> valuesMap = Advapi32Util.registryGetValues(WinReg.HKEY_LOCAL_MACHINE,
                                WIN_REG_DM + "\\" + dmKey);
                            for (String key : valuesMap.keySet()) {
                                if (WIN_REG_DM_HOME.equals(key)) {
                                    try {
                                        dmHome = CommonUtils.toString(valuesMap.get(key));
                                        addDmHome(dmHome);
                                    } catch (DBException ex) {
                                        log.warn("Wrong DM_HOME: " + dmHome, ex);
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error reading Windows registry", e);
            }
        }
    }

    public static String readWinRegistry(String dmHome, String name) {
        if (RuntimeUtils.isWindows()) {
            try {
                if (Advapi32Util.registryKeyExists(WinReg.HKEY_LOCAL_MACHINE, WIN_REG_DM)) {
                    String[] dmKeys = Advapi32Util.registryGetKeys(WinReg.HKEY_LOCAL_MACHINE, WIN_REG_DM);
                    if (dmKeys != null) {
                        for (String dmKey : dmKeys) {
                            String keyName = WIN_REG_DM + "\\" + dmKey;
                            if (Advapi32Util.registryValueExists(WinReg.HKEY_LOCAL_MACHINE, keyName, WIN_REG_DM_HOME)) {
                                String home = Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, keyName,
                                    WIN_REG_DM_HOME);
                                if (dmHome.equals(home)) {
                                    return Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, keyName,
                                        name);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error reading Windows registry", e);
            }
        }
        return null;
    }

    public static String getFullDmVersion(String dmHome) {
        String disql = CommonUtils.makeDirectoryName(dmHome) + "bin/DIsql";
        try {
            Process p = Runtime.getRuntime().exec(new String[] {disql, "-id"});
            try {
                BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
                try {
                    String line;
                    while ((line = input.readLine()) != null) {
                        if (line.startsWith("DM Database")) {
                            return line;
                        }
                    }
                } finally {
                    IOUtils.close(input);
                }
            } finally {
                p.destroy();
            }
        } catch (Exception ex) {
            log.warn("Error reading dm version from " + disql, ex);
        }
        return null;
    }
    
}
