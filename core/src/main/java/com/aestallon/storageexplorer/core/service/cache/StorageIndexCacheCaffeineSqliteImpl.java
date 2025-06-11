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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import com.aestallon.storageexplorer.core.userconfig.service.UserConfigService;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.base.Strings;

public class StorageIndexCacheCaffeineSqliteImpl implements StorageIndexCache {

  private static final Logger log =
      LoggerFactory.getLogger(StorageIndexCacheCaffeineSqliteImpl.class);

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

  private final StorageEntryFactory storageEntryFactory;
  private final JdbcClient sqlite;
  private final LoadingCache<URI, StorageEntry> inner;

  StorageIndexCacheCaffeineSqliteImpl(final StorageId storageId,
                                      final StorageEntryFactory storageEntryFactory) {
    this.storageEntryFactory = storageEntryFactory;

    final String dbFolderStr = UserConfigService.SETTINGS_FOLDER
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

    this.inner = Caffeine.newBuilder()
        .expireAfterAccess(30L, TimeUnit.SECONDS)
        .maximumSize(1_000L)
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
                content text
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
    StorageEntry storageEntry = storageEntryFactory.create(uri).orElseThrow();
    final var uriPropsStr = r.getString(1);
    if (!Strings.isNullOrEmpty(uriPropsStr)) {
      storageEntry.setUriProperties(parseUriProperties(uriPropsStr));
    }
    return storageEntry;
  }

  private Set<UriProperty> parseUriProperties(final String s) {
    return Arrays.stream(s.split("\\|"))
        .map(es -> es.split(";"))
        .map(keyVal -> {
          final String key = keyVal[0];
          final String es[] = key.split("\\.");
          final UriProperty.Segment[] segments = Arrays.stream(es)
              .map(e -> (UriProperty.Segment) (e.chars().allMatch(Character::isDigit)
                  ? new UriProperty.Segment.Idx(Integer.parseInt(e))
                  : new UriProperty.Segment.Key(e)))
              .toArray(UriProperty.Segment[]::new);
          final String value = keyVal[1];
          final URI uri = URI.create(value);
          return UriProperty.of(segments, uri);
        })
        .collect(Collectors.toSet());
  }

  private String stringifyUriProperties(final Set<UriProperty> uriProperties) {
    return uriProperties.stream()
        .map(it -> UriProperty.Segment.asString(it.segments()) + ";" + it.uri().toString())
        .collect(Collectors.joining("|"));
  }

  private void save(final StorageEntry storageEntry) {
    final String typename = switch (storageEntry) {
      case ObjectEntry o -> o.typeName();
      case ListEntry l -> "st_l";
      case MapEntry m -> "st_m";
      case SequenceEntry s -> "st_s";
    };
    final int scoped = storageEntry instanceof ScopedEntry ? 1 : 0;
    sqlite
        .sql("""
            insert or replace into storage_entry (uri, schema, typename, scoped, content)
            values (:uri, :schema, :typename, :scoped, :content)""")
        .params(Map.of(
            "uri", storageEntry.uri().toString(),
            "schema", storageEntry.uri().getScheme(),
            "typename", typename,
            "scoped", scoped,
            "content", stringifyUriProperties(storageEntry.uriPropertiesStrict())))
        .update();
  }

  @Override
  public void put(URI uri, StorageEntry storageEntry) {
    inner.put(uri, storageEntry);
    save(storageEntry);
  }

  @Override
  public void putAll(Map<URI, StorageEntry> storageEntries) {
    inner.putAll(storageEntries);
    storageEntries.values().forEach(this::save);
  }

  @Override
  public void merge(URI uri, StorageEntry storageEntry) {
    inner.get(uri, k -> {
      save(storageEntry);
      return storageEntry;
    });
  }

  @Override
  public StorageEntry compute(URI uri,
                              BiFunction<? super URI, ? super StorageEntry, ? extends StorageEntry> f) {
    var e = inner.get(uri);
    e = f.apply(uri, e);
    save(e);
    inner.put(uri, e);
    return e;
  }

  @Override
  public void clear() {
    sqlite.sql("truncate table storage_entry").update();
    inner.invalidateAll();
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

}
