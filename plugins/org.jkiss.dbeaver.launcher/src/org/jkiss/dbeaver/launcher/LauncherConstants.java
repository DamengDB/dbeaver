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

/**
 * @author aniefer
 */
public class LauncherConstants {
    public static final String INTERNAL_AMD64 = "amd64"; //$NON-NLS-1$
    public static final String INTERNAL_OS_SUNOS = "SunOS"; //$NON-NLS-1$
    public static final String INTERNAL_OS_LINUX = "Linux"; //$NON-NLS-1$
    public static final String INTERNAL_OS_MACOSX = "Mac OS"; //$NON-NLS-1$
    public static final String INTERNAL_OS_AIX = "AIX"; //$NON-NLS-1$
    public static final String INTERNAL_OS_HPUX = "HP-UX"; //$NON-NLS-1$
    public static final String INTERNAL_OS_QNX = "QNX"; //$NON-NLS-1$
    public static final String INTERNAL_OS_OS400 = "OS/400"; //$NON-NLS-1$
    public static final String INTERNAL_OS_OS390 = "OS/390"; //$NON-NLS-1$
    public static final String INTERNAL_OS_ZOS = "z/OS"; //$NON-NLS-1$

    public static final String ARCH_X86 = "x86";//$NON-NLS-1$
    public static final String ARCH_X86_64 = "x86_64";//$NON-NLS-1$

    /**
     * Constant string (value "win32") indicating the platform is running on a
     * Window 32-bit operating system (e.g., Windows 98, NT, 2000).
     */
    public static final String OS_WIN32 = "win32";//$NON-NLS-1$

    /**
     * Constant string (value "linux") indicating the platform is running on a
     * Linux-based operating system.
     */
    public static final String OS_LINUX = "linux";//$NON-NLS-1$

    /**
     * Constant string (value "aix") indicating the platform is running on an
     * AIX-based operating system.
     */
    public static final String OS_AIX = "aix";//$NON-NLS-1$

    /**
     * Constant string (value "solaris") indicating the platform is running on a
     * Solaris-based operating system.
     */
    public static final String OS_SOLARIS = "solaris";//$NON-NLS-1$

    /**
     * Constant string (value "hpux") indicating the platform is running on an
     * HP/UX-based operating system.
     */
    public static final String OS_HPUX = "hpux";//$NON-NLS-1$

    /**
     * Constant string (value "qnx") indicating the platform is running on a
     * QNX-based operating system.
     */
    public static final String OS_QNX = "qnx";//$NON-NLS-1$

    /**
     * Constant string (value "macosx") indicating the platform is running on a
     * Mac OS X operating system.
     */
    public static final String OS_MACOSX = "macosx";//$NON-NLS-1$

    /**
     * Constant string (value "os/400") indicating the platform is running on a
     * OS/400 operating system.
     */
    public static final String OS_OS400 = "os/400"; //$NON-NLS-1$

    /**
     * Constant string (value "os/390") indicating the platform is running on a
     * OS/390 operating system.
     */
    public static final String OS_OS390 = "os/390"; //$NON-NLS-1$

    /**
     * Constant string (value "z/os") indicating the platform is running on a
     * z/OS operating system.
     */
    public static final String OS_ZOS = "z/os"; //$NON-NLS-1$

    /**
     * Constant string (value "unknown") indicating the platform is running on a
     * machine running an unknown operating system.
     */
    public static final String OS_UNKNOWN = "unknown";//$NON-NLS-1$

    /**
     * Constant string (value "win32") indicating the platform is running on a
     * machine using the Windows windowing system.
     */
    public static final String WS_WIN32 = "win32";//$NON-NLS-1$

    /**
     * Constant string (value "wpf") indicating the platform is running on a
     * machine using the Windows Presendation Foundation system.
     */
    public static final String WS_WPF = "wpf";//$NON-NLS-1$

    /**
     * Constant string (value "motif") indicating the platform is running on a
     * machine using the Motif windowing system.
     */
    public static final String WS_MOTIF = "motif";//$NON-NLS-1$

    /**
     * Constant string (value "gtk") indicating the platform is running on a
     * machine using the GTK windowing system.
     */
    public static final String WS_GTK = "gtk";//$NON-NLS-1$

    /**
     * Constant string (value "photon") indicating the platform is running on a
     * machine using the Photon windowing system.
     */
    public static final String WS_PHOTON = "photon";//$NON-NLS-1$

    /**
     * Constant string (value "carbon") indicating the platform is running on a
     * machine using the Carbon windowing system (Mac OS X).
     */
    public static final String WS_CARBON = "carbon";//$NON-NLS-1$

    /**
     * Constant string (value "cocoa") indicating the platform is running on a
     * machine using the Cocoa windowing system (Mac OS X).
     */
    public static final String WS_COCOA = "cocoa"; //$NON-NLS-1$

    /**
     * Constant string (value "unknown") indicating the platform is running on a
     * machine running an unknown windowing system.
     */
    public static final String WS_UNKNOWN = "unknown";//$NON-NLS-1$
    //splash screen system properties
    public static final String SPLASH_HANDLE = "org.eclipse.equinox.launcher.splash.handle"; //$NON-NLS-1$
    public static final String SPLASH_LOCATION = "org.eclipse.equinox.launcher.splash.location"; //$NON-NLS-1$
    // for variable substitution
    public static final String VARIABLE_DELIM_STRING = "$"; //$NON-NLS-1$
    public static final char VARIABLE_DELIM_CHAR = '$';
    public static final String DBEAVER_DATA_FOLDER = "DBeaverData";
    protected static final String REFERENCE_SCHEME = "reference:"; //$NON-NLS-1$
    protected static final String JAR_SCHEME = "jar:"; //$NON-NLS-1$
    // log file handling
    protected static final String SESSION = "!SESSION"; //$NON-NLS-1$
    protected static final String ENTRY = "!ENTRY"; //$NON-NLS-1$
    protected static final String MESSAGE = "!MESSAGE"; //$NON-NLS-1$
    protected static final String STACK = "!STACK"; //$NON-NLS-1$
    protected static final int ERROR = 4;
    protected static final String PLUGIN_ID = "org.eclipse.equinox.launcher"; //$NON-NLS-1$
    static final String[] SPLASH_IMAGES = {"splash.png", //$NON-NLS-1$
        "splash.bmp", //$NON-NLS-1$
    };
    static final String FILE_SCHEME = "file:"; //$NON-NLS-1$
    // command line args
    static final String FRAMEWORK = "-framework"; //$NON-NLS-1$
    static final String INSTALL = "-install"; //$NON-NLS-1$
    static final String INITIALIZE = "-initialize"; //$NON-NLS-1$
    static final String VM = "-vm"; //$NON-NLS-1$
    static final String VMARGS = "-vmargs"; //$NON-NLS-1$
    static final String DEBUG = "-debug"; //$NON-NLS-1$
    static final String DEV = "-dev"; //$NON-NLS-1$
    static final String CONFIGURATION = "-configuration"; //$NON-NLS-1$
    static final String NOSPLASH = "-nosplash"; //$NON-NLS-1$
    static final String SHOWSPLASH = "-showsplash"; //$NON-NLS-1$
    static final String EXITDATA = "-exitdata"; //$NON-NLS-1$
    static final String NAME = "-name"; //$NON-NLS-1$
    static final String LAUNCHER = "-launcher"; //$NON-NLS-1$
    static final String PROTECT = "-protect"; //$NON-NLS-1$
    //currently the only level of protection we care about.
    static final String PROTECT_MASTER = "master"; //$NON-NLS-1$
    static final String PROTECT_BASE = "base"; //$NON-NLS-1$
    static final String LIBRARY = "--launcher.library"; //$NON-NLS-1$
    static final String APPEND_VMARGS = "--launcher.appendVmargs"; //$NON-NLS-1$
    static final String OVERRIDE_VMARGS = "--launcher.overrideVmargs"; //$NON-NLS-1$
    static final String NL = "-nl"; //$NON-NLS-1$
    static final String ENDSPLASH = "-endsplash"; //$NON-NLS-1$
    static final String CLEAN = "-clean"; //$NON-NLS-1$
    static final String NOEXIT = "-noExit"; //$NON-NLS-1$
    static final String OS = "-os"; //$NON-NLS-1$
    static final String WS = "-ws"; //$NON-NLS-1$
    static final String ARCH = "-arch"; //$NON-NLS-1$
    static final String STARTUP = "-startup"; //$NON-NLS-1$
    static final String OSGI = "org.eclipse.osgi"; //$NON-NLS-1$
    static final String STARTER = "org.eclipse.core.runtime.adaptor.EclipseStarter"; //$NON-NLS-1$
    static final String PLATFORM_URL = "platform:/base/"; //$NON-NLS-1$
    static final String ECLIPSE_PROPERTIES = "eclipse.properties"; //$NON-NLS-1$
    // constants: configuration file location
    static final String CONFIG_DIR = "configuration/"; //$NON-NLS-1$
    static final String CONFIG_FILE = "config.ini"; //$NON-NLS-1$
    static final String CONFIG_FILE_TEMP_SUFFIX = ".tmp"; //$NON-NLS-1$
    static final String CONFIG_FILE_BAK_SUFFIX = ".bak"; //$NON-NLS-1$
    static final String ECLIPSE = "eclipse"; //$NON-NLS-1$
    static final String PRODUCT_SITE_MARKER = ".eclipseproduct"; //$NON-NLS-1$
    static final String PRODUCT_SITE_ID = "id"; //$NON-NLS-1$
    static final String PRODUCT_SITE_VERSION = "version"; //$NON-NLS-1$
    static final String PRODUCT_SNAPSHOT_VERSION = "snapshot"; //$NON-NLS-1$
    // constants: System property keys and/or configuration file elements
    static final String PROP_USER_HOME = "user.home"; //$NON-NLS-1$
    static final String PROP_USER_DIR = "user.dir"; //$NON-NLS-1$
    static final String PROP_INSTALL_AREA = "osgi.install.area"; //$NON-NLS-1$
    static final String PROP_CONFIG_AREA = "osgi.configuration.area"; //$NON-NLS-1$
    static final String PROP_CONFIG_AREA_DEFAULT = "osgi.configuration.area.default"; //$NON-NLS-1$
    static final String PROP_BASE_CONFIG_AREA = "osgi.baseConfiguration.area"; //$NON-NLS-1$
    static final String PROP_SHARED_CONFIG_AREA = "osgi.sharedConfiguration.area"; //$NON-NLS-1$
    static final String PROP_CONFIG_CASCADED = "osgi.configuration.cascaded"; //$NON-NLS-1$
    static final String PROP_FRAMEWORK = "osgi.framework"; //$NON-NLS-1$
    static final String PROP_SPLASHPATH = "osgi.splashPath"; //$NON-NLS-1$
    static final String PROP_SPLASHLOCATION = "osgi.splashLocation"; //$NON-NLS-1$
    static final String PROP_CLASSPATH = "osgi.frameworkClassPath"; //$NON-NLS-1$
    static final String PROP_EXTENSIONS = "osgi.framework.extensions"; //$NON-NLS-1$
    static final String PROP_FRAMEWORK_SYSPATH = "osgi.syspath"; //$NON-NLS-1$
    static final String PROP_FRAMEWORK_SHAPE = "osgi.framework.shape"; //$NON-NLS-1$
    static final String PROP_LOGFILE = "osgi.logfile"; //$NON-NLS-1$
    static final String PROP_REQUIRED_JAVA_VERSION = "osgi.requiredJavaVersion"; //$NON-NLS-1$
    static final String PROP_PARENT_CLASSLOADER = "osgi.parentClassloader"; //$NON-NLS-1$
    static final String PROP_FRAMEWORK_PARENT_CLASSLOADER = "osgi.frameworkParentClassloader"; //$NON-NLS-1$
    static final String PROP_NL = "osgi.nl"; //$NON-NLS-1$
    static final String PROP_NOSHUTDOWN = "osgi.noShutdown"; //$NON-NLS-1$
    static final String PROP_DEBUG = "osgi.debug"; //$NON-NLS-1$
    static final String PROP_OS = "osgi.os"; //$NON-NLS-1$
    static final String PROP_WS = "osgi.ws"; //$NON-NLS-1$
    static final String PROP_ARCH = "osgi.arch"; //$NON-NLS-1$
    static final String PROP_EXITCODE = "eclipse.exitcode"; //$NON-NLS-1$
    static final String PROP_EXITDATA = "eclipse.exitdata"; //$NON-NLS-1$
    static final String PROP_LAUNCHER = "eclipse.launcher"; //$NON-NLS-1$
    static final String PROP_LAUNCHER_NAME = "eclipse.launcher.name"; //$NON-NLS-1$
    static final String PROP_LOG_INCLUDE_COMMAND_LINE = "eclipse.log.include.commandline"; //$NON-NLS-1$
    static final String PROP_VM = "eclipse.vm"; //$NON-NLS-1$
    static final String PROP_VMARGS = "eclipse.vmargs"; //$NON-NLS-1$
    static final String PROP_COMMANDS = "eclipse.commands"; //$NON-NLS-1$
    static final String PROP_ECLIPSESECURITY = "eclipse.security"; //$NON-NLS-1$
    // Suffix for location properties - see LocationManager.
    static final String READ_ONLY_AREA_SUFFIX = ".readOnly"; //$NON-NLS-1$
    // Data mode constants for user, configuration and data locations.
    static final String NONE = "@none"; //$NON-NLS-1$
    static final String NO_DEFAULT = "@noDefault"; //$NON-NLS-1$
    static final String USER_HOME = "@user.home"; //$NON-NLS-1$
    static final String USER_DIR = "@user.dir"; //$NON-NLS-1$
    // Placeholder of program configuration data, depends on OS
    static final String XDG_DATA_HOME = "@data.home"; //$NON-NLS-1$
    static final String PROP_XDG_DATA_HOME_WIN = "APPDATA"; //$NON-NLS-1$
    static final String PROP_XDG_DATA_HOME_UNIX = "XDG_DATA_HOME"; //$NON-NLS-1$
    // Placeholder for hashcode of installation directory
    static final String INSTALL_HASH_PLACEHOLDER = "@install.hash"; //$NON-NLS-1$
    static final String LAUNCHER_DIR = "@launcher.dir"; //$NON-NLS-1$
    // types of parent classloaders the framework can have
    static final String PARENT_CLASSLOADER_APP = "app"; //$NON-NLS-1$
    static final String PARENT_CLASSLOADER_EXT = "ext"; //$NON-NLS-1$
    static final String PARENT_CLASSLOADER_BOOT = "boot"; //$NON-NLS-1$
    static final String PARENT_CLASSLOADER_CURRENT = "current"; //$NON-NLS-1$
    static final String BASE_TIMESTAMP_FILE_CONFIGINI = ".baseConfigIniTimestamp"; //$NON-NLS-1$
    static final String KEY_CONFIGINI_TIMESTAMP = "configIniTimestamp"; //$NON-NLS-1$
    static final String PROP_IGNORE_USER_CONFIGURATION = "eclipse.ignoreUserConfiguration"; //$NON-NLS-1$
    static final String DBEAVER_INSTALL_FOLDER = "install-data";
    static final String ENV_DATA_HOME_WIN = "APPDATA"; //$NON-NLS-1$
    static final String LOCATION_DATA_HOME_UNIX = "~/.local/share"; //$NON-NLS-1$
    static final String LOCATION_DATA_HOME_MAC = "~/Library"; //$NON-NLS-1$
    static final String DB_DATA_HOME = "@data.home"; //$NON-NLS-1$
    static final String DBEAVER_CONFIG_FOLDER = "settings";
    static final String DBEAVER_CONFIG_FILE = "global-settings.ini";
    static final String DBEAVER_PROP_LANGUAGE = "nl";
}
