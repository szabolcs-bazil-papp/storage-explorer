/*
 * Copyright (C) 2026 Szabolcs Bazil Papp
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

import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

/**
 * A pre-materialised source of root-level property values, consulted by {@link ConditionEvaluator}
 * in place of a fresh {@code StorageInstanceExaminer.discoverProperty} call.
 *
 * <p>
 * Used by {@link ArrowQueryEngine} to feed {@code where} predicate evaluation from its Arrow-backed
 * projection store instead of re-discovering each property from the entry: the store already holds
 * a value for every property referenced by a top-level (single-valued) assertion, so this simply
 * hands it back. Returning {@code null} tells the evaluator this property was not planned for and it
 * should fall back to {@code examiner.discoverProperty} - this keeps the integration purely additive
 * and safe by construction.
 *
 * <p>
 * Only ever consulted at the assertion tree's root ({@code medial == null}): once evaluation
 * descends into a list-quantifier's {@code listElementCondition}, properties are resolved relative
 * to a nested list element, which has no flat, row-level columnar representation, so the examiner
 * path is used unconditionally there.
 */
@FunctionalInterface
public interface PropertySource {

  StorageInstanceExaminer.PropertyDiscoveryResult get(String prop);

}
