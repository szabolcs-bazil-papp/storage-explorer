package com.aestallon.storageexplorer.arcscript.api;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptEngine;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptEngineConfiguration;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptResult;
import com.aestallon.storageexplorer.arcscript.engine.QueryEngineSettings;
import com.aestallon.storageexplorer.arcscript.internal.ArcScriptImpl;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

public final class Arc {

  static <SCRIPT extends Script & ArcScript> SCRIPT parse(final String script) {
    final CompilerConfiguration config = new CompilerConfiguration();
    config.setScriptBaseClass(ArcScriptImpl.class.getName());

    final var imports = new ImportCustomizer();
    imports.addStaticStars("com.aestallon.storageexplorer.arcscript.api.ArcInterpreterFlag");
    config.addCompilationCustomizers(imports);

    final var shell = new GroovyShell(Arc.class.getClassLoader(), config);
    return (SCRIPT) shell.parse(script);
  }

  static <SCRIPT extends Script & ArcScript> ArcScript evaluate(final SCRIPT script) {
    script.run();
    return script;
  }

  static ArcScriptResult execute(final ArcScript arcScript, final StorageInstance storageInstance) {
    return execute(arcScript, storageInstance, QueryEngineSettings.defaults());
  }

  static ArcScriptResult execute(final ArcScript arcScript,
                                 final StorageInstance storageInstance,
                                 final QueryEngineSettings settings) {
    final var engine = new ArcScriptEngine(ArcScriptEngineConfiguration.of(settings));
    return engine.execute(arcScript, storageInstance);
  }

  public static ArcScript compile(final String script) {
    final var s = parse(script);
    return evaluate(s);
  }

  public static ArcScriptResult evaluate(final String script,
                                         final StorageInstance storageInstance) {
    return evaluate(script, storageInstance, QueryEngineSettings.defaults());
  }

  public static ArcScriptResult evaluate(final String script,
                                         final StorageInstance storageInstance,
                                         final QueryEngineSettings settings) {
    try {
      final var s = parse(script);
      final var as = evaluate(s);
      return execute(as, storageInstance, settings);
    } catch (Exception e) {
      return ArcScriptResult.err(e);
    }
  }

}
