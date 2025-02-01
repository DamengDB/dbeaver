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
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;

public class StartupClassLoader extends URLClassLoader {

    private final String[] extensionPaths;

    public StartupClassLoader(String[] extensionPaths, URL[] urls) {
        super(urls);
        this.extensionPaths = extensionPaths;
    }

    public StartupClassLoader(String[] extensionPaths, URL[] urls, ClassLoader parent) {
        super(urls, parent);
        this.extensionPaths = extensionPaths;
    }

    public StartupClassLoader(String[] extensionPaths, URL[] urls, ClassLoader parent, URLStreamHandlerFactory factory) {
        super(urls, parent, factory);
        this.extensionPaths = extensionPaths;
    }

    @Override
    protected String findLibrary(String name) {
        if (extensionPaths == null)
            return super.findLibrary(name);
        String libName = System.mapLibraryName(name);
        for (String extensionPath : extensionPaths) {
            File libFile = new File(extensionPath, libName);
            if (libFile.isFile())
                return libFile.getAbsolutePath();
        }
        return super.findLibrary(name);
    }

    /**
     * Must override addURL to make it public so the framework can
     * do deep reflection to add URLs on Java 9.
     */
    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    // preparing for Java 9
    protected URL findResource(String moduleName, String name) {
        return findResource(name);
    }

    // preparing for Java 9
    protected Class<?> findClass(String moduleName, String name) {
        try {
            return findClass(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
