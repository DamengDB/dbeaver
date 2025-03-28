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
package org.jkiss.dbeaver.ext.damengdb.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ModelPreferences;
import org.jkiss.dbeaver.ext.damengdb.model.DamengExecutionContext;
import org.jkiss.dbeaver.model.app.DBPPlatform;
import org.jkiss.dbeaver.model.data.DBDContentStorage;
import org.jkiss.dbeaver.model.data.DBDDisplayFormat;
import org.jkiss.dbeaver.model.data.storage.BytesContentStorage;
import org.jkiss.dbeaver.model.data.storage.TemporaryContentStorage;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.data.JDBCContentLOB;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSTypedObject;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.utils.ContentUtils;
import org.jkiss.dbeaver.utils.MimeTypes;
import org.jkiss.utils.BeanUtils;

/**
 * BFILE content
 */
public class DamengContentBFILE extends JDBCContentLOB {
	
    private static final Log log = Log.getLog(DamengContentBFILE.class);

    private Object bfile;

    private String dir;

    private String fileName;

    private String filePath;

    public DamengContentBFILE(DBCExecutionContext executionContext, Object bfile) {
        super(executionContext);
        this.bfile = bfile;
        if (this.bfile != null) {
            try {
                this.dir = (String) BeanUtils.invokeObjectMethod(bfile, "getDir");
                this.fileName = (String) BeanUtils.invokeObjectMethod(bfile, "getFileName");
            } catch (Throwable e) {
                log.error(e);
            }
        }
    }

    private String getFilePath() throws DBCException {
        if (filePath != null) {
            return filePath;
        }

        String sql = "select bintochar(info6) from sysobjects where type$='DIR' and name=?";
        try {
            Connection conn = ((DamengExecutionContext) executionContext).getConnection(null);
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, dir);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                filePath = new File(rs.getString(1), fileName).getAbsolutePath();
                break;
            }

            rs.close();
            ps.close();
        } catch (SQLException e) {
            throw new DBCException("Error when reading BFILE dir", e, executionContext);
        }

        return filePath;
    }

    @Override
    public long getLOBLength() throws DBCException {
        if (bfile != null) {
            File file = new File(getFilePath());
            if (!file.exists()) {
                throw new DBCException("Error when reading BFILE length", null, executionContext);
            }
            return file.length();
        }
        return 0;
    }

    @NotNull
    @Override
    public String getContentType() {
        return MimeTypes.OCTET_STREAM;
    }

    @Override
    public DBDContentStorage getContents(DBRProgressMonitor monitor) throws DBCException {
        if (storage == null && bfile != null) {
            InputStream bs = null;
            try {
                bs = new FileInputStream(getFilePath());
                long contentLength = getContentLength();
                DBPPlatform platform = DBWorkbench.getPlatform();
                if (contentLength < platform.getPreferenceStore().getInt(ModelPreferences.MEMORY_CONTENT_MAX_SIZE)) {
                    try {
                        storage = BytesContentStorage.createFromStream(bs, contentLength, getDefaultEncoding());
                    } catch (IOException e) {
                        throw new DBCException("IO error while reading content", e);
                    }
                } else {
                    // Create new local storage
                    Path tempFile;
                    try {
                        tempFile = ContentUtils.createTempContentFile(monitor, platform, "blob" + bfile.hashCode());
                    } catch (IOException e) {
                        throw new DBCException("Can't create temporary file", e);
                    }
                    try (OutputStream os = Files.newOutputStream(tempFile)) {
                        ContentUtils.copyStreams(bs, contentLength, os, monitor);
                    } catch (IOException e) {
                        ContentUtils.deleteTempFile(tempFile);
                        throw new DBCException("IO error while copying stream", e);
                    } catch (Throwable e) {
                        ContentUtils.deleteTempFile(tempFile);
                        throw new DBCException(e, executionContext);
                    }
                    this.storage = new TemporaryContentStorage(platform, tempFile, getDefaultEncoding(), true);
                }
            } catch (IOException e) {
                throw new DBCException("IO error while reading content", e);
            } finally {
                if (bs != null) {
                    try {
                        bs.close();
                    } catch (IOException e) {
                    }
                }
            }
        }

        return storage;
    }

    @Override
    public void release() {
        releaseBlob();
        super.release();
    }

    private void releaseBlob() {
        if (bfile != null) {
            bfile = null;
        }
    }

    @Override
    public void bindParameter(JDBCSession session, JDBCPreparedStatement preparedStatement, DBSTypedObject columnType,
                              int paramIndex) throws DBCException {
        throw new DBCException("BFILE update not supported");
    }

    @Override
    public Object getRawValue() {
        return bfile;
    }

    @Override
    public boolean isNull() {
        return bfile == null && storage == null;
    }

    @Override
    protected JDBCContentLOB createNewContent() {
        return new DamengContentBFILE(executionContext, null);
    }

    @Override
    public String getDisplayString(DBDDisplayFormat format) {
        return bfile == null ? null : "[BFILE:" + bfile.toString() + "]";
    }

}
