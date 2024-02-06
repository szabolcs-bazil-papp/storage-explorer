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

package hu.aestallon.storageexplorer.domain.graph.service.internal;

import java.util.stream.Stream;
import org.graphstream.graph.Graph;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;
import hu.aestallon.storageexplorer.domain.storage.service.StorageIndex;

public class IncomingEdgeDiscoveryService {

  private final StorageIndex storageIndex;

  public IncomingEdgeDiscoveryService(StorageIndex storageIndex) {
    this.storageIndex = storageIndex;
  }

  public Stream<StorageEntry> execute(Graph graph, StorageEntry storageEntry) {
    return storageIndex.entities()
        .filter(it -> it.references(storageEntry))
        .filter(it -> NodeAdditionService.edgeMissing(graph, it, storageEntry));
  }

}