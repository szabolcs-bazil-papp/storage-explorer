/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package hu.aestallon.storageexplorer.jdbc;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLStreamHandler;
import java.net.http.HttpClient;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

public class Sb4Driver implements Driver {
  
  private static final String PREFIX = "jdbc:sb4a:";
  private final HttpClient httpClient;

  public Sb4Driver() {
    this.httpClient = HttpClient.newBuilder().build();
  }

  @Override
  public Connection connect(String url, Properties info) throws SQLException {
    if (!acceptsURL(url)) {
      throw new SQLException("Not a valid URL: " + url);
    }

    try {
      final URI uri = new URI(url.substring(PREFIX.length()) + "/storageexplorer/eval");
      return new Sb4Connection(httpClient, uri);
    } catch (URISyntaxException e) {
      throw new SQLException(e);
    }
  }

  @Override
  public boolean acceptsURL(String url) throws SQLException {
    return url != null && url.startsWith(PREFIX);
  }

  @Override
  public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
    return new DriverPropertyInfo[0];
  }

  @Override
  public int getMajorVersion() {
    return 0;
  }

  @Override
  public int getMinorVersion() {
    return 0;
  }

  @Override
  public boolean jdbcCompliant() {
    return false;
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return null;
  }
}
