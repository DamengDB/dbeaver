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
package org.jkiss.dbeaver.ext.damengdb.tasks;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.eclipse.osgi.util.NLS;
import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.damengdb.model.DamengDataSource;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.runtime.DBRRunnableContext;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.task.DBTTask;
import org.jkiss.dbeaver.registry.task.TaskPreferenceStore;
import org.jkiss.dbeaver.tasks.nativetool.AbstractNativeToolHandler;
import org.jkiss.dbeaver.tasks.nativetool.NativeToolUtils;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.dbeaver.utils.RuntimeUtils;

public class DamengScriptExecuteHandler
    extends AbstractNativeToolHandler<DamengScriptExecuteSettings, DBSObject, DamengDataSource> {
	
    @Override
    public Collection<DamengDataSource> getRunInfo(DamengScriptExecuteSettings settings) {
        return Collections.singletonList((DamengDataSource) settings.getDataSourceContainer().getDataSource());
    }

    @Override
    protected DamengScriptExecuteSettings createTaskSettings(DBRRunnableContext context, DBTTask task)
        throws DBException {
        DamengScriptExecuteSettings settings = new DamengScriptExecuteSettings();
        settings.loadSettings(context, new TaskPreferenceStore(task));

        return settings;
    }

    @Override
    protected boolean needsModelRefresh() {
        return false;
    }

    @Override
    public void fillProcessParameters(DamengScriptExecuteSettings settings, DamengDataSource arg, List<String> cmd)
        throws IOException {
        String disqlExec = RuntimeUtils.getNativeBinaryName("DIsql");
        File disqlBinary = new File(settings.getClientHome().getPath(), "bin/" + disqlExec);
        if (!disqlBinary.exists()) {
            disqlBinary = new File(settings.getClientHome().getPath(), disqlExec);
        }
        if (!disqlBinary.exists()) {
            throw new IOException("DIsql binary not found in '" + settings.getClientHome().getPath().getAbsolutePath());
        }

        cmd.add(disqlBinary.getAbsolutePath());
    }

    @Override
    protected List<String> getCommandLine(DamengScriptExecuteSettings settings, DamengDataSource arg)
        throws IOException {
        List<String> cmd = new ArrayList<>();
        fillProcessParameters(settings, arg, cmd);
        DBPConnectionConfiguration conInfo = settings.getDataSourceContainer().getActualConnectionConfiguration();
        String port = conInfo.getHostPort();
        String url = conInfo.getHostName() + (port != null ? ":" + port : "");
        cmd.add(conInfo.getUserName() + "/" + conInfo.getUserPassword() + "@" + url);
        cmd.add("`" + settings.getInputFile());
        return cmd;
    }

    @Override
    protected boolean isLogInputStream() {
        return true;
    }

    @Override
    protected void startProcessHandler(DBRProgressMonitor monitor, DBTTask task, DamengScriptExecuteSettings settings,
                                       DamengDataSource arg, ProcessBuilder processBuilder, Process process, Log log) throws IOException {
        final File inputFile = new File(settings.getInputFile());
        if (!inputFile.exists()) {
            throw new IOException("File '" + inputFile.getAbsolutePath() + "' doesn't exist");
        }

        LogReaderJob logReaderJob = new LogReaderJob(task, settings, processBuilder, process, isLogInputStream());
        logReaderJob.start();

        MonitorDisqlThread monitorDisqlThread = new MonitorDisqlThread(logReaderJob);
        monitorDisqlThread.setDaemon(true);
        monitorDisqlThread.start();
    }

    private class LogReaderJob extends Thread {
        private final DBTTask task;

        private final DamengScriptExecuteSettings settings;

        private final PrintStream logWriter;

        private final ProcessBuilder processBuilder;

        private final Process process;

        private final boolean isLogInputStream;

        private boolean finish = false;

        private long finishTime = -1;

        protected LogReaderJob(DBTTask task, DamengScriptExecuteSettings settings, ProcessBuilder processBuilder,
                               Process process, boolean isLogInputStream) {
            super("Log reader for " + task.getName());
            this.task = task;
            this.settings = settings;
            this.logWriter = settings.getLogWriter();
            this.processBuilder = processBuilder;
            this.process = process;
            this.isLogInputStream = isLogInputStream;
        }

        @Override
        public void run() {
            String lf = GeneralUtils.getDefaultLineSeparator();
            List<String> command = processBuilder.command();

            StringBuilder cmdString = new StringBuilder();
            for (String cmd : command) {
                if (NativeToolUtils.isSecureString(settings, cmd)) {
                    cmd = "******";
                }
                if (cmdString.length() > 0) {
                    cmdString.append(' ');
                }
                cmdString.append(cmd);
            }
            cmdString.append(lf);

            try {
                logWriter.println(NLS.bind("Task ''{0}'' started at {1}", task.getName(), new Date() + lf));
                logWriter.flush();

                logWriter.print(cmdString.toString());

                if (isLogInputStream) {
                    try {
                        readStream(process.getInputStream());
                    } catch (IOException e) {
                        logWriter.println(e.getMessage() + lf);
                    }
                } else {
                    readStream(process.getErrorStream());
                }
            } catch (IOException e) {
                logWriter.println();
                logWriter.println(e.getMessage() + lf);
            } finally {
                logWriter.println();
                logWriter.print(NLS.bind("Task ''{0}'' finished at {1}", task.getName(), new Date() + lf));
                logWriter.flush();
            }
        }

        private String readStream(@NotNull InputStream inputStream) throws IOException {
            StringBuilder message = new StringBuilder();
            try (Reader reader = new InputStreamReader(inputStream, settings.getConsoleEncoding())) {
                StringBuilder buf = new StringBuilder();
                for (; ; ) {
                    int b = reader.read();
                    finishTime = -1;
                    if (b == -1) {
                        break;
                    }
                    buf.append((char) b);
                    if (b == '\n') {
                        message.append(buf);
                        logWriter.print(buf);
                        logWriter.flush();
                        buf.setLength(0);
                    } else if (b == ' ') {
                        if (buf.toString().equals("SQL> ")) {
                            finishTime = System.currentTimeMillis();
                        }
                    }
                }
            }
            finish = true;
            return message.toString();
        }
    }

    private class MonitorDisqlThread extends Thread {
        private LogReaderJob logReaderJob;

        public MonitorDisqlThread(LogReaderJob logReaderJob) {
            super("Monitor Disql Thread");
            this.logReaderJob = logReaderJob;
        }

        @Override
        public void run() {
            while (!logReaderJob.finish) {
                if (logReaderJob.finishTime > 0 && System.currentTimeMillis() - logReaderJob.finishTime > 1000) {
                    OutputStream os = logReaderJob.process.getOutputStream();
                    try {
                        os.write("exit;\n".getBytes());
                        os.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    break;
                }

                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                }
            }
        }
    }
    
}
