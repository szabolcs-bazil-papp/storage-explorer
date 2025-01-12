package com.aestallon.storageexplorer.arcscript.api;

import java.net.URI;
import java.security.PrivateKey;
import java.util.List;
import org.codehaus.groovy.control.CompilerConfiguration;
import com.aestallon.storageexplorer.arcscript.api.internal.ArcScriptImpl;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptEngine;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptEngineConfiguration;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

public interface Arc {

  static <SCRIPT extends Script & ArcScript> SCRIPT parse(final String script) {
    final CompilerConfiguration config = new CompilerConfiguration();
    config.setScriptBaseClass(ArcScriptImpl.class.getName());
    final var shell = new GroovyShell(Arc.class.getClassLoader(), config);
    return (SCRIPT) shell.parse(script);
  }

  static <SCRIPT extends Script & ArcScript> ArcScript evaluate(final SCRIPT script) {
    script.run();
    return script;
  }

  static void execute(final ArcScript arcScript, final StorageInstance storageInstance) {
    final var engine = new ArcScriptEngine(null);
    final List<URI> uris = engine.execute(arcScript, storageInstance);
    System.out.println(uris); // :)
  }

}
