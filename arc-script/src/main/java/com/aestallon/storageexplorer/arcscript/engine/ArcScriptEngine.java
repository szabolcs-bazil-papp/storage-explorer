package com.aestallon.storageexplorer.arcscript.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.aestallon.storageexplorer.arcscript.api.ArcScript;
import com.aestallon.storageexplorer.arcscript.internal.ArcScriptImpl;
import com.aestallon.storageexplorer.arcscript.internal.Instruction;
import com.aestallon.storageexplorer.arcscript.internal.index.IndexInstructionImpl;
import com.aestallon.storageexplorer.arcscript.internal.query.Assertion;
import com.aestallon.storageexplorer.arcscript.internal.query.QueryConditionImpl;
import com.aestallon.storageexplorer.arcscript.internal.query.QueryInstructionImpl;
import com.aestallon.storageexplorer.arcscript.internal.update.UpdateInstructionImpl;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.service.IndexingStrategy;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

public class ArcScriptEngine {

  private final ArcScriptEngineConfiguration config;

  public ArcScriptEngine(ArcScriptEngineConfiguration config) {
    this.config = config;
  }

  public ArcScriptResult execute(ArcScript arcScript, StorageInstance storageInstance) {
    if (!(arcScript instanceof ArcScriptImpl as)) {
      return new ArcScriptResult.UnknownError("ArcScript is not an ArcScriptImpl");
    }

    final List<Instruction> instructions = new ArrayList<>(as.instructions);
    if (instructions.isEmpty()) {
      return ArcScriptResult.empty();
    }

    // we must find missing or incomplete indexing instructions and amend them...
    record IndexInsert(int idx, ImplicitIndexInstruction instruction) {}
    final List<IndexInsert> inserts = new ArrayList<>();
    OUTER:
    for (int i = 0; i < instructions.size(); i++) {
      final Instruction instruction = instructions.get(i);
      if (instruction instanceof IndexInstructionImpl index && index._schemas.isEmpty()) {
        return ArcScriptResult.impermissible("Specify at least one schema for indexing: ", index);
      }

      if (instruction instanceof QueryInstructionImpl query) {
        Set<String> schemas = query._schemas;
        Set<String> types = query._types;
        if (schemas.isEmpty()) {
          return ArcScriptResult.impermissible("Specify at least one schema for query: ", query);
        }

        for (int j = 0; j < i; j++) {
          final Instruction instruction2 = instructions.get(j);
          if (instruction2 instanceof IndexInstructionImpl index) {
            if (index._schemas.equals(schemas) && index._types.equals(types)) {
              // everything perfectly matches for this query, nothing to be done!
              continue OUTER;
            }
          }
        }

        // here would come a complex implicit indexing check, where we widen the indexing 
        // instructions not covering the full landscape of later queries, and as a last resort, we 
        // add an extra, implicit index. I don't have the energy to properly implement that, maybe 
        // later...
        final var implicit = new ImplicitIndexInstruction();
        implicit._schemas.addAll(schemas);
        implicit._types.addAll(types);
        inserts.addFirst(new IndexInsert(i, implicit));
      }
    }
    inserts.forEach(it -> instructions.add(it.idx, it.instruction));

    final List<ArcScriptResult.ActionElement> actionElements = new ArrayList<>();
    for (final Instruction instruction : instructions) {
      switch (instruction) {
        case QueryInstructionImpl query -> {
          System.out.println(query);

          final IndexingTarget target = new IndexingTarget(query._schemas, query._types);
          final var condition = query.condition;
          final Set<StorageEntry> res = new HashSet<>();
          long cnt = 0;

          final Set<StorageEntry> entries = storageInstance.index().get(target);
          final var examiner = storageInstance.examiner();
          for (final StorageEntry entry : entries) {
            final var evaluator = new QueryConditionEvaluator(examiner, entry, condition);
            if (evaluator.evaluate()) {
              res.add(entry);
              cnt++;
            }

            if (query._limit > 0 && cnt == query._limit) {
              break;
            }
          }

          actionElements.add(new ArcScriptResult.QueryPerformed(query.toString(), res));
        }
        case IndexInstructionImpl index -> {
          final IndexingTarget target = new IndexingTarget(index._schemas, index._types);
          final int size = storageInstance
              .index()
              .refresh(IndexingStrategy.of(index._strategy), target);
          actionElements.add(new ArcScriptResult.IndexingPerformed(
              index instanceof ImplicitIndexInstruction,
              index._schemas,
              index._types,
              index.toString(),
              size));
        }
        case UpdateInstructionImpl update ->
            throw new IllegalArgumentException("Updates are not yet supported!");
        default -> throw new IllegalStateException("Unexpected value: " + instruction);
      }
    }

    return new ArcScriptResult.Ok(actionElements);
  }

  private static final class QueryConditionEvaluator {

    private final StorageInstanceExaminer examiner;
    private final StorageEntry entry;
    private final QueryConditionImpl.AssertionIterator iterator;

    private QueryConditionEvaluator(final StorageInstanceExaminer examiner,
                                    final StorageEntry entry,
                                    final QueryConditionImpl c) {
      this.examiner = examiner;
      this.entry = entry;
      this.iterator = (c != null)
          ? c.assertionIterator()
          : QueryConditionImpl.AssertionIterator.empty();
    }

    private QueryConditionEvaluator(final QueryConditionEvaluator orig,
                                    final QueryConditionImpl c) {
      this(orig.examiner, orig.entry, c);
    }

    private boolean evaluate() {
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
      return new QueryConditionEvaluator(this, qc).evaluate();
    }

    private boolean resolveValue(final Assertion assertion) {
      final var val = examiner.discoverProperty(entry, assertion.prop());
      return assertion.check(val);
    }
  }


  private static final class ImplicitIndexInstruction extends IndexInstructionImpl {}
}
