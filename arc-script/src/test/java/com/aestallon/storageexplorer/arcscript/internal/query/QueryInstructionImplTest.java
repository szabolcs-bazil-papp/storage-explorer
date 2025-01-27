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

import org.junit.jupiter.api.Test;
import com.aestallon.storageexplorer.arcscript.api.Arc;
import com.aestallon.storageexplorer.arcscript.internal.ArcScriptImpl;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * These tests are performed in a somewhat unorthodox fashion. The main goal here is to test the
 * correct hierarchy of {@link QueryElement}s, not their actual execution (this task is left to the
 * tests pertaining to the engine itself).
 *
 * <p>
 * Thus, we primarily construct query instructions from parsing actual ArcScript bodies, and inspect
 * the ordering of their query elements.
 */
class QueryInstructionImplTest {

  @Test
  void queryWithNoWhereClause_containsNoQueryCondition() {
    final var arcScript = compileScript("""
        query {
             a 'Foo'
          from 'baz'
        }""");

    assertThat(arcScript.instructions).isNotNull().hasSize(1);
    final var instruction = arcScript.instructions.getFirst();

    assertThat(instruction).isInstanceOf(QueryInstructionImpl.class);
    final var query = (QueryInstructionImpl) instruction;
    assertThat(query.condition).isNull();
  }

  private static ArcScriptImpl compileScript(final String script) {
    final var arcScript = Arc.compile(script);
    assertThat(arcScript).isInstanceOf(ArcScriptImpl.class);
    return (ArcScriptImpl) arcScript;
  }

  @Test
  void queryWithOneAssertion_containsOneConditionWithOneAssertion() {
    final var arcScript = compileScript("""
        query {
              a 'Foo'
           from 'baz'
          where {
            str 'name' contains 'John'
          }
        }""");

    assertThat(arcScript.instructions).isNotNull().hasSize(1);
    final var instruction = arcScript.instructions.getFirst();

    assertThat(instruction).isInstanceOf(QueryInstructionImpl.class);
    final var query = (QueryInstructionImpl) instruction;

    final var condition = query.condition;
    assertThat(condition).isNotNull();

    final var assertionIterator = condition.assertionIterator();
    assertThat(assertionIterator.hasNext()).isTrue();

    final var onlyAssertion = assertionIterator.next();
    assertThat(onlyAssertion).isNotNull();
    assertThat(onlyAssertion.element()).isInstanceOf(Assertion.class);
    assertThat((Assertion) onlyAssertion.element())
        .returns("name", Assertion::prop)
        .returns("contains", Assertion::op)
        .returns("John", Assertion::displayValue);

    assertThat(assertionIterator.hasNext()).isFalse();
  }

}
