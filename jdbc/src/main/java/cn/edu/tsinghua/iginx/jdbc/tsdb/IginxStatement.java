/*
 * IGinX - the polystore system with high performance
 * Copyright (C) Tsinghua University
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package cn.edu.tsinghua.iginx.jdbc.tsdb;

import cn.edu.tsinghua.iginx.exception.SessionException;
import cn.edu.tsinghua.iginx.session.SessionQueryDataSet;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class IginxStatement implements Statement {

  protected IginxConnection connection;

  public IginxStatement(IginxConnection connection) {
    this.connection = connection;
  }

  @Override
  public ResultSet executeQuery(String sql) throws SQLException {
    String trimedSql = sql.trim().toLowerCase();
    if (!trimedSql.endsWith(";")) {
      return executeMagicQuery(trimedSql);
    }
    throw new SQLFeatureNotSupportedException("Only support magic query");
  }

  private static final String SCAN_SQL_PREFIX = "select * from sensor where ";

  private ResultSet executeMagicQuery(String sql) throws SQLException {
    if (sql.startsWith(SCAN_SQL_PREFIX)) {
      return executeMagicScan(sql.substring(SCAN_SQL_PREFIX.length()));
    } else if (sql.equals("describe table sensor")) {
      return new IginxResultSet(
          Collections.singletonList("Field"),
          Collections.singletonList(Types.VARCHAR),
          Arrays.asList(
              Collections.singletonList("device_id"),
              Collections.singletonList("time"),
              Collections.singletonList("field0")));
    }

    throw new SQLFeatureNotSupportedException("Magic query not supported: " + sql);
  }

  private static final Pattern SCAN_FILTER_PATTERN =
      Pattern.compile("device_id = '(.+)' and time  between (\\d+) and (\\d+)");

  private ResultSet executeMagicScan(String where) throws SQLException {
    Matcher matcher = SCAN_FILTER_PATTERN.matcher(where);
    if (!matcher.matches()) {
      throw new SQLSyntaxErrorException("Invalid scan filter: " + where);
    }
    String deviceId = matcher.group(1);
    long startTime = Long.parseLong(matcher.group(2));
    long endTime = Long.parseLong(matcher.group(3));
    String path = deviceId.replace(':', '.') + ".field0";
    List<String> paths = Collections.singletonList(path);
    List<List<Object>> values = new ArrayList<>();
    try {
      SessionQueryDataSet dataSet =
          connection.getSessionPool().queryData(paths, startTime, endTime);
      long[] keys = dataSet.getKeys();
      List<String> devices =
          dataSet.getPaths().stream()
              .map(p -> p.substring(0, p.lastIndexOf('.')))
              .map(p -> p.replace('.', ':'))
              .collect(Collectors.toList());
      List<List<Object>> valuesList = dataSet.getValues();
      for (int row = 0; row < keys.length; row++) {
        long key = keys[row];
        List<Object> rowData = valuesList.get(row);
        for (int col = 0; col < devices.size(); col++) {
          Object value = rowData.get(col);
          if (value == null) {
            continue;
          }
          String device = devices.get(col);
          values.add(Arrays.asList(device, key, new String((byte[]) value)));
        }
      }

      return new IginxResultSet(
          Arrays.asList("device_id", "time", "field0"),
          Arrays.asList(Types.VARCHAR, Types.BIGINT, Types.VARCHAR),
          values);
    } catch (SessionException e) {
      throw new SQLException("Fail to execute scan", e);
    }
  }

  @Override
  public void close() throws SQLException {}

  @Override
  public ResultSet getResultSet() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public boolean execute(String sql) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public int executeUpdate(String sql) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public int executeUpdate(String sql, String[] columnNames) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public boolean execute(String sql, int[] columnIndexes) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public boolean execute(String sql, String[] columnNames) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public int[] executeBatch() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void addBatch(String sql) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void clearBatch() throws SQLFeatureNotSupportedException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public Connection getConnection() throws SQLFeatureNotSupportedException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public boolean isClosed() throws SQLFeatureNotSupportedException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public SQLWarning getWarnings() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void clearWarnings() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public int getResultSetType() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public int getFetchDirection() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setFetchDirection(int direction) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public int getMaxFieldSize() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setMaxFieldSize(int max) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public int getMaxRows() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setMaxRows(int max) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setEscapeProcessing(boolean enable) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public int getQueryTimeout() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setQueryTimeout(int seconds) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void cancel() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setCursorName(String name) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public int getUpdateCount() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public boolean getMoreResults() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public int getFetchSize() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setFetchSize(int rows) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public int getResultSetConcurrency() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public boolean getMoreResults(int current) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public ResultSet getGeneratedKeys() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public int getResultSetHoldability() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public boolean isPoolable() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setPoolable(boolean poolable) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void closeOnCompletion() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public boolean isCloseOnCompletion() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }
}