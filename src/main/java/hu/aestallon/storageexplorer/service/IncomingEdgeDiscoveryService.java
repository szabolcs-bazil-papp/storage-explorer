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

package hu.aestallon.storageexplorer.service;

import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;
import org.graphstream.graph.Graph;
import org.springframework.stereotype.Service;
import hu.aestallon.storageexplorer.service.internal.StorageEntry;
import hu.aestallon.storageexplorer.service.internal.StorageIndex;
import hu.aestallon.storageexplorer.util.Pair;
import hu.aestallon.storageexplorer.util.Uris;

@Service
public class IncomingEdgeDiscoveryService {

  private final StorageIndex storageIndex;

  public IncomingEdgeDiscoveryService(StorageIndex storageIndex) {
    this.storageIndex = storageIndex;
  }

  public Set<URI> execute(Graph graph, URI uri) {
    return storageIndex.refs()
        .filter(it -> !it.a().uri().getScheme().contains("viewcontext"))
        .filter(it -> it.b().stream().map(u -> u.uri).anyMatch(Uris.equalsIgnoringVersion(uri)))
        .filter(it -> NodeAdditionService.edgeMissing(graph, it.a().uri(), uri))
        .map(Pair::a)
        .map(StorageEntry::uri)
        .collect(Collectors.toSet());
  }

}
