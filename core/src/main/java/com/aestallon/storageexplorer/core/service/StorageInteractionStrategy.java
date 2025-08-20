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

package com.aestallon.storageexplorer.core.service;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.core.object.ObjectNode;
import com.aestallon.storageexplorer.common.util.IO;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryMeta;
import com.aestallon.storageexplorer.core.util.Uris;
import com.google.common.base.Strings;

abstract sealed class StorageInteractionStrategy<T extends StorageIndex<T>, U extends ObjectEntryLoadingService<T>> {

  private static final Logger log = LoggerFactory.getLogger(StorageInteractionStrategy.class);


  @FunctionalInterface
  interface Factory<T extends StorageIndex<T>, U extends ObjectEntryLoadingService<T>, R extends StorageInteractionStrategy<T, U>> {

    R create(U loadingService);

  }


  protected final U loadingService;

  protected StorageInteractionStrategy(final U loadingService) {
    this.loadingService = loadingService;
  }

  abstract static sealed class FileSystem extends
      StorageInteractionStrategy<FileSystemStorageIndex, ObjectEntryLoadingService.FileSystem> {

    protected FileSystem(ObjectEntryLoadingService.FileSystem loadingService) {
      super(loadingService);
    }

    protected abstract ObjectNode loadObjectNode(ObjectEntry entry);

    static final class Autonomous extends FileSystem {

      Autonomous(ObjectEntryLoadingService.FileSystem loadingService) {
        super(loadingService);
      }

      @Override
      protected ObjectNode loadObjectNode(ObjectEntry entry) {
        if (Uris.isSingleVersion(entry.uri())) {
          // We have to do this...
          return tryDeserialise(entry)
              .map(it -> loadingService.storageIndex.objectApi.create(null, it))
              .orElse(null);
        }
        try {
          return loadingService.storageIndex.objectApi.loadLatest(entry.uri());
        } catch (final Exception e) {
          log.error(e.getMessage(), e);
          return null;
        }
      }

      private Optional<Map<?, ?>> tryDeserialise(final ObjectEntry entry) {
        final var entryPath = entry.path();
        // ObjectEntry::path is never null here, because FileSystemStorageIndex guarantees it!
        assert entryPath != null;
        final String rawContent = IO.read(entryPath);
        if (Strings.isNullOrEmpty(rawContent)) {
          log.error("Empty content found during deserialisation attempt of [ {} ]", entry.uri());
          return Optional.empty();
        }

        try {
          final Map<?, ?> res = loadingService.storageIndex.objectApi
              .getDefaultSerializer()
              .fromString(rawContent, LinkedHashMap.class);
          return Optional.of(res);
        } catch (IOException e) {
          log.error("Error during deserialisation attempt of [ {} ]", entry.uri());
          log.error(e.getMessage(), e);
          return Optional.empty();
        }
      }

    }


    static final class Trusting extends FileSystem {

      Trusting(ObjectEntryLoadingService.FileSystem loadingService) {
        super(loadingService);
      }

      @Override
      protected ObjectNode loadObjectNode(ObjectEntry entry) {
        try {
          return loadingService.storageIndex.objectApi.load(entry.uri());
        } catch (final Exception e) {
          log.error(e.getMessage(), e);
          return null;
        }
      }

    }

  }


  abstract static sealed class RelationalDatabase extends
      StorageInteractionStrategy<RelationalDatabaseStorageIndex, ObjectEntryLoadingService.RelationalDatabase> {

    protected RelationalDatabase(ObjectEntryLoadingService.RelationalDatabase loadingService) {
      super(loadingService);
    }

    protected abstract ObjectEntryLoadResult.SingleVersion.Eager loadExact(URI uri, long version);

    protected abstract List<ObjectEntryLoadResult> loadBatch(List<URI> uris);

    static final class Autonomous extends RelationalDatabase {

      Autonomous(ObjectEntryLoadingService.RelationalDatabase loadingService) {
        super(loadingService);
      }

      @Override
      protected ObjectEntryLoadResult.SingleVersion.Eager loadExact(URI uri, long version) {
        return loadingService.storageIndex.loadSingle(uri, version);
      }

      @Override
      protected List<ObjectEntryLoadResult> loadBatch(List<URI> uris) {
        return loadingService.storageIndex.loadBatch(uris);
      }
    }


    static final class Trusting extends RelationalDatabase {

      Trusting(ObjectEntryLoadingService.RelationalDatabase loadingService) {
        super(loadingService);
      }

      @Override
      protected ObjectEntryLoadResult.SingleVersion.Eager loadExact(URI uri, long version) {
        try {
          final var node = loadingService.storageIndex.objectApi.load(Uris.atVersion(uri, version));
          return new ObjectEntryLoadResult.SingleVersion.Eager(
              ObjectEntryMeta.of(node.getData()),
              node.getObjectAsMap(),
              ObjectEntryLoadingService.OBJECT_MAPPER);
        } catch (final Exception e) {
          log.error(e.getMessage(), e);
          return new ObjectEntryLoadResult.SingleVersion.Eager(
              new ObjectEntryMeta(uri, null, null, version, null, null, null),
              Collections.emptyMap(),
              ObjectEntryLoadingService.OBJECT_MAPPER);
        }
      }

      @Override
      protected List<ObjectEntryLoadResult> loadBatch(List<URI> uris) {
        try {
          return loadingService.storageIndex.objectApi.loadBatch(uris).stream()
              .map(node -> {
                final var head = new ObjectEntryLoadResult.SingleVersion.Eager(
                    ObjectEntryMeta.of(node.getData()),
                    node.getObjectAsMap(),
                    ObjectEntryLoadingService.OBJECT_MAPPER);
                if (Uris.isSingleVersion(Uris.latest(node.getObjectUri()))) {
                  return head;
                } else {
                  return ObjectEntryLoadResult.multiVersion(
                      head,
                      loadingService::loadExact,
                      node.getVersionNr());
                }

              })
              .toList();
        } catch (final Exception e) {
          return IntStream.range(0, uris.size())
              .<ObjectEntryLoadResult>mapToObj(i -> new ObjectEntryLoadResult.Err(e.getMessage()))
              .toList();
        }
      }

    }

  }

}
