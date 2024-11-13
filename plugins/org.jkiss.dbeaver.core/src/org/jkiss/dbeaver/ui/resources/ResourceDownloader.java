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
package org.jkiss.dbeaver.ui.resources;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.utils.ContentUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ResourceDownloader {

    private static final ResourceDownloader INSTANCE = new ResourceDownloader();

    public static ResourceDownloader getInstance() {
        return INSTANCE;
    }

    public Path downloadResource(IFileStore fileStore) throws InterruptedException, InvocationTargetException {
        final Path[] target = new Path[1];

        UIUtils.runInProgressService(monitor -> {
            try {
                target[0] = Files.createTempFile(
                    DBWorkbench.getPlatform().getTempFolder(monitor, "external-files"),
                    null,
                    fileStore.getName()
                );

                try (InputStream is = fileStore.openInputStream(EFS.NONE, null)) {
                    try (OutputStream os = Files.newOutputStream(target[0])) {
                        final IFileInfo info = fileStore.fetchInfo(EFS.NONE, null);
                        ContentUtils.copyStreams(is, info.getLength(), os, monitor);
                    }
                }
            } catch (Exception e) {
                throw new InvocationTargetException(e);
            }
        });
        return target[0];
    }
}
