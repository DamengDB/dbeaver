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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Function;

public class ResourceDownloader {

    private static final ResourceDownloader INSTANCE = new ResourceDownloader();

    public static ResourceDownloader getInstance() {
        return INSTANCE;
    }

    public Path downloadResourceAsTempFile(IFileStore fileStore) throws InterruptedException, InvocationTargetException {
        return downloadResource(fileStore, (Path tempFolder) -> {
            try {
                return Files.createTempFile(tempFolder, null, fileStore.getName());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public Path downloadResourceWithOriginalNameAndDeleteIfExist(IFileStore fileStore) throws InterruptedException, InvocationTargetException, IOException {
        return downloadResource(fileStore, (Path tempFolder) -> {
            String fileName = fileStore.getName();
            Path resolvePath = tempFolder.resolve(fileName);
            if(!fileStore.fetchInfo().isDirectory()){
                try {
                    Files.deleteIfExists(resolvePath);
                    return Files.createFile(resolvePath);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                if(resolvePath.toFile().exists() && resolvePath.toFile().isDirectory()) {
                    try {
                        Files.walkFileTree(resolvePath, new SimpleFileVisitor<>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                Files.delete(file);
                                return FileVisitResult.CONTINUE;
                            }
                            @Override
                            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                                Files.delete(dir);
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

            }

        });
    }
    private Path downloadResource(IFileStore fileStore, Function<Path, Path> fileProducer) throws InterruptedException, InvocationTargetException {
        final Path[] target = new Path[1];

        UIUtils.runInProgressService(monitor -> {
            try {
                Path tempFolder = DBWorkbench.getPlatform().getTempFolder(monitor, "external-files");
                target[0] = fileProducer.apply(tempFolder);

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
