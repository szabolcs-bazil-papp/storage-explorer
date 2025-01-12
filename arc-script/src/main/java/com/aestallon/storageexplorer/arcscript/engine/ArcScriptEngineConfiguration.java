package com.aestallon.storageexplorer.arcscript.engine;

import java.io.PrintWriter;
import java.util.concurrent.Executor;

public final class ArcScriptEngineConfiguration {
  
  private final PrintWriter writer;
  private final ClassLoader classLoader;
  private final Executor executor;
  
  private ArcScriptEngineConfiguration(final PrintWriter writer, 
                                       final ClassLoader classLoader, 
                                       final Executor executor) {
    this.writer = writer;
    this.classLoader = classLoader;
    this.executor = executor;
  }
}
