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

package com.aestallon.storageexplorer.arcscript.engine;

import java.util.List;
import com.aestallon.storageexplorer.arcscript.internal.query.Assertion;
import com.aestallon.storageexplorer.arcscript.internal.query.QueryConditionImpl;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

final class ConditionEvaluator {

  private final StorageInstanceExaminer examiner;
  private final StorageEntry entry;
  private final StorageInstanceExaminer.ObjectEntryLookupTable cache;
  private final QueryConditionImpl.AssertionIterator iterator;
  private final StorageInstanceExaminer.PropertyDiscoveryResult medial;
  private final PropertySource propertySource;

  ConditionEvaluator(final StorageInstanceExaminer examiner,
                     final StorageEntry entry,
                     final StorageInstanceExaminer.ObjectEntryLookupTable cache,
                     final QueryConditionImpl c) {
    this(examiner, entry, cache, c, null, null);
  }

  /**
   * Root-level constructor accepting a {@link PropertySource}: root ({@code medial == null})
   * single-valued assertions consult it before falling back to a fresh
   * {@code examiner.discoverProperty} call. Used by {@link ArrowQueryEngine} to source predicate
   * inputs from its Arrow-backed projection; {@code null} reproduces the original,
   * always-rediscover behaviour used by the LEGACY and PIPELINED engines.
   */
  ConditionEvaluator(final StorageInstanceExaminer examiner,
                     final StorageEntry entry,
                     final StorageInstanceExaminer.ObjectEntryLookupTable cache,
                     final QueryConditionImpl c,
                     final PropertySource propertySource) {
    this(examiner, entry, cache, c, null, propertySource);
  }

  private ConditionEvaluator(final StorageInstanceExaminer examiner,
                             final StorageEntry entry,
                             final StorageInstanceExaminer.ObjectEntryLookupTable cache,
                             final QueryConditionImpl c,
                             final StorageInstanceExaminer.PropertyDiscoveryResult medial,
                             final PropertySource propertySource) {
    this.examiner = examiner;
    this.entry = entry;
    this.cache = cache;
    this.iterator = (c != null)
        ? c.assertionIterator()
        : QueryConditionImpl.AssertionIterator.empty();
    this.medial = medial;
    this.propertySource = propertySource;
  }

  private ConditionEvaluator(final ConditionEvaluator orig,
                             final QueryConditionImpl c) {
    this(orig.examiner, orig.entry, orig.cache, c, orig.medial, orig.propertySource);
  }

  private ConditionEvaluator(final ConditionEvaluator orig,
                             final QueryConditionImpl c,
                             final StorageInstanceExaminer.PropertyDiscoveryResult medial) {
    this(orig.examiner, orig.entry, orig.cache, c, medial, orig.propertySource);
  }

  boolean evaluate() {
    if (!iterator.hasNext()) {
      // this is equivalent
      return true;
    }

    var state = evalNext();
    while (iterator.hasNext()) {
      final var relationNext = iterator.peekRelation();
      // because we are evaluating strictly left to right, we cannot short-circuit anywhere.
      // E.g. with C-style precedence, a || b && c can be short-circuited after verifying `a`,
      // for the precedence implies brackets: a || b && c IS EQUIVALENT TO a || (b && c).
      // Our Smalltalk-like strict adherence to left-to-right means implicit brackets starting
      // from the left: ((a || b) && c).
      // What_can we do then? We can skip evaluating operands (a.k.a poor man's short-circuiting).
      // Following the above example (a || b && c), if a then b can be skipped, reducing to 
      // (true && c) == c
      switch (relationNext) {
        case AND -> {
          if (state) {
            // if a then a && b = b
            state = evalNext();
          } else {
            // else if !a then a && b = false
            skipNext();
          }
        }
        case OR -> {
          if (state) {
            // if a then a || b = true
            skipNext();
          } else {
            // if !a then a || b = b
            state = evalNext();
          }
        }  // end case
      } // end switch
    } // end when
    return state;
  }

  private void skipNext() {
    iterator.next();
  }

  private StorageInstanceExaminer.PropertyDiscoveryResult discoverRootProperty(final String prop) {
    if (propertySource != null) {
      final var fromSource = propertySource.get(prop);
      if (fromSource != null) {
        return fromSource;
      }
    }
    return examiner.discoverProperty(entry, prop, cache);
  }

  private boolean evalNext() {
    final var next = iterator.next();
    return switch (next.element()) {
      case Assertion a -> evalAssertion(a);
      case QueryConditionImpl q -> evalCondition(q);
    };
  }

  private boolean evalCondition(QueryConditionImpl qc) {
    return new ConditionEvaluator(this, qc).evaluate();
  }

  private boolean evalAssertion(final Assertion assertion) {
    final var val = (medial == null)
        ? discoverRootProperty(assertion.prop())
        : examiner.discoverProperty(medial, assertion.prop(), cache);
    if (assertion.isSingle()) {
      return assertion.check(val);
    }

    final var listElementCondition = assertion.listElementCondition();
    return switch (val) {
      case StorageInstanceExaminer.ListFound list -> {
        List<StorageInstanceExaminer.PropertyDiscoveryResult> es = list.value();
        final var matchOp = assertion.matchOp();
        final boolean anyMatch = Assertion.MatchOp.ANY == matchOp;
        final boolean allMatch = Assertion.MatchOp.ALL == matchOp;
        final boolean noneMatch = Assertion.MatchOp.NONE == matchOp;
        if (es.isEmpty()) {
          // if the list is empty, both allMatch and noneMatch are vacuously satisfied, and anyMatch
          // automatically fails: 
          yield !anyMatch;
        }

        for (final StorageInstanceExaminer.PropertyDiscoveryResult e : es) {
          final boolean match = new ConditionEvaluator(this, listElementCondition, e).evaluate();
          if (match) {
            if (anyMatch) {
              // anyMatch is satisfied already, short-circuit to TRUE:
              yield true;
            } else if (noneMatch) {
              // we found one element contradicting noneMatch, short-circuit to FALSE:
              yield false;
            }
          } else {
            if (allMatch) {
              // we found an element contradicting allMatch, short-circuit to FALSE:
              yield false;
            }
          }
        }

        // if we had anyMatch and any matched, we would have short-circuited already with TRUE;
        // if we had !anyMatch (all or none), we would have short-circuited already with FALSE if any
        // element contradicted it:
        yield !anyMatch;
      }
      default -> false;
    };
  }

}
