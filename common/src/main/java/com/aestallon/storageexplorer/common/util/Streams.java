package com.aestallon.storageexplorer.common.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class Streams {

  private Streams() {}

  public static <E> Stream<E> enumerationToStream(Enumeration<E> e) {
    final Iterable<E> iterable = e::asIterator;
    return StreamSupport.stream(iterable.spliterator(), false);
  }
  
  public static <E> Collector<E, ?, Stream<E>> reverse() {
    return Collectors.collectingAndThen(Collectors.toCollection(ArrayList::new), es -> {
      Collections.reverse(es);
      return es.stream();
    });
  }

}
