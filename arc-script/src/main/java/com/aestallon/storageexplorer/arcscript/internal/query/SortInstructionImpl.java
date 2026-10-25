/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
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

package com.aestallon.storageexplorer.arcscript.internal.query;

import java.util.ArrayList;
import java.util.List;
import com.aestallon.storageexplorer.arcscript.api.SortInstruction;
import com.aestallon.storageexplorer.arcscript.internal.ArcLiteral;

public class SortInstructionImpl implements SortInstruction {

  static final class SortOpImpl implements SortOp {

    private final String target;
    private boolean asc = true;

    private SortOpImpl(String target) {
      this.target = target;
    }

    @Override
    public void asc() {
      asc = true;
    }

    @Override
    public void desc() {
      asc = false;
    }

    public String get(String name) {
      if ("asc".equalsIgnoreCase(name)) {
        asc();
        return "asc";
      } else if ("desc".equalsIgnoreCase(name)) {
        desc();
        return "desc";
      } else {
        throw new IllegalArgumentException("Unknown sort order: " + name);
      }
    }

    SortKey asSortKey() {
      return new SortKey(target, asc);
    }

  }


  final List<SortOpImpl> _ops = new ArrayList<>();

  @Override
  public SortOp by(String prop) {
    if (prop == null || prop.isBlank()) {
      throw new IllegalArgumentException("property cannot be null or empty");
    }

    prop = prop.trim();
    if (_ops.stream().map(it -> it.target).anyMatch(prop::equals)) {
      throw new IllegalArgumentException("Duplicate sort key: [ " + prop + " ]!" );
    }

    final var op = new SortOpImpl(prop);
    _ops.add(op);
    return op;
  }

  @Override
  public SortOp by(ArcLiteral al) {
    return by(al.toString());
  }

  public ArcLiteral propertyMissing(String name) {
    return new ArcLiteral(name);
  }

}
