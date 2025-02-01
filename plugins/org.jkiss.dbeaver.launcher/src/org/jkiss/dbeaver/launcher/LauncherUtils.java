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
package org.jkiss.dbeaver.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;

public class LauncherUtils {

    /*
     * Build an array of path suffixes based on the given NL which is suitable
     * for splash path searching.  The returned array contains paths in order
     * from most specific to most generic. So, in the FR_fr locale, it will return
     * candidates in "nl/fr/FR/", then "nl/fr/", and finally in the root.
     * Candidate names are defined in SPLASH_IMAGES and include splash.png, splash.jpg, etc.
     */
    static String[] buildNLVariants(String locale) {
        //build list of suffixes for loading resource bundles
        String nl = locale;
        ArrayList<String> result = new ArrayList<>(4);
        int lastSeparator;
        while (true) {
            for (String name : LauncherConstants.SPLASH_IMAGES) {
                result.add("nl" + File.separatorChar + nl.replace('_', File.separatorChar) + File.separatorChar + name); //$NON-NLS-1$
            }
            lastSeparator = nl.lastIndexOf('_');
            if (lastSeparator == -1)
                break;
            nl = nl.substring(0, lastSeparator);
        }
        //add the empty suffix last (most general)
        Collections.addAll(result, LauncherConstants.SPLASH_IMAGES);
        return result.toArray(new String[0]);
    }

    public static String substituteVars(String path) {
        StringBuilder buf = new StringBuilder(path.length());
        StringTokenizer st = new StringTokenizer(path, LauncherConstants.VARIABLE_DELIM_STRING, true);
        boolean varStarted = false; // indicates we are processing a var subtitute
        String var = null; // the current var key
        while (st.hasMoreElements()) {
            String tok = st.nextToken();
            if (LauncherConstants.VARIABLE_DELIM_STRING.equals(tok)) {
                if (!varStarted) {
                    varStarted = true; // we found the start of a var
                    var = ""; //$NON-NLS-1$
                } else {
                    // we have found the end of a var
                    String prop = null;
                    // get the value of the var from system properties
                    if (var != null && !var.isEmpty())
                        prop = System.getProperty(var);
                    if (prop == null) {
                        prop = System.getenv(var);
                    }
                    if (prop != null) {
                        // found a value; use it
                        buf.append(prop);
                    } else {
                        // could not find a value append the var; keep delemiters
                        buf.append(LauncherConstants.VARIABLE_DELIM_CHAR);
                        buf.append(var == null ? "" : var); //$NON-NLS-1$
                        buf.append(LauncherConstants.VARIABLE_DELIM_CHAR);
                    }
                    varStarted = false;
                    var = null;
                }
            } else {
                if (!varStarted)
                    buf.append(tok); // the token is not part of a var
                else
                    var = tok; // the token is the var key; save the key to process when we find the end token
            }
        }
        if (var != null)
            // found a case of $var at the end of the path with no trailing $; just append it as is.
            buf.append(LauncherConstants.VARIABLE_DELIM_CHAR).append(var);
        return buf.toString();
    }

    static boolean canWrite(File installDir) {
        if (!installDir.isDirectory())
            return false;

        if (Files.isWritable(installDir.toPath()))
            return true;

        File fileTest = null;
        try {
            // we use the .dll suffix to properly test on Vista virtual directories
            // on Vista you are not allowed to write executable files on virtual directories like "Program Files"
            fileTest = File.createTempFile("writableArea", ".dll", installDir); //$NON-NLS-1$ //$NON-NLS-2$
        } catch (IOException e) {
            //If an exception occured while trying to create the file, it means that it is not writtable
            return false;
        } finally {
            if (fileTest != null)
                fileTest.delete();
        }
        return true;
    }

    public static String getWorkingDirectory(String defaultWorkspaceLocation) {
        String osName = (System.getProperty("os.name")).toUpperCase();
        String workingDirectory;
        if (osName.contains("WIN")) {
            String appData = System.getenv("AppData");
            if (appData == null) {
                appData = System.getProperty("user.home");
            }
            workingDirectory = appData + "\\" + defaultWorkspaceLocation;
        } else if (osName.contains("MAC")) {
            workingDirectory = System.getProperty("user.home") + "/Library/" + defaultWorkspaceLocation;
        } else {
            // Linux
            String dataHome = System.getProperty("XDG_DATA_HOME");
            if (dataHome == null) {
                dataHome = System.getProperty("user.home") + "/.local/share";
            }
            String badWorkingDir = dataHome + "/." + defaultWorkspaceLocation;
            String goodWorkingDir = dataHome + "/" + defaultWorkspaceLocation;
            if (!new File(goodWorkingDir).exists() && new File(badWorkingDir).exists()) {
                // Let's use bad working dir if it exists (#6316)
                workingDirectory = badWorkingDir;
            } else {
                workingDirectory = goodWorkingDir;
            }
        }
        return workingDirectory;
    }

    static void setSystemPropertyIfNotSet(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }

    /**
     * Resolve the given file against  osgi.install.area.
     * If osgi.install.area is not set, or the file is not relative, then
     * the file is returned as is.
     */
    static File resolveFile(File toAdjust) {
        if (!toAdjust.isAbsolute()) {
            String installArea = System.getProperty(LauncherConstants.PROP_INSTALL_AREA);
            if (installArea != null) {
                if (installArea.startsWith(LauncherConstants.FILE_SCHEME))
                    toAdjust = new File(installArea.substring(5), toAdjust.getPath());
                else if (new File(installArea).exists())
                    toAdjust = new File(installArea, toAdjust.getPath());
            }
        }
        return toAdjust;
    }

    static URL adjustTrailingSlash(URL url, boolean trailingSlash) throws MalformedURLException {
        String file = url.getFile();
        if (trailingSlash == (file.endsWith("/"))) //$NON-NLS-1$
            return url;
        file = trailingSlash ? file + "/" : file.substring(0, file.length() - 1); //$NON-NLS-1$
        return new URL(url.getProtocol(), url.getHost(), file);
    }

    static URL buildURL(String spec, boolean trailingSlash) {
        if (spec == null)
            return null;
        if (File.separatorChar == '\\')
            spec = spec.trim();
        boolean isFile = spec.startsWith(LauncherConstants.FILE_SCHEME);
        try {
            if (isFile) {
                File toAdjust = new File(spec.substring(5));
                toAdjust = resolveFile(toAdjust);
                if (toAdjust.isDirectory())
                    return adjustTrailingSlash(toAdjust.toURL(), trailingSlash);
                return toAdjust.toURL();
            }
            return new URL(spec);
        } catch (MalformedURLException e) {
            // if we failed and it is a file spec, there is nothing more we can do
            // otherwise, try to make the spec into a file URL.
            if (isFile)
                return null;
            try {
                File toAdjust = new File(spec);
                if (toAdjust.isDirectory())
                    return adjustTrailingSlash(toAdjust.toURL(), trailingSlash);
                return toAdjust.toURL();
            } catch (MalformedURLException e1) {
                return null;
            }
        }
    }

    static String resolveEnv(String source, String var, String prop) {
        String value = System.getenv(prop); // $NON-NLS-1$
        if (value == null) {
            value = "";
        }
        return value + source.substring(var.length());
    }

    static String resolveLocation(String source, String var, String location) {
        String result = location + source.substring(var.length());
        return result.replaceFirst("^~", System.getProperty(LauncherConstants.PROP_USER_HOME));
    }

    static InputStream getStream(URL location) throws IOException {
        if ("file".equalsIgnoreCase(location.getProtocol())) { //$NON-NLS-1$
            // this is done to handle URLs with invalid syntax in the path
            File f = new File(location.getPath());
            if (f.exists()) {
                return new FileInputStream(f);
            }
        }
        return location.openStream();
    }

    /*
     * Load the configuration
     */
    static Properties load(URL url, String suffix) throws IOException {
        // figure out what we will be loading
        if (suffix != null && !suffix.isEmpty()) //$NON-NLS-1$
            url = new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getFile() + suffix);

        // try to load saved configuration file
        Properties props = new Properties();
        try (InputStream is = getStream(url)) {
            props.load(is);
        }
        return props;
    }

    static Properties loadProperties(URL url) throws IOException {
        // try to load saved configuration file (watch for failed prior save())
        if (url == null)
            return null;
        Properties result;
        IOException originalException;
        try {
            result = load(url, null); // try to load config file
        } catch (IOException e1) {
            originalException = e1;
            try {
                result = load(url, LauncherConstants.CONFIG_FILE_TEMP_SUFFIX); // check for failures on save
            } catch (IOException e2) {
                try {
                    result = load(url, LauncherConstants.CONFIG_FILE_BAK_SUFFIX); // check for failures on save
                } catch (IOException e3) {
                    throw originalException; // we tried, but no config here ...
                }
            }
        }
        return result;
    }

    static Properties substituteVars(Properties result) {
        if (result == null) {
            //nothing todo.
            return null;
        }
        for (Enumeration<?> eKeys = result.keys(); eKeys.hasMoreElements(); ) {
            Object key = eKeys.nextElement();
            if (key instanceof String) {
                String value = result.getProperty((String) key);
                if (value != null)
                    result.put(key, substituteVars(value));
            }
        }
        return result;
    }
}
