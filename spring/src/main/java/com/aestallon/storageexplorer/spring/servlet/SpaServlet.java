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

package com.aestallon.storageexplorer.spring.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class SpaServlet extends HttpServlet {

  private static final String INDEX = "index.html";

  private static final Map<String, byte[]> RESOURCES = new ConcurrentHashMap<>();

  private final boolean allowOthers;
  private final String basePath;
  private final String apiPath;


  public SpaServlet(boolean allowOthers, String basePath, String apiPath) {
    this.allowOthers = allowOthers;
    this.basePath = basePath;
    this.apiPath = apiPath;
  }


  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    req.setCharacterEncoding("utf-8");

    if (!isReqAllowed(req)) {
      resp.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    String pathInfo = req.getPathInfo();
    if (pathInfo == null || pathInfo.isEmpty()) {
      pathInfo = INDEX;
    } else if (pathInfo.startsWith("/")) {
      pathInfo = pathInfo.substring(1);
    }

    var data = getResource(pathInfo);
    if (data == null) {
      sendIndex(resp);
      return;
    }

    resp.setContentLength(data.length);
    resp.setContentType(contentType(pathInfo));
    if (cacheable(pathInfo)) {
      resp.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000");
    } else {
      resp.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
    }

    resp.getOutputStream().write(data);
  }

  private String contentType(final String pathInfo) {
    if (pathInfo.endsWith(".html")) {
      return "text/html";
    } else if (pathInfo.endsWith(".js")) {
      return "text/javascript";
    } else if (pathInfo.endsWith(".css")) {
      return "text/css";
    } else if (pathInfo.endsWith(".png")) {
      return "image/png";
    } else if (pathInfo.endsWith(".ico")) {
      return "image/x-icon";
    } else if (pathInfo.endsWith(".woff")) {
      return "font/woff";
    } else if (pathInfo.endsWith(".woff2")) {
      return "font/woff2";
    } else if (pathInfo.endsWith(".eot")) {
      return "application/vnd.ms-fontobject";
    } else {
      return "application/octet-stream";
    }
  }

  private boolean cacheable(final String pathInfo) {
    return !pathInfo.endsWith(".js");
  }

  private boolean isReqAllowed(final HttpServletRequest req) {
    return allowOthers || isLocalReq(req);
  }

  private boolean isLocalReq(final HttpServletRequest req) {
    final String address = req.getRemoteAddr();
    try {
      final InetAddress inetAddress = InetAddress.getByName(address);
      return inetAddress.isLoopbackAddress();
    } catch (UnknownHostException | NoClassDefFoundError e) {
      return false;
    }
  }

  private void sendIndex(HttpServletResponse resp) throws IOException {
    final byte[] indexFile = getResource(INDEX);
    assert indexFile != null;

    resp.setContentType("text/html");
    resp.setContentLength(indexFile.length);
    resp.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
    resp.setHeader(HttpHeaders.PRAGMA, "no-cache");
    resp.setDateHeader(HttpHeaders.EXPIRES, 0);
    resp.getOutputStream().write(indexFile);
  }

  private byte[] getResource(final String path) throws IOException {
    final String pathToUse = "/storage-explorer/" + path;
    var data = RESOURCES.get(pathToUse);
    if (data == null) {
      data = loadResource(pathToUse);
      if (data != null) {
        RESOURCES.put(pathToUse, data);
      }
    }

    return data;
  }

  private byte[] loadResource(final String path) throws IOException {
    try (final InputStream is = getClass().getResourceAsStream(path)) {
      if (is == null) {
        return null;
      }

      return is.readAllBytes();
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    doGet(req, resp);
  }

  @Override
  public void init() throws ServletException {
    try {
      final byte[] data = getResource(INDEX);
      final String string = new String(data, StandardCharsets.UTF_8);
      final byte[] amendedData = string.replace(
              "var STORAGE_EXPLORER_API_PATH = '/storageexplorer';",
              "var STORAGE_EXPLORER_API_PATH = '" + apiPath + "';")
          .replace(
              "<base href=\"/\">",
              "<base href=\"" + basePath + "\">")
          .getBytes(StandardCharsets.UTF_8);
      RESOURCES.put("/storage-explorer/index.html", amendedData);

    } catch (IOException e) {
      throw new ServletException(e);
    }
  }

  @Override
  public void destroy() {
    RESOURCES.clear();
    super.destroy();
  }

}
