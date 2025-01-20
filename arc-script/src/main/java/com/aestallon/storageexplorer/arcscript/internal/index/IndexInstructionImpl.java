package com.aestallon.storageexplorer.arcscript.internal.index;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.aestallon.storageexplorer.arcscript.api.IndexInstruction;
import com.aestallon.storageexplorer.arcscript.internal.Instruction;
import com.aestallon.storageexplorer.core.model.instance.dto.IndexingStrategyType;

public class IndexInstructionImpl implements IndexInstruction, Instruction {

  public final Set<String> _schemas = new HashSet<>();
  public final Set<String> _types = new HashSet<>();
  public IndexingStrategyType _strategy = IndexingStrategyType.INITIAL;

  @Override
  public void schemas(String... schemas) {
    if (schemas == null || schemas.length == 0) {
      throw new IllegalArgumentException("Schemas must have at least one schema");
    }

    _schemas.addAll(Arrays.stream(schemas).filter(Objects::nonNull).collect(Collectors.toSet()));
  }

  @Override
  public void types(String... types) {
    if (types == null || types.length == 0) {
      throw new IllegalArgumentException("Types must have at least one type");
    }

    _types.addAll(Arrays.stream(types).filter(Objects::nonNull).collect(Collectors.toSet()));
  }

  @Override
  public void method(IndexingStrategyType method) {
    _strategy = method;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("index ");
    if (_types.size() == 1) {
      sb.append("type ").append(_types.iterator().next()).append(" ");
    } else if (_types.size() > 1) {
      sb.append("types ").append(_types).append(" ");
    }
    
    if (_schemas.isEmpty()) {
      sb.append("everywhere ");
    } else if (_schemas.size() == 1) {
      sb.append("in schema ").append(_schemas.iterator().next()).append(" ");
    } else {
      sb.append("in schemas ").append(_schemas).append(" ");
    }
    
    return sb.append("with method ").append(_strategy).toString();
  }
}
