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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.graphstream.graph.Graph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import hu.aestallon.storageexplorer.util.IO;
import static hu.aestallon.storageexplorer.util.Uris.equalsIgnoringVersion;

@Service
public class IncomingEdgeDiscoveryService {

  private final String fsBaseDirectory;

  public IncomingEdgeDiscoveryService(@Value("${fs.base.directory:./fs}") String fsBaseDirectory) {
    this.fsBaseDirectory = fsBaseDirectory;
  }

  public Set<URI> execute(Graph graph, URI uri) {
    try (final var files = Files.walk(Path.of(fsBaseDirectory))) {
      return files
          .filter(p -> p.toFile().isFile())
          .map(IO::read)
          .filter(it -> it.contains(uri.toString()))
          .flatMap(IO.findObjectUri().andThen(Optional::stream))
          .filter(equalsIgnoringVersion(uri).negate())
          .filter(it -> !it.getScheme().endsWith("-collections")) // TODO: Handle collections!
          .filter(it -> NodeAdditionService.edgeMissing(graph, it, uri))
          .collect(Collectors.toSet());
    } catch (IOException e) {
      return Collections.emptySet();
    }
  }

}
