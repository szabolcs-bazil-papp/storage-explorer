package com.aestallon.storageexplorer.core.model.type;

import java.util.List;

public sealed interface NominalType {

  String typeName();

  String description();

  enum Primitive implements NominalType {
    STR {
      @Override
      public String typeName() {
        return "str";
      }

      @Override
      public String description() {
        return "UTF-8 encoded text";
      }
    },
    I32 {
      @Override
      public String typeName() {
        return "i32";
      }

      @Override
      public String description() {
        return "Signed 32-bit integer";
      }
    },
    I64 {
      @Override
      public String typeName() {
        return "i64";
      }

      @Override
      public String description() {
        return "Signed 64-bit integer";
      }
    },
    F32 {
      @Override
      public String typeName() {
        return "f32";
      }

      @Override
      public String description() {
        return "Signed 32-bit floating point number";
      }
    },
    F64 {
      @Override
      public String typeName() {
        return "f64";
      }

      @Override
      public String description() {
        return "Signed 64-bit floating point number";
      }
    },
    NUM {
      @Override
      public String typeName() {
        return "num";
      }

      @Override
      public String description() {
        return "Number (uncategorized format)";
      }
    },
    BOOL {
      @Override
      public String description() {
        return "Binary value: true or false.";
      }

      @Override
      public String typeName() {
        return "bool";
      }
    },
    DATE {
      @Override
      public String description() {
        return "Textual representation of a date, containing year, month and day-of-month information.";
      }

      @Override
      public String typeName() {
        return "date";
      }
    },
    TIME {
      @Override
      public String description() {
        return "Textual representation of a date and time, containing year, month, day-of-month, hour, minute, second and potentially finer resolution information. May contain timezone or offset information.";
      }

      @Override
      public String typeName() {
        return "date-time";
      }
    },
    URI {
      @Override
      public String description() {
        return "Universal Resource Identifier. Potentially denotes a persistent entry in the smartbit4all storage.";
      }

      @Override
      public String typeName() {
        return "URI";
      }
    },
    UUID {
      @Override
      public String description() {
        return "Universally Unique Identifier";
      }

      @Override
      public String typeName() {
        return "UUID";
      }
    }

  }

  sealed interface Root {}

  record Enumeration(String typeName, String description, List<String> values)
      implements NominalType, Root {

  }


  record ObjProperty(String key, String description, NominalType type, PropertyType.Arity arity,
      boolean required) {}


  record Obj(String typeName, String description, List<ObjProperty> properties)
      implements NominalType, Root {}


  record Ref(String typeName) implements NominalType {
    @Override
    public String description() {
      return "Symbolic reference to " + typeName;
    }
  }

  record Unknown() implements NominalType, Root {
    @Override
    public String typeName() {
      return "?";
    }

    @Override
    public String description() {
      return "Unknown type";
    }
  }
}
