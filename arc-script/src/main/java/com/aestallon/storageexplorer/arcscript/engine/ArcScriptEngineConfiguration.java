package com.aestallon.storageexplorer.arcscript.engine;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ArcScriptEngineConfiguration {

  /**
   * Creates a configuration based on the provided {@link QueryEngineSettings}, selecting the
   * {@link QueryEngine} implementation dictated by {@link QueryEngineSettings#engineMode()}.
   *
   * @param settings the {@link QueryEngineSettings} to apply, not null
   *
   * @return an {@link ArcScriptEngineConfiguration}, never null
   */
  public static ArcScriptEngineConfiguration of(final QueryEngineSettings settings) {
    Objects.requireNonNull(settings, "settings cannot be null!");
    final QueryEngine queryEngine = switch (settings.engineMode()) {
      case LEGACY -> new QueryEngineImpl();
      case PIPELINED -> new PipelinedQueryEngine(settings);
      case ARROW -> new ArrowQueryEngine(settings);
    };
    return new ArcScriptEngineConfiguration(queryEngine, settings);
  }

  final QueryEngine queryEngine;
  private final QueryEngineSettings settings;

  public ArcScriptEngineConfiguration(final QueryEngine queryEngine) {
    this(queryEngine, QueryEngineSettings.defaults());
  }

  private ArcScriptEngineConfiguration(final QueryEngine queryEngine,
                                       final QueryEngineSettings settings) {
    this.queryEngine = queryEngine;
    this.settings = settings;
  }

  public QueryEngineSettings settings() {
    return settings;
  }

  /**
   * The pagination registry backing this configuration's large ARROW-engine results, for callers
   * (Swing/Spring/CLI) that need to fetch subsequent pages of a {@link ArcScriptResult.PageInfo}.
   * Empty when {@link #settings()}'s {@link QueryEngineSettings#engineMode()} isn't {@code ARROW}.
   */
  public Optional<ArrowResultSetStore> arrowResultStore() {
    return (queryEngine instanceof ArrowQueryEngine arrow)
        ? Optional.of(arrow.resultStore())
        : Optional.empty();
  }
  
}
