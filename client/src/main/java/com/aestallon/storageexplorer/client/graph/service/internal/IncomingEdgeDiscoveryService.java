/*
 * Copyright (C) 2024 it4all Hungary Kft.
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

package com.aestallon.storageexplorer.client.graph.service.internal;

import java.util.stream.Stream;
import org.graphstream.graph.Graph;
import com.aestallon.storageexplorer.client.graph.GraphContainmentPredicate;
import com.aestallon.storageexplorer.client.userconfig.model.GraphSettings;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;

public class IncomingEdgeDiscoveryService {

  private final StorageInstance storageInstance;
  private final GraphContainmentPredicate inclusionCriterion;

  public IncomingEdgeDiscoveryService(StorageInstance storageInstance, GraphSettings settings) {
    this.storageInstance = storageInstance;
    this.inclusionCriterion = GraphContainmentPredicate.whiteListBlackListPredicate(settings);
  }

  public Stream<StorageEntry> execute(Graph graph, StorageEntry storageEntry) {
    return storageInstance.entities()
        .filter(it -> inclusionCriterion.test(graph, it))
        .filter(it -> it.references(storageEntry))
        .filter(it -> NodeAdditionService.edgeMissing(graph, it, storageEntry));
  }

}
