/*
 * Copyright (C) 2026 Szabolcs Bazil Papp
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

package com.aestallon.storageexplorer.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.api.config.PlatformApiConfig;
import org.smartbit4all.api.org.bean.User;
import org.smartbit4all.core.object.ObjectApi;
import org.smartbit4all.core.object.ObjectDefinitionApi;
import org.smartbit4all.domain.data.storage.ObjectStorage;
import org.smartbit4all.storage.fs.StorageFS;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import com.aestallon.storageexplorer.arcscript.api.Arc;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptResult;
import com.aestallon.storageexplorer.arcscript.engine.PipelinedQueryEngine;
import com.aestallon.storageexplorer.arcscript.engine.QueryEngine;
import com.aestallon.storageexplorer.arcscript.engine.QueryEngineImpl;
import com.aestallon.storageexplorer.arcscript.engine.QueryEngineSettings;
import com.aestallon.storageexplorer.arcscript.internal.ArcScriptImpl;
import com.aestallon.storageexplorer.arcscript.internal.query.QueryInstructionImpl;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.dto.Availability;
import com.aestallon.storageexplorer.core.model.instance.dto.FsStorageLocation;
import com.aestallon.storageexplorer.core.model.instance.dto.IndexingStrategyType;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceDto;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceType;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.service.FileSystemStorageIndex;
import com.aestallon.storageexplorer.core.service.IndexingStrategy;

/**
 * LEGACY vs PIPELINED ArcScript query engine benchmarks over real-world query shapes.
 *
 * <p>
 * A trial builds a genuine file-system smartbit4all storage in a temporary directory (a platform
 * context wrapping {@link StorageFS}), persists {@code entryCount} {@code User} objects plus a
 * stored list and a stored map referencing them, and indexes the schema <em>once</em> - mirroring
 * the production flow, where the engine-level implicit {@code index} instruction always completes
 * before a query engine runs. Each benchmark then measures a single pre-parsed {@code query}
 * instruction executed directly on the selected {@link QueryEngine}, so the measured difference is
 * exactly the engine, not script parsing or indexing.
 *
 * <p>
 * Note that entry <em>content</em> is not cached across invocations (each execution constructs a
 * fresh load cache, exactly as production does), so predicate/projection benchmarks measure the
 * realistic load-evaluate-project cycle.
 *
 * <p>
 * Sweep any knob from the JMH command line, e.g.
 * {@code ./gradlew benchmark:jmh -PjmhArgs="-p whereConcurrency=4,32,default -p entryCount=5000"}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgsAppend = "-Xmx2g")
public class QueryEngineBenchmark {

  private static final String SCHEMA = "bench";

  @Param({ "LEGACY", "PIPELINED" })
  public String engineMode;

  @Param({ "2000" })
  public int entryCount;

  /**
   * {@code default} applies {@link QueryEngineSettings#defaults()}; any integer (or {@code -1} for
   * unbounded) overrides the pipelined engine's {@code where} evaluation concurrency. Ignored by
   * the LEGACY engine.
   */
  @Param({ "default" })
  public String whereConcurrency;

  private Path storagePath;
  private AnnotationConfigApplicationContext ctx;
  private StorageInstance storageInstance;
  private QueryEngine engine;

  private QueryInstructionImpl fullScan;
  private QueryInstructionImpl filtered;
  private QueryInstructionImpl filteredProjection;
  private QueryInstructionImpl ordered;
  private QueryInstructionImpl orderedTopTen;
  private QueryInstructionImpl limitedUnordered;
  private QueryInstructionImpl listSourced;
  private QueryInstructionImpl mapSourced;

  @Setup(Level.Trial)
  public void setUp() throws IOException {
    storagePath = Files.createTempDirectory("se-bench");

    final var dto = new StorageInstanceDto()
        .id(UUID.randomUUID())
        .name("bench")
        .availability(Availability.AVAILABLE)
        .indexingStrategy(IndexingStrategyType.ON_DEMAND)
        .type(StorageInstanceType.FS)
        .fs(new FsStorageLocation().path(storagePath));
    storageInstance = StorageInstance.fromDto(dto);

    ctx = new AnnotationConfigApplicationContext();
    ctx.register(PlatformApiConfig.class);
    ctx.registerBean(
        storageInstance.id().toString(),
        ObjectStorage.class,
        () -> new StorageFS(
            storagePath.toFile(),
            ctx.getBean(ObjectDefinitionApi.class)));
    final Map<String, Object> props = new HashMap<>();
    props.put("applicationruntime.maintain.enabled", "false");
    props.put("invocationregistry.refresh.enabled", "false");
    props.put("application.setup.enabled", "false");
    props.put("dataseries.mgmt.enabled", "false");
    ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("default", props));
    ctx.refresh();

    final ObjectApi objectApi = ctx.getBean(ObjectApi.class);
    final var userUris = new java.util.ArrayList<java.net.URI>(entryCount);
    for (int i = 0; i < entryCount; i++) {
      userUris.add(objectApi.saveAsNew(SCHEMA, new User()
          .username("user_%05d".formatted(i))
          .name("User %05d".formatted(i))
          .email("user%05d@example.com".formatted(i))
          .inactive(i % 3 == 0)));
    }

    final CollectionApi collectionApi = ctx.getBean(CollectionApi.class);
    collectionApi.list(SCHEMA, "firstHalf").addAll(userUris.subList(0, entryCount / 2));
    final Map<String, java.net.URI> byUsername = new HashMap<>();
    for (int i = 0; i < userUris.size(); i++) {
      byUsername.put("user_%05d".formatted(i), userUris.get(i));
    }
    collectionApi.map(SCHEMA, "byUsername").putAll(byUsername);

    storageInstance.setIndex(new FileSystemStorageIndex(
        storageInstance.id(),
        objectApi,
        ctx.getBean(CollectionApi.class),
        storagePath,
        false));
    // one indexing pass up front, mirroring the implicit index instruction that always precedes
    // query execution in production:
    storageInstance.index().refresh(
        IndexingStrategy.of(IndexingStrategyType.INITIAL),
        new IndexingTarget(Set.of(SCHEMA), Set.of()));

    engine = createEngine();

    fullScan = parse("""
        query {
          every 'User'
          from 'bench'
        }""");
    filtered = parse("""
        query {
          every 'User'
          from 'bench'
          where { str 'name' contains '1' }
        }""");
    filteredProjection = parse("""
        query {
          every 'User'
          from 'bench'
          where { bool 'inactive' is false }
          show 'name', 'email'
        }""");
    ordered = parse("""
        query {
          every 'User'
          from 'bench'
          order { by 'name' }
          show 'name'
        }""");
    orderedTopTen = parse("""
        query {
          every 'User'
          from 'bench'
          order { by 'name' desc }
          show 'name'
          limit 10
        }""");
    limitedUnordered = parse("""
        query {
          every 'User'
          from 'bench'
          where { str 'name' contains '1' }
          limit 10
        }""");
    listSourced = parse("""
        query {
          list 'firstHalf'
          from 'bench'
          where { bool 'inactive' is false }
          show 'name'
        }""");
    mapSourced = parse("""
        query {
          map 'byUsername'
          from 'bench'
          show 'name'
        }""");
  }

  private QueryEngine createEngine() {
    final var builder = QueryEngineSettings.builder()
        .engineMode(QueryEngineSettings.EngineMode.valueOf(engineMode));
    if (!"default".equals(whereConcurrency)) {
      builder.whereConcurrency(Integer.parseInt(whereConcurrency));
    }
    final QueryEngineSettings settings = builder.build();
    return switch (settings.engineMode()) {
      case LEGACY -> new QueryEngineImpl();
      case PIPELINED -> new PipelinedQueryEngine(settings);
    };
  }

  private static QueryInstructionImpl parse(final String script) {
    final var arcScript = Arc.compile(script);
    return (QueryInstructionImpl) ((ArcScriptImpl) arcScript).instructions.getFirst();
  }

  @TearDown(Level.Trial)
  public void tearDown() throws IOException {
    if (ctx != null) {
      ctx.close();
    }
    if (storagePath != null) {
      try (Stream<Path> walk = Files.walk(storagePath)) {
        walk.sorted(Comparator.reverseOrder()).forEach(p -> {
          try {
            Files.delete(p);
          } catch (final IOException e) {
            // best effort - the OS reclaims stragglers in the temp directory eventually
          }
        });
      }
    }
  }

  @Benchmark
  public ArcScriptResult.InstructionResult fullScan() {
    return engine.execute(storageInstance, fullScan);
  }

  @Benchmark
  public ArcScriptResult.InstructionResult filtered() {
    return engine.execute(storageInstance, filtered);
  }

  @Benchmark
  public ArcScriptResult.InstructionResult filteredProjection() {
    return engine.execute(storageInstance, filteredProjection);
  }

  @Benchmark
  public ArcScriptResult.InstructionResult ordered() {
    return engine.execute(storageInstance, ordered);
  }

  @Benchmark
  public ArcScriptResult.InstructionResult orderedTopTen() {
    return engine.execute(storageInstance, orderedTopTen);
  }

  @Benchmark
  public ArcScriptResult.InstructionResult limitedUnordered() {
    return engine.execute(storageInstance, limitedUnordered);
  }

  @Benchmark
  public ArcScriptResult.InstructionResult listSourced() {
    return engine.execute(storageInstance, listSourced);
  }

  @Benchmark
  public ArcScriptResult.InstructionResult mapSourced() {
    return engine.execute(storageInstance, mapSourced);
  }

}
