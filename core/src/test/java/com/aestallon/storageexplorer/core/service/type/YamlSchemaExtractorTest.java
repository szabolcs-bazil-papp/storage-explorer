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

package com.aestallon.storageexplorer.core.service.type;

import java.io.IOException;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import com.aestallon.storageexplorer.core.model.type.NominalType;

class YamlSchemaExtractorTest {

  @Test
  void foo() throws IOException {
    final var input = """
        components:
          schemas:
            Foo:
              type: object
              description: |
                Type-level description
              properties:
                a:
                  type: integer
                  format: int32
                  description: |
                    Description of a
                b:
                  description: Description of b
                  type: array
                  items:
                    type: string
                c:
                  type: string
                  format: uri
                  description: ref to Bar
                d:
                  type: array
                  items:
                    type: string
                    format: uri
                e:
                  type: RandomE
                  description: haha
              required: [ b, d ]""";
  final var extractor = new YamlSchemaExtractor();
    Map<String, NominalType.Obj> types = extractor.extract(input);
    assertThat(types)
        .isNotNull()
        .hasSize(1);

    final var foo = types.get("Foo");
    assertThat(foo).isNotNull();
    assertThat(foo.description()).contains("Type-level description");
    assertThat(foo.properties()).hasSize(5);

    assertThat(foo.properties().get(0))
        .returns("a", NominalType.ObjProperty::key)
        .returns("Description of a\n", NominalType.ObjProperty::description)
        .returns(NominalType.Primitive.I32, NominalType.ObjProperty::type)
        .returns(false, NominalType.ObjProperty::required);

  }

}
