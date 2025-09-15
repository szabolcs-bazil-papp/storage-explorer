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

import java.io.IOException;
import java.util.Collections;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import com.aestallon.storageexplorer.spring.auth.AuthService;
import com.aestallon.storageexplorer.spring.auth.ExplorerApiOncePerRequestFilter;
import com.aestallon.storageexplorer.spring.util.HttpRequests;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class DefaultExplorerApiOncePerRequestFilter extends ExplorerApiOncePerRequestFilter {

  private final AuthService authService;
  private final boolean allowOthers;

  public DefaultExplorerApiOncePerRequestFilter(AuthService authService, boolean allowOthers) {
    this.authService = authService;
    this.allowOthers = allowOthers;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {

    if (!isReqAllowed(request)) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    final var uri = request.getRequestURI();
    if (uri != null && uri.endsWith("/verify")) {
      filterChain.doFilter(request, response);
      return;
    }

    final String authorization = request.getHeader("Authorization");
    if (authService.validateToken(authorization)) {
      SecurityContextHolder.setContext(
          new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
              "storage-explorer-user",
              authorization,
              Collections.emptySet())));
      filterChain.doFilter(request, response);
    } else {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
  }

  private boolean isReqAllowed(final HttpServletRequest req) {
    return allowOthers || HttpRequests.isLocalReq(req);
  }

}
