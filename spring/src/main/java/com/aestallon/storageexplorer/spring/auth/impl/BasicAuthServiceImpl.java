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

package com.aestallon.storageexplorer.spring.auth.impl;

import java.util.Base64;
import com.aestallon.storageexplorer.spring.auth.AuthService;

public class BasicAuthServiceImpl implements AuthService {

  private final String token;

  public BasicAuthServiceImpl(String username, String password) {
    this.token = issueTokenInternal(username, password, false);
  }

  @Override
  public String issueToken(String username, String password) {
    return issueTokenInternal(username, password, true);
  }

  private String issueTokenInternal(String username, String password, boolean verify) {
    final var encoder = Base64.getEncoder();
    final var credentials = username + ":" + password;
    final var encoded = encoder.encodeToString(credentials.getBytes());
    final var token = "Basic " + encoded;
    if (!verify || validateToken(token)) {
      return token;
    }
    return null;
  }

  @Override
  public boolean validateToken(String token) {
    return this.token.equals(token);
  }

}
