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

package com.aestallon.storageexplorer.client.graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.graphstream.graph.Graph;
import com.aestallon.storageexplorer.client.userconfig.model.GraphSettings;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;

@FunctionalInterface
public interface GraphContainmentPredicate extends BiPredicate<Graph, StorageEntry> {
  
  static GraphContainmentPredicate whiteListBlackListPredicate(GraphSettings settings) {
    final var ps = new ArrayList<Predicate<StorageEntry>>(4);
    if (!settings.getBlacklistedSchemas().isEmpty()) {
      final var bs = new HashSet<>(settings.getBlacklistedSchemas());
      ps.add(it -> !bs.contains(it.uri().getScheme()));
    }
    
    if (!settings.getBlacklistedTypes().isEmpty()) {
      final var bt = new HashSet<>(settings.getBlacklistedTypes());
      ps.add(it -> !bt.contains(StorageEntry.typeNameOf(it)));
    }
    
    if (!settings.getWhitelistedSchemas().isEmpty()) {
      final var ws = new HashSet<>(settings.getWhitelistedSchemas());
      ps.add(it -> ws.contains(it.uri().getScheme()));
    }
    
    if (!settings.getWhitelistedTypes().isEmpty()) {
      final var wt = new HashSet<>(settings.getWhitelistedTypes());
      ps.add(it -> wt.contains(StorageEntry.typeNameOf(it)));
    }
    
    return (g, e) -> {
      for (final var p : ps) {
        if (!p.test(e)) {
          return false;
        }
      }
      return true;
    };
  }
  
}
