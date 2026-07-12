package com.aestallon.storageexplorer.arcscript.engine;

import java.util.Objects;

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

}
