package hu.aestallon.storageexplorer.domain.storage.service;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import org.springframework.jdbc.core.JdbcTemplate;
import com.google.common.base.Strings;
import com.intellij.uiDesigner.lw.IRootContainer;
import hu.aestallon.storageexplorer.domain.storage.model.entry.ObjectEntry;
import hu.aestallon.storageexplorer.domain.storage.model.entry.ScopedEntry;
import hu.aestallon.storageexplorer.domain.storage.model.entry.StorageEntry;
import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.StorageId;
import hu.aestallon.storageexplorer.util.Pair;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

public final class RelationalDatabaseStorageIndex extends StorageIndex {

  private static final Logger log = LoggerFactory.getLogger(RelationalDatabaseStorageIndex.class);

  private final JdbcTemplate db;

  RelationalDatabaseStorageIndex(
      StorageId storageId,
      ObjectApi objectApi,
      CollectionApi collectionApi,
      JdbcTemplate db) {
    super(storageId, objectApi, collectionApi);
    this.db = db;
  }

  @Override
  void refresh() {
    clear();
    final Map<URI, StorageEntry> map = db
        .query("SELECT URI FROM OBJECT_ENTRY", (r, i) -> r.getString("URI"))
        .stream()
        .filter(it -> !Strings.isNullOrEmpty(it))
        .map(URI::create)
        .map(uri -> Pair.of(uri, StorageEntry.create(storageId, uri, objectApi, collectionApi)))
        .flatMap(Pair.streamOnB())
        .map(Pair.onB(StorageEntry.class::cast))
        .collect(Pair.toMap());

    Map<String, List<ScopedEntry>> scopedEntries = map.values().stream()
        .filter(ScopedEntry.class::isInstance)
        .map(ScopedEntry.class::cast)
        .collect(groupingBy(it -> it.scope().getPath()));

    map.values().stream()
        .filter(ObjectEntry.class::isInstance)
        .map(ObjectEntry.class::cast)
        .forEach(it -> {
          final var scopedChildren = scopedEntries.get(it.uri().getPath());
          if (scopedChildren == null) {
            return;
          }

          scopedChildren.forEach(it::addScopedEntry);
        });
    cache.putAll(map);
  }
}
