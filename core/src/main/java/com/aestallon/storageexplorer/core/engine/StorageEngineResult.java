package com.aestallon.storageexplorer.core.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public sealed interface StorageEngineResult<T> {

  record Ok<T>(T value) implements StorageEngineResult<T> {}


  record Err<T>(StorageEngineErr err) implements StorageEngineResult<T> {

    @SuppressWarnings("unchecked")
    <R> StorageEngineResult<R> coerce() {
      return (StorageEngineResult<R>) this;
    }

  }

  static <T> StorageEngineResult<T> ok(T value) {
    return new Ok<>(value);
  }

  static <T> StorageEngineResult<T> err(StorageEngineErr err) {
    return new Err<>(err);
  }

  sealed interface StorageEngineResultFolder<T, R extends StorageEngineResultFolder<T, R>> {

    void accept(StorageEngineResult<T> result);

    R merge(R that);

    StorageEngineResult<List<T>> get();

    sealed abstract class AbstractStorageEngineResultFolder<T,
        R extends StorageEngineResultFolder<T, R>>
        implements StorageEngineResultFolder<T, R> {
      protected final List<StorageEngineResult.Ok<T>> inner = new ArrayList<>();
      protected StorageEngineResult.Err<T> err = null;
    }


    final class Strict<T> extends AbstractStorageEngineResultFolder<T, Strict<T>> {

      @Override
      public void accept(StorageEngineResult<T> result) {
        if (err != null) {
          return;
        }

        switch (result) {
          case Ok<T> ok -> inner.add(ok);
          case Err<T> e -> this.err = e;
        }
      }

      @Override
      public Strict<T> merge(Strict<T> that) {
        if (err != null) {
          return this;
        }

        if (that.err != null) {
          return that;
        }

        inner.addAll(that.inner);
        return this;
      }

      @Override
      public StorageEngineResult<List<T>> get() {
        return err != null
            ? err.coerce()
            : ok(inner.stream().map(StorageEngineResult.Ok::value).toList());
      }
    }


    final class Lenient<T> extends AbstractStorageEngineResultFolder<T, Lenient<T>> {

      @Override
      public void accept(StorageEngineResult<T> result) {
        switch (result) {
          case Ok<T> ok -> inner.add(ok);
          case Err<T> e -> {
            if (this.err == null) {
              this.err = e;
            }
          }
        }
      }

      @Override
      public Lenient<T> merge(Lenient<T> that) {
        inner.addAll(that.inner);
        if (err == null) {
          err = that.err;
        }
        return this;
      }

      @Override
      public StorageEngineResult<List<T>> get() {
        return inner.isEmpty()
            ? err.coerce()
            : ok(inner.stream().map(StorageEngineResult.Ok::value).toList());
      }

    }

  }

  static <T> Collector<StorageEngineResult<T>, StorageEngineResultFolder.Strict<T>,
      StorageEngineResult<List<T>>> allOf() {
    return Collector.of(
        StorageEngineResultFolder.Strict::new,
        StorageEngineResultFolder::accept,
        StorageEngineResultFolder::merge,
        StorageEngineResultFolder::get,
        Collector.Characteristics.UNORDERED);
  }

  static <T> Collector<StorageEngineResult<T>, StorageEngineResultFolder.Lenient<T>,
      StorageEngineResult<List<T>>> anyOf() {
    return Collector.of(
        StorageEngineResultFolder.Lenient::new,
        StorageEngineResultFolder::accept,
        StorageEngineResultFolder::merge,
        StorageEngineResultFolder::get,
        Collector.Characteristics.UNORDERED);
  }

  default T orElseThrow() {
    return switch (this) {
      case Ok<T>(var value) -> value;
      case Err<T>(var err) -> throw new StorageEngineException(err);
    };
  }

  default T orElse(final T t) {
    return switch (this) {
      case Ok<T>(var value) -> value;
      case Err<T> err -> t;
    };
  }

  default T orElseGet(final Supplier<? extends T> fn) {
    return switch (this) {
      case Ok<T>(var value) -> value;
      case Err<T> err -> fn.get();
    };
  }

  default <R> StorageEngineResult<R> map(final Function<? super T, ? extends R> fn) {
    return switch (this) {
      case Ok<T>(var value) -> ok(fn.apply(value));
      case Err<T> err -> err.coerce();
    };
  }

  default <R> StorageEngineResult<R> flatMap(
      final Function<? super T, ? extends StorageEngineResult<R>> fn) {
    return switch (this) {
      case Ok<T>(var value) -> fn.apply(value);
      case Err<T> err -> err.coerce();
    };
  }

  default StorageEngineResult<T> errMap(
      final Function<? super StorageEngineErr, ? extends StorageEngineErr> fn) {
    return switch (this) {
      case Ok<T> ok -> ok;
      case Err<T>(var err) -> err(fn.apply(err));
    };
  }

  default Optional<T> asOptional() {
    return switch (this) {
      case Ok<T>(var value) -> Optional.of(value);
      case Err<T> err -> Optional.empty();
    };
  }



}
