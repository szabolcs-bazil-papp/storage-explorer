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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.core.model.type.NominalType;
import com.aestallon.storageexplorer.core.model.type.Property;
import com.aestallon.storageexplorer.core.model.type.PropertyType;

@Service
public class NominalTypeService {

  private static final Logger log = LoggerFactory.getLogger(NominalTypeService.class);
  private final UserConfigService userConfigService;
  private final ConcurrentHashMap<String, Map<String, NominalType.Obj>> types;
  private final ConcurrentHashMap<String, PropertyType> propStructures;

  public NominalTypeService(UserConfigService userConfigService) {
    this.userConfigService = userConfigService;
    types = new ConcurrentHashMap<>();
    propStructures = new ConcurrentHashMap<>();
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

  public Optional<Pair<NominalType.Obj, NominalType.ObjProperty>> getProperty(
      NominalType.Obj objType,
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

  public PropertyType asPropertyType(String hostTypename, NominalType.ObjProperty nominalProp) {
    final var key = hostTypename + "." + nominalProp.key();
    return propStructures.computeIfAbsent(key, k -> constructStructure(nominalProp));
  }

  private PropertyType.Complex asPropertyType(String hostTypename) {
    final var type = propStructures.computeIfAbsent(hostTypename, this::constructPropertyType);
    assert type instanceof PropertyType.Complex;
    return (PropertyType.Complex) type;
  }

  private PropertyType.Complex constructPropertyType(String hostTypename) {
    return get(hostTypename)
        .map(obj -> obj.properties().stream()
            .map(prop -> {
              final String pKey = prop.key();
              final PropertyType p = asPropertyType(hostTypename, prop);
              return new Property(pKey, p);
            })
            .toList())
        .map(props -> new PropertyType.Complex(props, PropertyType.Arity.ONE))
        .orElseGet(PropertyType::ofComplex);
  }

  private PropertyType constructStructure(final NominalType.ObjProperty nominalProp) {
    final var arity = nominalProp.arity();
    final var required = nominalProp.required();
    final NominalType nominalType = nominalProp.type();
    PropertyType structuralType = switch (nominalType) {
      case NominalType.Primitive primitive -> switch (primitive) {
        case F32, F64, I32, I64, NUM -> PropertyType.NUM;
        case STR, DATE, TIME, UUID ->  PropertyType.STR;
        case BOOL -> PropertyType.BOOL;
        case URI -> new PropertyType.Ref("?", arity);
      };
      case NominalType.Ref(var target) -> asPropertyType(target);
      case NominalType.Obj obj -> {
        log.warn("Huh? {} on {}", obj, nominalProp);
        yield asPropertyType(obj.typeName());
      }
    };

    structuralType = structuralType.withArity(arity);
    if (!required) {
      structuralType = PropertyType.union(structuralType, PropertyType.NULL);
    }

    return structuralType;
  }
}
