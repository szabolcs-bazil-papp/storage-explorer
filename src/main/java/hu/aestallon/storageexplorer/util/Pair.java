/*
 * Copyright (C) 2024 it4all Hungary Kft.
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

package hu.aestallon.storageexplorer.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Pair<A, B> {

  public static <A, B> Pair<A, B> of(A a, B b) {
    return new Pair<>(a, b);
  }

  public static <A, B> Pair<A, B> of(Map.Entry<A, B> e) {
    return of(e.getKey(), e.getValue());
  }

  public static <A, B, R> Function<Pair<A, B>, Pair<R, B>> onA(
      Function<? super A, ? extends R> mapper) {
    return it -> Pair.of(mapper.apply(it.a), it.b);
  }

  public static <A, B, R> Function<Pair<A, B>, Pair<A, R>> onB(
      Function<B, R> mapper) {
    return it -> Pair.of(it.a, mapper.apply(it.b));
  }

  public static <A, B> Collector<Pair<A, B>, ?, Map<A, B>> toMap() {
    return Collectors.toMap(Pair::a, Pair::b, (x, y) -> y, HashMap::new);
  }

  public static <A, B> Function<Pair<Optional<A>, B>, Stream<Pair<A, B>>> streamOnA() {
    return pair -> pair.a.stream().flatMap(it -> Stream.of(Pair.of(it, pair.b)));
  }

  public static <A, B> Function<Pair<A, Optional<B>>, Stream<Pair<A, B>>> streamOnB() {
    return pair -> pair.b.stream().flatMap(it -> Stream.of(Pair.of(pair.a, it)));
  }

  public static <A, B> Consumer<? super Pair<? extends A, ? extends B>> putIntoMap(
      Map<? super A, ? super B> m) {
    return pair -> m.put(pair.a(), pair.b());
  }

  public static <A, B> Function<B, Pair<A, B>> withA(Function<B, A> mapper) {
    return b -> Pair.of(mapper.apply(b), b);
  }

  public static <A, B> Function<A, Pair<A, B>> withB(Function<A, B> mapper) {
    return a -> Pair.of(a, mapper.apply(a));
  }

  public static <A, B, R> Function<? super Pair<A, B>, R> map(
      BiFunction<A, B, R> mapper) {
    return pair -> mapper.apply(pair.a, pair.b);
  }

  private final A a;
  private final B b;

  private Pair(A a, B b) {
    this.a = a;
    this.b = b;
  }

  public A a() {
    return a;
  }

  public B b() {
    return b;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    Pair<?, ?> pair = (Pair<?, ?>) o;
    return Objects.equals(a, pair.a) && Objects.equals(b, pair.b);
  }

  @Override
  public int hashCode() {
    return Objects.hash(a, b);
  }

  @Override
  public String toString() {
    return "Pair { " +
        "a: " + a +
        ", b: " + b +
        " }";
  }

}
