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
import static com.aestallon.storageexplorer.arcscript.engine.ArcPropertyPaths.toPath;
import com.aestallon.storageexplorer.arcscript.internal.query.Assertion;
import com.aestallon.storageexplorer.arcscript.internal.query.QueryConditionImpl;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;
import com.aestallon.storageexplorer.core.service.index.QueryableIndex;
import com.aestallon.storageexplorer.core.service.index.RowId;
import com.aestallon.storageexplorer.core.service.index.impl.ExaminerBackedProjection;

final class ConditionEvaluator {

  private final StorageInstanceExaminer examiner;
  private final StorageEntry entry;
  private final QueryableIndex index;
  private final StorageInstanceExaminer.ObjectEntryLookupTable cache;
  private final QueryConditionImpl.AssertionIterator iterator;
  private final StorageInstanceExaminer.PropertyDiscoveryResult medial;
  /** When set (alongside {@link #row}), a top-level assertion lookup (medial == null) resolves
   *  through the query's shared {@link ExaminerBackedProjection} instead of calling {@code
   *  discoverProperty} directly - every plain property reference at any AND/OR nesting depth
   *  shares one cached cell this way. Recursive {@code any}/{@code all}/{@code none} list-element
   *  lookups (medial != null) always go straight through {@link #examiner}: each list element is
   *  a synthetic, non-columnar value, visited once regardless. Null for the legacy (non-pipelined)
   *  evaluation path, which keeps its original, uncached behavior exactly. */
  private final ExaminerBackedProjection projection;
  private final RowId row;

  ConditionEvaluator(final StorageInstanceExaminer examiner,
                     final StorageEntry entry,
                     final QueryableIndex index,
                     final StorageInstanceExaminer.ObjectEntryLookupTable cache,
                     final QueryConditionImpl c) {
    this(examiner, entry, index, cache, c, null, null, null);
  }

  ConditionEvaluator(final ExaminerBackedProjection projection,
                     final RowId row,
                     final StorageInstanceExaminer examiner,
                     final StorageEntry entry,
                     final QueryableIndex index,
                     final StorageInstanceExaminer.ObjectEntryLookupTable cache,
                     final QueryConditionImpl c) {
    this(examiner, entry, index, cache, c, null, projection, row);
  }

  private ConditionEvaluator(final StorageInstanceExaminer examiner,
                             final StorageEntry entry,
                             final QueryableIndex index,
                             final StorageInstanceExaminer.ObjectEntryLookupTable cache,
                             final QueryConditionImpl c,
                             final StorageInstanceExaminer.PropertyDiscoveryResult medial,
                             final ExaminerBackedProjection projection,
                             final RowId row) {
    this.examiner = examiner;
    this.entry = entry;
    this.index = index;
    this.cache = cache;
    this.iterator = (c != null)
        ? c.assertionIterator()
        : QueryConditionImpl.AssertionIterator.empty();
    this.medial = medial;
    this.projection = projection;
    this.row = row;
  }

  private ConditionEvaluator(final ConditionEvaluator orig,
                             final QueryConditionImpl c) {
    this(orig.examiner, orig.entry, orig.index, orig.cache, c, orig.medial, orig.projection,
        orig.row);
  }

  private ConditionEvaluator(final ConditionEvaluator orig,
                             final QueryConditionImpl c,
                             final StorageInstanceExaminer.PropertyDiscoveryResult medial) {
    // descending into a list element's own sub-condition: the projection no longer applies (that
    // element isn't a row), so every lookup below here goes straight through the examiner:
    this(orig.examiner, orig.entry, orig.index, orig.cache, c, medial, null, null);
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
    final var val = (medial != null)
        ? examiner.discoverProperty(medial, assertion.prop(), index, cache)
        : (projection != null)
            ? projection.discover(row, projection.column(toPath(assertion.prop())))
            : examiner.discoverProperty(entry, assertion.prop(), index, cache);
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
