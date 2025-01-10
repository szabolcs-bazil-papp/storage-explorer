package com.aestallon.storageexplorer.queryscript.api;

import org.codehaus.groovy.control.CompilerConfiguration;
import com.aestallon.storageexplorer.queryscript.api.internal.QueryScriptImpl;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;

public interface QueryScript {
  
  static QueryScript evaluate(final String script) {
    final CompilerConfiguration config = new CompilerConfiguration();
    config.setScriptBaseClass(QueryScriptImpl.class.getName());
    final var shell = new GroovyShell(QueryScriptImpl.class.getClassLoader(), config);
    final var qs = shell.parse(script);
    qs.run();
    return (QueryScript) qs;
  }
  
  Query query(Closure closure);
  
}
