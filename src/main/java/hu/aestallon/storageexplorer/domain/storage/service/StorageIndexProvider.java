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

package hu.aestallon.storageexplorer.domain.storage.service;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.api.config.PlatformApiConfig;
import org.smartbit4all.core.object.ObjectApi;
import org.smartbit4all.core.object.ObjectDefinitionApi;
import org.smartbit4all.domain.data.storage.ObjectStorage;
import org.smartbit4all.storage.fs.StorageFS;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Service;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;
import hu.aestallon.storageexplorer.ui.controller.ViewController;

@Service
public class StorageIndexProvider {

  private final ApplicationEventPublisher eventPublisher;
  private final Map<String, StorageIndex> indicesByName;

  public StorageIndexProvider(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
    indicesByName = new HashMap<>();
  }

  public StorageIndex provide(final String name) {
    return indicesByName.get(name);
  }

  public Stream<StorageIndex> provide() {
    return indicesByName.values().stream();
  }

  public Stream<StorageEntry> searchForUri(final String queryString) {
    // TODO: Move here and implement!
    return Stream.empty();
  }

  public StorageIndex indexOf(final URI uri) {
    // TODO: don't do this, store backreference!
    return indicesByName.values().stream()
        .filter(it -> it.get(uri).isPresent())
        .findFirst()
        .orElseThrow();
  }

  public StorageIndex indexOf(final StorageEntry entry) {
    // TODO: Return Optional!
    return indexOf(entry.uri());
  }

  public StorageIndex init(Path path) {
    final String fsDirName = path.getFileName().toString();
    final String fsDirParentName = path.getParent().getFileName().toString();
    final String name = String.format("%s (%s)", fsDirName, fsDirParentName);

    final var ctx = new AnnotationConfigApplicationContext();
    ctx.register(PlatformApiConfig.class);
    ctx.registerBean(name, ObjectStorage.class, () -> new StorageFS(
        path.toFile(),
        ctx.getBean(ObjectDefinitionApi.class)));
    ctx.refresh();

    ObjectApi objectApi = ctx.getBean(ObjectApi.class);
    CollectionApi collectionApi = ctx.getBean(CollectionApi.class);

    final StorageIndex storageIndex = new StorageIndex(name, path, objectApi, collectionApi);
    indicesByName.put(name, storageIndex);
    eventPublisher.publishEvent(new ViewController.StorageImportEvent(storageIndex));
    return storageIndex;
  }



}
