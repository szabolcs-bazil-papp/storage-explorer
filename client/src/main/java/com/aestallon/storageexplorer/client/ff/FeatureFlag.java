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

package com.aestallon.storageexplorer.client.ff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public enum FeatureFlag {

  ENTRY_EDITOR("--enable-editor", "e");

  private static Set<FeatureFlag> enabledFlags = EnumSet.noneOf(FeatureFlag.class);

  private final String value;
  private final String shorthand;

  FeatureFlag(String value, String shorthand) {
    this.value = value;
    this.shorthand = shorthand;
  }

  public static void parse(String[] args) {
    if (args == null || args.length == 0) {
      enabledFlags = EnumSet.noneOf(FeatureFlag.class);
      return;
    }

    final List<FeatureFlag> temp = new ArrayList<>(FeatureFlag.values().length);
    for (final var flag : FeatureFlag.values()) {
      if (Arrays.stream(args).anyMatch(arg -> matchesFlag(arg, flag))) {
        temp.add(flag);
      }
    }
    enabledFlags = temp.isEmpty() ? EnumSet.noneOf(FeatureFlag.class) : EnumSet.copyOf(temp);
  }

  private static boolean matchesFlag(final String arg, final FeatureFlag flag) {
    return arg.equals(flag.value)
           || (arg.length() > 1 && arg.charAt(0) == '-' && arg.contains(flag.shorthand));
  }

  public boolean isEnabled() {
    return enabledFlags.contains(this);
  }

}
