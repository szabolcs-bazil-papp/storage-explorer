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

package com.aestallon.storageexplorer.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage-explorer")
public class StorageExplorerProperties {

  /**
   * The path to the REST API.
   */
  private String apiPath;

  /**
   * Whether to enable the UI.
   */
  private boolean uiEnabled = false;

  /**
   * The path to the UI.
   */
  private String uiPath = "/se-ui/";

  private String username = "";

  private String password = "";

  private Settings settings = new Settings();

  public String getApiPath() {
    return apiPath;
  }

  public void setApiPath(String apiPath) {
    this.apiPath = apiPath;
  }

  public boolean isUiEnabled() {
    return uiEnabled;
  }

  public void setUiEnabled(boolean uiEnabled) {
    this.uiEnabled = uiEnabled;
  }

  public String getUiPath() {
    return uiPath;
  }

  public void setUiPath(String uiPath) {
    this.uiPath = uiPath;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public Settings getSettings() {
    return settings;
  }

  public void setSettings(Settings settings) {
    this.settings = settings;
  }

  public static class Settings {

    /**
     * Whether to enable remote access.
     */
    private boolean webAllowOthers = false;

    public boolean getWebAllowOthers() {
      return webAllowOthers;
    }

    public void setWebAllowOthers(boolean webAllowOthers) {
      this.webAllowOthers = webAllowOthers;
    }
  }

}
