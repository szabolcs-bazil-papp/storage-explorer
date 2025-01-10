package com.aestallon.storageexplorer.arcscript.api;

import org.codehaus.groovy.control.CompilerConfiguration;
import com.aestallon.storageexplorer.arcscript.api.internal.ArcScriptBodyImpl;
import groovy.lang.GroovyShell;

public interface ArcScript {
  
  static ArcScriptBody evaluate(final String script) {
    final CompilerConfiguration config = new CompilerConfiguration();
    config.setScriptBaseClass(ArcScriptBodyImpl.class.getName());
    final var shell = new GroovyShell(ArcScript.class.getClassLoader(), config);
    final var qs = shell.parse(script);
    qs.run();
    return (ArcScriptBody) qs;
  }
  
}
