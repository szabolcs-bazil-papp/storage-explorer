package com.aestallon.storageexplorer.core.model.type;

import java.util.List;

public sealed interface NominalType {

  String typeName();

  enum Primitive implements NominalType {
    STR {
      @Override
      public String typeName() {
        return "str";
      }
    },
    I32 {
      @Override
      public String typeName() {
        return "i32";
      }
    },
    I64 {
      @Override
      public String typeName() {
        return "i64";
      }
    },
    F32 {
      @Override
      public String typeName() {
        return "f32";
      }
    },
    F64 {
      @Override
      public String typeName() {
        return "f64";
      }
    },
    NUM {
      @Override
      public String typeName() {
        return "num";
      }
    },
    BOOL {
      @Override
      public String typeName() {
        return "bool";
      }
    },
    DATE {
      @Override
      public String typeName() {
        return "date";
      }
    },
    TIME {
      @Override
      public String typeName() {
        return "date-time";
      }
    },
    URI {
      @Override
      public String typeName() {
        return "URI";
      }
    },
    UUID {
      @Override
      public String typeName() {
        return "UUID";
      }
    }

  }

  record ObjProperty(String key, String description, NominalType type, PropertyType.Arity arity, boolean required) {}

  record Obj(String typeName, String description, List<ObjProperty> properties) implements NominalType {}

  record Ref(String typeName) implements NominalType {}
}
