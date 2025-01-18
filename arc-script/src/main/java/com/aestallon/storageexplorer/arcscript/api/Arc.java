package com.aestallon.storageexplorer.arcscript.api;

import java.lang.invoke.VarHandle;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Executor;
import org.codehaus.groovy.control.CompilerConfiguration;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptResult;
import com.aestallon.storageexplorer.arcscript.internal.ArcScriptImpl;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptEngine;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import groovy.transform.Final;

public final class Arc {

  public static <SCRIPT extends Script & ArcScript> SCRIPT parse(final String script) {
    final CompilerConfiguration config = new CompilerConfiguration();
    config.setScriptBaseClass(ArcScriptImpl.class.getName());
    final var shell = new GroovyShell(Arc.class.getClassLoader(), config);
    return (SCRIPT) shell.parse(script);
  }

  public static <SCRIPT extends Script & ArcScript> ArcScript evaluate(final SCRIPT script) {
    script.run();
    return script;
  }

  public static ArcScriptResult execute(final ArcScript arcScript, final StorageInstance storageInstance) {
    final var engine = new ArcScriptEngine(null);
    return engine.execute(arcScript, storageInstance);
  }

  public static ArcScriptResult evaluate(final String script,
                                         final StorageInstance storageInstance) {
    try {
      final var s = parse(script);
      final var as = evaluate(s);
      return execute(as, storageInstance);
    } catch (Exception e) {
      return ArcScriptResult.err(e);
    }
  }

}
