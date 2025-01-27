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

import com.aestallon.storageexplorer.arcscript.internal.query.Assertion;
import com.aestallon.storageexplorer.arcscript.internal.query.QueryConditionImpl;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

final class ConditionEvaluator {

  private final StorageInstanceExaminer examiner;
  private final StorageEntry entry;
  private final StorageInstanceExaminer.ObjectEntryLookupTable cache;
  private final QueryConditionImpl.AssertionIterator iterator;

  ConditionEvaluator(final StorageInstanceExaminer examiner,
                     final StorageEntry entry,
                     final StorageInstanceExaminer.ObjectEntryLookupTable cache,
                     final QueryConditionImpl c) {
    this.examiner = examiner;
    this.entry = entry;
    this.cache = cache;
    this.iterator = (c != null)
        ? c.assertionIterator()
        : QueryConditionImpl.AssertionIterator.empty();
  }

  private ConditionEvaluator(final ConditionEvaluator orig,
                             final QueryConditionImpl c) {
    this(orig.examiner, orig.entry, orig.cache, c);
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
      // E.g. with C-style precedence, a || b && c can be short-circuited after verifying !!a,
      // for the precedence implies brackets: a || b && c IS EQUIVALENT TO a || (b && c).
      // Our Smalltalk-like strict adherence to left-to-right means implicit brackets starting
      // from the left: ((a || b) && c).
      // What _can_ we do then? We can skip evaluating operands! (this is not as cool as short-
      // circuiting, but hey). Following the above example (a || b && c), if a then b can be
      // skipped, reducing to (true && c)
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

  private boolean evalNext() {
    final var next = iterator.next();
    return switch (next.element()) {
      case Assertion a -> resolveValue(a);
      case QueryConditionImpl q -> evalNext(q);
    };
  }

  private boolean evalNext(QueryConditionImpl qc) {
    return new ConditionEvaluator(this, qc).evaluate();
  }

  private boolean resolveValue(final Assertion assertion) {
    final var val = examiner.discoverProperty(entry, assertion.prop());
    return assertion.check(val);
  }
}
