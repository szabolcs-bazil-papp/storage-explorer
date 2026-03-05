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

package com.aestallon.storageexplorer.client.userconfig.service;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.core.model.type.NominalType;

@Service
public class NominalTypeService {

  private final UserConfigService userConfigService;
  private final ConcurrentHashMap<String, Map<String, NominalType.Obj>> types;

  public NominalTypeService(UserConfigService userConfigService) {
    this.userConfigService = userConfigService;
    types = new ConcurrentHashMap<>();
  }

  public void save(final Path yaml) {
    Pair<String, Map<String, NominalType.Obj>> res = userConfigService
        .typeInfoRepository()
        .saveYaml(yaml);
    if (res != null) {
      types.put(res.a(), res.b());
    }
  }

  public void load() {
    types.putAll(userConfigService.typeInfoRepository().loadYamls());
  }

  public Optional<NominalType.Obj> get(String typename) {
    return types.values().stream()
        .flatMap(it -> Optional.ofNullable(it.get(typename)).stream())
        .findFirst();
  }

  public Optional<? extends NominalType> get(String typename, String propertyPath) {
    if (propertyPath == null || propertyPath.isEmpty()) {
      return get(typename);
    }

    return get(typename).flatMap(objType -> get(objType, propertyPath));
  }

  public Optional<Pair<NominalType.Obj, NominalType.ObjProperty>> getProperty(NominalType.Obj objType,
                                                       String propertyPath) {
    final var dotIdx = propertyPath.indexOf('.');
    if (dotIdx < 0) {
      return objType.properties().stream()
          .filter(prop -> prop.key().equals(propertyPath))
          .findFirst()
          .map(prop -> Pair.of(objType, prop));
    }

    final var rootProp = propertyPath.substring(0, dotIdx);
    final var rest = propertyPath.substring(dotIdx + 1);
    return get(objType, rootProp).flatMap(propType -> switch (propType) {
      case NominalType.Obj obj -> getProperty(obj, rest);
      default -> Optional.empty();
    });
  }

  private Optional<? extends NominalType> get(NominalType.Obj objType, String propertyPath) {
    final var dotIdx = propertyPath.indexOf('.');
    if (dotIdx < 0) {
      return objType.properties().stream()
          .filter(prop -> prop.key().equals(propertyPath))
          .findFirst()
          .map(prop -> prop.type() instanceof NominalType.Ref(String target)
              ? get(target).map(NominalType.class::cast).orElse(prop.type())
              : prop.type());
    }

    final var rootProp = propertyPath.substring(0, dotIdx);
    final var rest = propertyPath.substring(dotIdx + 1);
    return objType.properties().stream()
        .filter(prop -> prop.key().equals(rootProp))
        .findFirst()
        .flatMap(prop -> prop.type() instanceof NominalType.Ref(String target)
            ? get(target)
            : Optional.empty())
        .flatMap(referencedType -> get(referencedType, rest));
  }

}
