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
package org.jkiss.dbeaver.ext.damengdb.model;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.ext.damengdb.internal.DamengMessages;
import org.jkiss.dbeaver.ext.damengdb.model.plan.DamengExecutionPlan;
import org.jkiss.dbeaver.model.data.json.JSONUtils;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCStatement;
import org.jkiss.dbeaver.model.impl.jdbc.exec.JDBCFactoryDefault;
import org.jkiss.dbeaver.model.impl.jdbc.exec.JDBCStatementImpl;

public class DamengJDBCFactory extends JDBCFactoryDefault {
	
    @Override
    public JDBCStatement createStatement(@NotNull JDBCSession session, @NotNull Statement original,
                                         boolean disableLogging) throws SQLException {
        return new DmJDBCStatement<>(session, original, disableLogging);
    }
}

@SuppressWarnings(value = {"rawtypes", "unchecked"})
class DmJDBCStatement<STATEMENT extends Statement> extends JDBCStatementImpl {
    private JDBCSession session;

    public DmJDBCStatement(@NotNull JDBCSession connection, @NotNull STATEMENT original, boolean disableLogging) {
        super(connection, original, disableLogging);
        this.session = connection;
    }

    @Override
    protected boolean handleExecuteResult(boolean result) {
        if (result == true) {
            return result;
        } else {
            String sql = query.toUpperCase().trim();
            if (isExplainSql(sql)) {
                result = true;
            }
            return result;
        }
    }

    /**
     * Determine whether it is Explain sql
     *
     * @return
     */
    private boolean isExplainSql(String sql) {
        String sqlTemp = sql = sql.trim();
        if (sqlTemp.toUpperCase().startsWith(DamengConstants.EXPLAIN_KEYWORD)) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected JDBCResultSet makeResultSet(@Nullable ResultSet resultSet) throws SQLException {
        if (resultSet == null) {
            if (isExplainSql(query)) {
                resultSet = new DmPlanResultSet(this.session, query);
            }
        }

        return super.makeResultSet(resultSet);
    }
}

class DmPlanResultSet implements ResultSet, ResultSetMetaData {
    private final int maxColumn = 7;
    // Precision/Max length
    private final int PLAN_ID_PRECISION = 10;
    private final int PLAN_NAME_PRECISION = 128;
    private final int PLAN_CONTENT_PRECISION = 1024;
    private final int PLAN_COST_PRECISION = 128;
    private final int PLAN_CARDINALITY_PRECISION = 128;
    private final int PLAN_CPU_COST_PRECISION = 128;
    private final int PLAN_DESCRIPTION_PRECISION = 128;
    private final int PLAN_ID_COLUMN_INDEX = 1;
    private final int PLAN_NAME_COLUMN_INDEX = 2;
    private final int PLAN_CONTENT_COLUMN_INDEX = 3;
    private final int PLAN_COST_COLUMN_INDEX = 4;
    private final int PLAN_CARDINALITY_COLUMN_INDEX = 5;
    private final int PLAN_CPU_COST_COLUMN_INDEX = 6;
    private final int PLAN_DESCRIPTION_COLUMN_INDEX = 7;
    private long startIndex = 1;
    private int curRowNum = (int) startIndex - 1;
    private int rowCount = 0;
    private JDBCSession planSession;
    private String sql;
    private String planString;
    private List<Map<String, Object>> planInfoList = new ArrayList<>();

    public DmPlanResultSet(JDBCSession session, String sql) {
        this.sql = sql.trim();
        this.planSession = session;

        initializePlanInfoList();

    }

    /**
     * Add date to List,caculate the lines of the result set
     *
     * @param node execution plan
     */
    private void addListNode(Map<String, Object> node) {
        planInfoList.add(node);
        rowCount++;
    }

    /**
     * initalize the PlanInfoList
     */
    private void initializePlanInfoList() {
        boolean isIndentation = true; // PlanName keeps the indentation
        String line = null;
        Map<String, Object> planmap = null;

        try {

            if (sql.toUpperCase().contains(DamengConstants.EXPLAIN_KEYWORD)) {
                int position = sql.toUpperCase().indexOf(DamengConstants.EXPLAIN_KEYWORD);
                if (position != -1) {
                    sql = sql.toUpperCase().replace(DamengConstants.EXPLAIN_KEYWORD, "");
                }
            }
            DamengExecutionPlan eplan = new DamengExecutionPlan((DamengDataSource) planSession.getDataSource(),
                planSession, sql);
            planString = DamengExecutionPlan.getExplainInfo(planSession.getOriginal(), sql);
            BufferedReader reader = new BufferedReader(new StringReader(planString));

            reader.readLine(); // First line is unnecessary
            planInfoList.add(null); // planInfoList starts with 1 and doesn't
            // calculate the lines
            while ((line = reader.readLine()) != null) {
                planmap = eplan.parsePlanString2PlanMap(line, isIndentation);
                addListNode(planmap);
            }
        } catch (Exception e) {
            // nothing
        }
    }

    /**
     * Check whether the column is within the range
     *
     * @param column
     * @return
     */
    private boolean checkColumn(int column) {
        if (column <= this.maxColumn && column > 0) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean next() throws SQLException {
        if (rowCount > curRowNum) {
            curRowNum++;
            return true;
        }

        return false;
    }

    @Override
    public String getString(int columnIndex) throws SQLException {

        String columnName = getColumnKeyName(columnIndex);
        return getString(columnName);
    }

    @Override
    public String getString(String columnName) throws SQLException {
        return planInfoList.get(curRowNum).get(columnName).toString();
    }

    /**
     * Get the key of the columnIndex's Map
     *
     * @param columnIndex
     * @return
     */
    private String getColumnKeyName(int columnIndex) {
        switch (columnIndex) {
            case PLAN_ID_COLUMN_INDEX:
                return DamengExecutionPlan.PLAN_ID;

            case PLAN_NAME_COLUMN_INDEX:
                return DamengExecutionPlan.PLAN_NAME;

            case PLAN_CONTENT_COLUMN_INDEX:
                return DamengExecutionPlan.PLAN_CONTENT;

            case PLAN_COST_COLUMN_INDEX:
                return DamengExecutionPlan.PLAN_COST;

            case PLAN_CARDINALITY_COLUMN_INDEX:
                return DamengExecutionPlan.PLAN_CARDINALITY;

            case PLAN_CPU_COST_COLUMN_INDEX:
                return DamengExecutionPlan.PLAN_CPU_COST;

            case PLAN_DESCRIPTION_COLUMN_INDEX:
                return DamengExecutionPlan.PLAN_DESCRIPTION;

            default:
                return null;
        }
    }

    /**
     * Get the column label
     */
    @Override
    public String getColumnLabel(int column) throws SQLException {
        switch (column) {
            case PLAN_ID_COLUMN_INDEX:
                return DamengMessages.dameng_execution_plan_id_title;

            case PLAN_NAME_COLUMN_INDEX:
                return DamengMessages.dameng_execution_plan_name_title;

            case PLAN_CONTENT_COLUMN_INDEX:
                return DamengMessages.dameng_execution_plan_content_title;

            case PLAN_COST_COLUMN_INDEX:
                return DamengMessages.dameng_execution_plan_cost_title;

            case PLAN_CARDINALITY_COLUMN_INDEX:
                return DamengMessages.dameng_execution_plan_cardinality_title;

            case PLAN_CPU_COST_COLUMN_INDEX:
                return DamengMessages.dameng_execution_plan_cpu_cost_title;

            case PLAN_DESCRIPTION_COLUMN_INDEX:
                return DamengMessages.dameng_execution_plan_description_title;

            default:
                return null;
        }
    }

    @Override
    public void close() throws SQLException {
    }

    @Override
    public boolean wasNull() throws SQLException {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;

    }

    @Override
    public boolean getBoolean(int columnIndex) throws SQLException {
        return false;
    }

    @Override
    public byte getByte(int columnIndex) throws SQLException {
        return 0;
    }

    @Override
    public short getShort(int columnIndex) throws SQLException {
        return 0;
    }

    @Override
    public int getInt(int columnIndex) throws SQLException {
        return JSONUtils.getInteger(planInfoList.get(curRowNum), getColumnKeyName(columnIndex), 0);
    }

    @Override
    public long getLong(int columnIndex) throws SQLException {

        return JSONUtils.getLong(planInfoList.get(curRowNum), getColumnKeyName(columnIndex), 0);
    }

    @Override
    public float getFloat(int columnIndex) throws SQLException {
        return 0;
    }

    @Override
    public double getDouble(int columnIndex) throws SQLException {
        return 0;
    }

    @Override
    @Deprecated
    public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
        return null;
    }

    @Override
    public byte[] getBytes(int columnIndex) throws SQLException {
        return null;
    }

    @Override
    public Date getDate(int columnIndex) throws SQLException {
        return null;
    }

    @Override
    public Time getTime(int columnIndex) throws SQLException {
        return null;
    }

    @Override
    public Timestamp getTimestamp(int columnIndex) throws SQLException {
        return null;
    }

    @Override
    public InputStream getAsciiStream(int columnIndex) throws SQLException {
        return null;
    }

    @Override
    @Deprecated
    public InputStream getUnicodeStream(int columnIndex) throws SQLException {
        return null;
    }

    @Override
    public InputStream getBinaryStream(int columnIndex) throws SQLException {
        return null;
    }

    @Override
    public boolean getBoolean(String columnName) throws SQLException {
        return false;
    }

    @Override
    public byte getByte(String columnName) throws SQLException {
        return 0;
    }

    @Override
    public short getShort(String columnName) throws SQLException {
        return 0;
    }

    @Override
    public int getInt(String columnName) throws SQLException {
        return getInt(findColumn(columnName));
    }

    @Override
    public long getLong(String columnName) throws SQLException {
        return getLong(findColumn(columnName));
    }

    @Override
    public float getFloat(String columnName) throws SQLException {
        return 0;
    }

    @Override
    public double getDouble(String columnName) throws SQLException {
        return 0;
    }

    @Override
    @Deprecated
    public BigDecimal getBigDecimal(String columnName, int scale) throws SQLException {
        return null;
    }

    @Override
    public byte[] getBytes(String columnName) throws SQLException {
        return null;
    }

    @Override
    public Date getDate(String columnName) throws SQLException {
        return null;
    }

    @Override
    public Time getTime(String columnName) throws SQLException {
        return null;
    }

    @Override
    public Timestamp getTimestamp(String columnName) throws SQLException {
        return null;
    }

    @Override
    public InputStream getAsciiStream(String columnName) throws SQLException {
        return null;
    }

    @Override
    @Deprecated
    public InputStream getUnicodeStream(String columnName) throws SQLException {
        return null;
    }

    @Override
    public InputStream getBinaryStream(String columnName) throws SQLException {
        return null;
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {

    }

    @Override
    public String getCursorName() throws SQLException {
        return null;
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return this;
    }

    @Override
    public Object getObject(int columnIndex) throws SQLException {
        return getString(columnIndex);
    }

    @Override
    public Object getObject(String columnName) throws SQLException {
        return getObject(findColumn(columnName));
    }

    @Override
    public int findColumn(String columnName) throws SQLException {
        int columnIndex = 0;
        switch (columnName) {
            case DamengExecutionPlan.PLAN_ID:
                columnIndex = PLAN_ID_COLUMN_INDEX;

            case DamengExecutionPlan.PLAN_NAME:
                columnIndex = PLAN_NAME_COLUMN_INDEX;

            case DamengExecutionPlan.PLAN_CONTENT:
                columnIndex = PLAN_CONTENT_COLUMN_INDEX;

            case DamengExecutionPlan.PLAN_COST:
                columnIndex = PLAN_COST_COLUMN_INDEX;

            case DamengExecutionPlan.PLAN_CARDINALITY:
                columnIndex = PLAN_CARDINALITY_COLUMN_INDEX;

            case DamengExecutionPlan.PLAN_CPU_COST:
                columnIndex = PLAN_CPU_COST_COLUMN_INDEX;

            case DamengExecutionPlan.PLAN_DESCRIPTION:
                columnIndex = PLAN_DESCRIPTION_COLUMN_INDEX;

            default:
                return columnIndex;
        }

    }

    @Override
    public Reader getCharacterStream(int columnIndex) throws SQLException {
        return null;
    }

    @Override
    public Reader getCharacterStream(String columnName) throws SQLException {
        return null;
    }

    @Override
    public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
        return null;
    }

    @Override
    public BigDecimal getBigDecimal(String columnName) throws SQLException {
        return null;
    }

    @Override
    public boolean isBeforeFirst() throws SQLException {
        return false;
    }

    @Override
    public boolean isAfterLast() throws SQLException {
        return false;
    }

    @Override
    public boolean isFirst() throws SQLException {
        return false;
    }

    @Override
    public boolean isLast() throws SQLException {
        return false;
    }

    @Override
    public void beforeFirst() throws SQLException {

    }

    @Override
    public void afterLast() throws SQLException {

    }

    @Override
    public boolean first() throws SQLException {
        return false;
    }

    @Override
    public boolean last() throws SQLException {
        return false;
    }

    @Override
    public int getRow() throws SQLException {
        return 0;
    }

    @Override
    public boolean absolute(int row) throws SQLException {
        return false;
    }

    @Override
    public boolean relative(int rows) throws SQLException {
        return false;
    }

    @Override
    public boolean previous() throws SQLException {
        return false;
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return 0;
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {

    }

    @Override
    public int getFetchSize() throws SQLException {
        return 0;
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {

    }

    @Override
    public int getType() throws SQLException {
        return 0;
    }

    @Override
    public int getConcurrency() throws SQLException {
        return 0;
    }

    @Override
    public boolean rowUpdated() throws SQLException {
        return false;
    }

    @Override
    public boolean rowInserted() throws SQLException {
        return false;
    }

    @Override
    public boolean rowDeleted() throws SQLException {
        return false;
    }

    @Override
    public void updateNull(int columnIndex) throws SQLException {

    }

    @Override
    public void updateBoolean(int columnIndex, boolean x) throws SQLException {

    }

    @Override
    public void updateByte(int columnIndex, byte x) throws SQLException {

    }

    @Override
    public void updateShort(int columnIndex, short x) throws SQLException {

    }

    @Override
    public void updateInt(int columnIndex, int x) throws SQLException {

    }

    @Override
    public void updateLong(int columnIndex, long x) throws SQLException {

    }

    @Override
    public void updateFloat(int columnIndex, float x) throws SQLException {

    }

    @Override
    public void updateDouble(int columnIndex, double x) throws SQLException {

    }

    @Override
    public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {

    }

    @Override
    public void updateString(int columnIndex, String x) throws SQLException {

    }

    @Override
    public void updateBytes(int columnIndex, byte[] x) throws SQLException {

    }

    @Override
    public void updateDate(int columnIndex, Date x) throws SQLException {

    }

    @Override
    public void updateTime(int columnIndex, Time x) throws SQLException {

    }

    @Override
    public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {

    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {

    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {

    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {

    }

    @Override
    public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {

    }

    @Override
    public void updateObject(int columnIndex, Object x) throws SQLException {

    }

    @Override
    public void updateNull(String columnName) throws SQLException {

    }

    @Override
    public void updateBoolean(String columnName, boolean x) throws SQLException {

    }

    @Override
    public void updateByte(String columnName, byte x) throws SQLException {

    }

    @Override
    public void updateShort(String columnName, short x) throws SQLException {

    }

    @Override
    public void updateInt(String columnName, int x) throws SQLException {

    }

    @Override
    public void updateLong(String columnName, long x) throws SQLException {

    }

    @Override
    public void updateFloat(String columnName, float x) throws SQLException {

    }

    @Override
    public void updateDouble(String columnName, double x) throws SQLException {

    }

    @Override
    public void updateBigDecimal(String columnName, BigDecimal x) throws SQLException {

    }

    @Override
    public void updateString(String columnName, String x) throws SQLException {

    }

    @Override
    public void updateBytes(String columnName, byte[] x) throws SQLException {

    }

    @Override
    public void updateDate(String columnName, Date x) throws SQLException {

    }

    @Override
    public void updateTime(String columnName, Time x) throws SQLException {

    }

    @Override
    public void updateTimestamp(String columnName, Timestamp x) throws SQLException {

    }

    @Override
    public void updateAsciiStream(String columnName, InputStream x, int length) throws SQLException {

    }

    @Override
    public void updateBinaryStream(String columnName, InputStream x, int length) throws SQLException {

    }

    @Override
    public void updateCharacterStream(String columnName, Reader reader, int length) throws SQLException {

    }

    @Override
    public void updateObject(String columnName, Object x, int scaleOrLength) throws SQLException {

    }

    @Override
    public void updateObject(String columnName, Object x) throws SQLException {

    }

    @Override
    public void insertRow() throws SQLException {

    }

    @Override
    public void updateRow() throws SQLException {

    }

    @Override
    public void deleteRow() throws SQLException {

    }

    @Override
    public void refreshRow() throws SQLException {

    }

    @Override
    public void cancelRowUpdates() throws SQLException {

    }

    @Override
    public void moveToInsertRow() throws SQLException {

    }

    @Override
    public void moveToCurrentRow() throws SQLException {

    }

    @Override
    public Statement getStatement() throws SQLException {
        return null;
    }

    @Override
    public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
        return null;
    }

    @Override
    public Ref getRef(int columnIndex) throws SQLException {
        return null;
    }

    @Override
    public Blob getBlob(int columnIndex) throws SQLException {
        return null;
    }

    @Override
    public Clob getClob(int columnIndex) throws SQLException {
        return null;
    }

    @Override
    public Array getArray(int columnIndex) throws SQLException {
        return null;
    }

    @Override
    public Object getObject(String columnName, Map<String, Class<?>> map) throws SQLException {
        return null;
    }

    @Override
    public Ref getRef(String columnName) throws SQLException {
        return null;
    }

    @Override
    public Blob getBlob(String columnName) throws SQLException {
        return null;
    }

    @Override
    public Clob getClob(String columnName) throws SQLException {
        return null;
    }

    @Override
    public Array getArray(String columnName) throws SQLException {
        return null;
    }

    @Override
    public Date getDate(int columnIndex, Calendar cal) throws SQLException {
        return null;
    }

    @Override
    public Date getDate(String columnName, Calendar cal) throws SQLException {
        return null;
    }

    @Override
    public Time getTime(int columnIndex, Calendar cal) throws SQLException {
        return null;
    }

    @Override
    public Time getTime(String columnName, Calendar cal) throws SQLException {
        return null;
    }

    @Override
    public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
        return null;
    }

    @Override
    public Timestamp getTimestamp(String columnName, Calendar cal) throws SQLException {
        return null;
    }

    @Override
    public URL getURL(int columnIndex) throws SQLException {
        return null;
    }

    @Override
    public URL getURL(String columnName) throws SQLException {
        return null;
    }

    @Override
    public void updateRef(int columnIndex, Ref x) throws SQLException {

    }

    @Override
    public void updateRef(String columnName, Ref x) throws SQLException {

    }

    @Override
    public void updateBlob(int columnIndex, Blob x) throws SQLException {

    }

    @Override
    public void updateBlob(String columnName, Blob x) throws SQLException {

    }

    @Override
    public void updateClob(int columnIndex, Clob x) throws SQLException {

    }

    @Override
    public void updateClob(String columnName, Clob x) throws SQLException {

    }

    @Override
    public void updateArray(int columnIndex, Array x) throws SQLException {

    }

    @Override
    public void updateArray(String columnName, Array x) throws SQLException {

    }

    @Override
    public RowId getRowId(int columnIndex) throws SQLException {
        return null;
    }

    @Override
    public RowId getRowId(String columnName) throws SQLException {
        return null;
    }

    @Override
    public void updateRowId(int columnIndex, RowId x) throws SQLException {

    }

    @Override
    public void updateRowId(String columnName, RowId x) throws SQLException {

    }

    @Override
    public int getHoldability() throws SQLException {
        return 0;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return false;
    }

    @Override
    public void updateNString(int columnIndex, String nString) throws SQLException {

    }

    @Override
    public void updateNString(String columnName, String nString) throws SQLException {

    }

    @Override
    public void updateNClob(int columnIndex, NClob nClob) throws SQLException {

    }

    @Override
    public void updateNClob(String columnName, NClob nClob) throws SQLException {

    }

    @Override
    public NClob getNClob(int columnIndex) throws SQLException {
        return null;
    }

    @Override
    public NClob getNClob(String columnName) throws SQLException {
        return null;
    }

    @Override
    public SQLXML getSQLXML(int columnIndex) throws SQLException {
        return null;
    }

    @Override
    public SQLXML getSQLXML(String columnName) throws SQLException {
        return null;
    }

    @Override
    public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {

    }

    @Override
    public void updateSQLXML(String columnName, SQLXML xmlObject) throws SQLException {

    }

    @Override
    public String getNString(int columnIndex) throws SQLException {
        return null;
    }

    @Override
    public String getNString(String columnName) throws SQLException {
        return null;
    }

    @Override
    public Reader getNCharacterStream(int columnIndex) throws SQLException {
        return null;
    }

    @Override
    public Reader getNCharacterStream(String columnName) throws SQLException {
        return null;
    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {

    }

    @Override
    public void updateNCharacterStream(String columnName, Reader reader, long length) throws SQLException {

    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {

    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {

    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {

    }

    @Override
    public void updateAsciiStream(String columnName, InputStream x, long length) throws SQLException {

    }

    @Override
    public void updateBinaryStream(String columnName, InputStream x, long length) throws SQLException {

    }

    @Override
    public void updateCharacterStream(String columnName, Reader reader, long length) throws SQLException {

    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException {

    }

    @Override
    public void updateBlob(String columnName, InputStream inputStream, long length) throws SQLException {

    }

    @Override
    public void updateClob(int columnIndex, Reader reader, long length) throws SQLException {

    }

    @Override
    public void updateClob(String columnName, Reader reader, long length) throws SQLException {

    }

    @Override
    public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {

    }

    @Override
    public void updateNClob(String columnName, Reader reader, long length) throws SQLException {

    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {

    }

    @Override
    public void updateNCharacterStream(String columnName, Reader reader) throws SQLException {

    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {

    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {

    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x) throws SQLException {

    }

    @Override
    public void updateAsciiStream(String columnName, InputStream x) throws SQLException {

    }

    @Override
    public void updateBinaryStream(String columnName, InputStream x) throws SQLException {

    }

    @Override
    public void updateCharacterStream(String columnName, Reader reader) throws SQLException {

    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {

    }

    @Override
    public void updateBlob(String columnName, InputStream inputStream) throws SQLException {

    }

    @Override
    public void updateClob(int columnIndex, Reader reader) throws SQLException {

    }

    @Override
    public void updateClob(String columnName, Reader reader) throws SQLException {

    }

    @Override
    public void updateNClob(int columnIndex, Reader reader) throws SQLException {

    }

    @Override
    public void updateNClob(String columnName, Reader reader) throws SQLException {

    }

    @Override
    public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
        return null;
    }

    @Override
    public <T> T getObject(String columnName, Class<T> type) throws SQLException {
        return null;
    }

    @Override
    public int getColumnCount() throws SQLException {

        return this.maxColumn;
    }

    @Override
    public boolean isAutoIncrement(int column) throws SQLException {
        return false;
    }

    @Override
    public boolean isCaseSensitive(int column) throws SQLException {
        return false;
    }

    @Override
    public boolean isSearchable(int column) throws SQLException {
        return false;
    }

    @Override
    public boolean isCurrency(int column) throws SQLException {
        return false;
    }

    @Override
    public int isNullable(int column) throws SQLException {
        if (checkColumn(column)) {
            // PLAN_CONTENT PLAN_NAME_DESCRIPTION can be null, other must not
            // null
            switch (column) {
                case PLAN_CONTENT_COLUMN_INDEX:
                case PLAN_DESCRIPTION_COLUMN_INDEX:
                    return 0;
                default:
                    return 1;
            }
        } else {
            return 1;
        }
    }

    @Override
    public boolean isSigned(int column) throws SQLException {
        return false;
    }

    // Max size
    @Override
    public int getColumnDisplaySize(int column) throws SQLException {
        switch (column) {
            case PLAN_ID_COLUMN_INDEX:
                return PLAN_ID_PRECISION;

            case PLAN_NAME_COLUMN_INDEX:
                return PLAN_NAME_PRECISION;

            case PLAN_CONTENT_COLUMN_INDEX:
                return PLAN_CONTENT_PRECISION;

            case PLAN_COST_COLUMN_INDEX:
                return PLAN_COST_PRECISION;

            case PLAN_CARDINALITY_COLUMN_INDEX:
                return PLAN_CARDINALITY_PRECISION;

            case PLAN_CPU_COST_COLUMN_INDEX:
                return PLAN_CPU_COST_PRECISION;

            case PLAN_DESCRIPTION_COLUMN_INDEX:
                return PLAN_DESCRIPTION_PRECISION;

            default:
                return 0;
        }
    }

    @Override
    public String getColumnName(int column) throws SQLException {
        return getColumnKeyName(column);
    }

    // Set the schema name of the Execution Plan Temporary Table as sys
    @Override
    public String getSchemaName(int column) throws SQLException {
        return DamengConstants.SCHEMA_SYS;
    }

    @Override
    public int getPrecision(int column) throws SQLException {
        return getColumnDisplaySize(column);// size

    }

    @Override
    public int getScale(int column) throws SQLException {
        return 0;
    }

    // Execution Plan Temporary Table's name
    @Override
    public String getTableName(int column) throws SQLException {

        return DamengConstants.DM_PLAN_TABLE;
    }

    @Override
    public String getCatalogName(int column) throws SQLException {
        return null;
    }

    @Override
    public int getColumnType(int column) throws SQLException {
        switch (column) {
            case PLAN_ID_COLUMN_INDEX:
                return Types.INTEGER;

            case PLAN_NAME_COLUMN_INDEX:
            case PLAN_CONTENT_COLUMN_INDEX:
            case PLAN_DESCRIPTION_COLUMN_INDEX:
            case PLAN_COST_COLUMN_INDEX:
            case PLAN_CARDINALITY_COLUMN_INDEX:
            case PLAN_CPU_COST_COLUMN_INDEX:
                return Types.VARCHAR;

            default:
                return Types.NULL;
        }

    }

    @Override
    public String getColumnTypeName(int column) throws SQLException {
        switch (column) {
            case PLAN_ID_COLUMN_INDEX:
                return "INTEGER";

            case PLAN_NAME_COLUMN_INDEX:
            case PLAN_CONTENT_COLUMN_INDEX:
            case PLAN_DESCRIPTION_COLUMN_INDEX:
            case PLAN_COST_COLUMN_INDEX:
            case PLAN_CARDINALITY_COLUMN_INDEX:
            case PLAN_CPU_COST_COLUMN_INDEX:
                return "VARCHAR";

            default:
                return "NULL";
        }

    }

    @Override
    public boolean isReadOnly(int column) throws SQLException {
        return true;
    }

    @Override
    public boolean isWritable(int column) throws SQLException {
        return false;
    }

    @Override
    public boolean isDefinitelyWritable(int column) throws SQLException {
        return false;
    }

    @Override
    public String getColumnClassName(int column) throws SQLException {
        return null;
    }

}
