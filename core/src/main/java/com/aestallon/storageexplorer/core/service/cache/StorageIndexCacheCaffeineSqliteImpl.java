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

package com.aestallon.storageexplorer.core.service.cache;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.apache.fury.Fury;
import org.apache.fury.ThreadSafeFury;
import org.apache.fury.config.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.aestallon.storageexplorer.core.model.entry.ListEntry;
import com.aestallon.storageexplorer.core.model.entry.MapEntry;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedEntry;
import com.aestallon.storageexplorer.core.model.entry.SequenceEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

public class StorageIndexCacheCaffeineSqliteImpl implements StorageIndexCache {

  private static final Logger log =
      LoggerFactory.getLogger(StorageIndexCacheCaffeineSqliteImpl.class);


  public record UriProps(Set<UriProperty> uriProperties, Set<UriProperty> scopedEntries) {

    static UriProps of(final StorageEntry storageEntry) {
      if (storageEntry.valid()) {
        return new UriProps(storageEntry.uriProperties(), Collections.emptySet());
      } else {
        final Set<UriProperty> uriProperties = new HashSet<>(storageEntry.uriPropertiesStrict());
        final Set<UriProperty> scopedEntries = (storageEntry instanceof ObjectEntry o)
            ? o.scopedEntriesAsUriProperties()
            : Collections.emptySet();
        return new UriProps(uriProperties, scopedEntries);
      }
    }

    StorageEntry toStorageEntry(final URI uri, final StorageEntryFactory factory) {
      return factory.create(uri)
          .map(it -> {
            it.setUriProperties(uriProperties());
            if (it instanceof ObjectEntry o && !scopedEntries.isEmpty()) {
              scopedEntries.stream().map(UriProperty::uri)
                  .flatMap(u -> factory.create(u).stream())
                  .filter(ScopedEntry.class::isInstance)
                  .map(ScopedEntry.class::cast)
                  .forEach(o::addScopedEntry);
            }
            return it;
          })
          .orElseThrow(() -> new IllegalStateException("Cannot restore " + uri));
    }
  }


  private static final class SqliteWriter {

    private final ExecutorService executor;
    private final LinkedBlockingQueue<StorageEntry> queue;
    private final JdbcClient sqlite;
    private final Future<?> future;

    private SqliteWriter(final JdbcClient sqlite) {
      this.sqlite = sqlite;
      this.executor = Executors.newSingleThreadExecutor();
      this.queue = new LinkedBlockingQueue<>();
      this.future = executor.submit(() -> {
        while (!Thread.currentThread().isInterrupted()) {
          try {

            final StorageEntry storageEntry = queue.poll(5L, TimeUnit.SECONDS);
            if (storageEntry != null) {
              saveInternal(storageEntry);
            }

          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("SqliteWriter is interrupted");
            return;
          }
        }

      });
    }

    private void save(final StorageEntry storageEntry) {
      queue.offer(storageEntry);
    }

    private void saveInternal(final StorageEntry storageEntry) {
      try {

        final String typename = switch (storageEntry) {
          case ObjectEntry o -> o.typeName();
          case ListEntry l -> "st_l";
          case MapEntry m -> "st_m";
          case SequenceEntry s -> "st_s";
        };
        final int scoped = storageEntry instanceof ScopedEntry ? 1 : 0;
        final UriProps uriProps = UriProps.of(storageEntry);
        final byte[] bytes = FURY.serializeJavaObject(uriProps);
        sqlite
            .sql("""
                insert or replace into storage_entry (uri, schema, typename, scoped, content)
                values (:uri, :schema, :typename, :scoped, :content)""")
            .params(Map.of(
                "uri", storageEntry.uri().toString(),
                "schema", storageEntry.uri().getScheme(),
                "typename", typename,
                "scoped", scoped,
                "content", bytes))
            .update();

      } catch (Exception e) {
        log.error("Error saving storage entry! {}", storageEntry.uri(), e);
      }
    }

    private void abort() {
      future.cancel(true);
      executor.shutdownNow();
    }
  }



  private static void initSqliteCacheDirectory(final String dbFolderStr) {
    final Path dbFolder = Path.of(dbFolderStr);
    if (!Files.exists(dbFolder)) {
      try {
        Files.createDirectories(dbFolder);
      } catch (IOException e) {
        throw new IllegalStateException("Cannot initialise cache dir!", e);
      }
    }
  }


  private static ThreadSafeFury fury() {
    final ThreadSafeFury fury = Fury.builder()
        .withCodegen(true)
        .withLanguage(Language.JAVA)
        .buildThreadSafeFury();
    fury.register(UriProperty.Segment.class);
    fury.register(UriProperty.Segment.Idx.class);
    fury.register(UriProperty.Segment.Key.class);
    fury.register(URI.class);
    fury.register(UriProperty.class);
    fury.register(UriProps.class);
    return fury;
  }

  private static final ThreadSafeFury FURY = fury();

  private final StorageEntryFactory storageEntryFactory;
  private final JdbcClient sqlite;
  private final LoadingCache<URI, StorageEntry> inner;
  private final SqliteWriter writer;

  StorageIndexCacheCaffeineSqliteImpl(final StorageId storageId,
                                      final StorageEntryFactory storageEntryFactory) {
    this.storageEntryFactory = storageEntryFactory;

    final String dbFolderStr = "."
                               + FileSystems.getDefault().getSeparator()
                               + "sqlite-cache";
    initSqliteCacheDirectory(dbFolderStr);
    final String dbFileStr = dbFolderStr
                             + FileSystems.getDefault().getSeparator()
                             + storageId.toString() + ".sqlite";

    final DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.sqlite.JDBC");
    dataSource.setUrl("jdbc:sqlite:" + dbFileStr);
    this.sqlite = JdbcClient.create(dataSource);
    initSqlite();
    this.writer = new SqliteWriter(sqlite);

    this.inner = Caffeine.newBuilder()
        .expireAfterAccess(30L, TimeUnit.SECONDS)
        .maximumSize(10_000L)
        .build(this::load);
  }

  private void initSqlite() {
    sqlite
        .sql("""
            create table if not exists storage_entry (
                uri varchar(300) not null primary key,
                schema varchar(100) not null,
                typename varchar(100) not null,
                scoped int not null default 0,
                content blob
            )""")
        .update();
  }

  private StorageEntry load(final URI uri) {
    return sqlite.sql("select content from storage_entry where uri = :uri")
        .param("uri", uri.toString())
        .query((r, i) -> parse(r, uri))
        .optional()
        .orElse(null);
  }

  private StorageEntry parse(final ResultSet r, final URI uri) throws SQLException {
    final var bytes = r.getBytes(1);
    final UriProps uriProps = FURY.deserializeJavaObject(bytes, UriProps.class);
    return uriProps.toStorageEntry(uri, storageEntryFactory);
  }



  @Override
  public void put(URI uri, StorageEntry storageEntry) {
    inner.put(uri, storageEntry);
    writer.save(storageEntry);
  }

  @Override
  public void putAll(Map<URI, StorageEntry> storageEntries) {
    inner.putAll(storageEntries);
    storageEntries.values().forEach(writer::save);
  }

  @Override
  public void merge(URI uri, StorageEntry storageEntry) {
    inner.get(uri, k -> {
      writer.save(storageEntry);
      return storageEntry;
    });
  }

  @Override
  public StorageEntry compute(URI uri,
                              BiFunction<? super URI, ? super StorageEntry, ? extends StorageEntry> f) {
    var e = inner.get(uri);
    e = f.apply(uri, e);
    writer.save(e);
    inner.put(uri, e);
    return e;
  }

  @Override
  public void clear() {
    inner.invalidateAll();
    sqlite.sql("delete from storage_entry where true").update();
  }

  @Override
  public Optional<StorageEntry> get(URI uri) {
    return Optional.ofNullable(inner.get(uri));
  }

  @Override
  public Stream<StorageEntry> stream() {
    final var uris = sqlite
        .sql("select uri from storage_entry")
        .query((r, i) -> URI.create(r.getString(1)))
        .list();
    return inner.getAll(uris).values().stream();
  }

  @Override
  public Stream<ObjectEntry> objectEntries() {
    final List<URI> uris = sqlite
        .sql("select uri from storage_entry where typename not in ('st_l', 'st_m', 'st_s')")
        .query((r, i) -> URI.create(r.getString(1)))
        .list();
    return inner.getAll(uris).values().stream().map(ObjectEntry.class::cast);
  }

  @Override
  public Stream<ScopedEntry> scopedEntries() {
    final List<URI> uris = sqlite
        .sql("select uri from storage_entry where scoped = 1")
        .query((r, i) -> URI.create(r.getString(1)))
        .list();
    return inner.getAll(uris).values().stream().map(ScopedEntry.class::cast);
  }

  @Override
  public Set<URI> knownUris() {
    return inner.asMap().keySet();
  }

}
