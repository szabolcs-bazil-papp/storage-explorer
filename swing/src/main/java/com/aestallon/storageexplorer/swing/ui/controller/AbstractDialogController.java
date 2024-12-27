package com.aestallon.storageexplorer.swing.ui.controller;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class AbstractDialogController<T> {

  @FunctionalInterface
  public interface Finisher<T> extends BiConsumer<T, T> {}


  protected final T initialModel;
  protected final Finisher<T> finisher;
  protected final Consumer<T> postProcessor;

  protected AbstractDialogController(final T initialModel, final Finisher<T> finisher) {
    this(initialModel, finisher, null);
  }
  
  protected AbstractDialogController(final T initialModel, 
                                     final Finisher<T> finisher,
                                     final Consumer<T> postProcessor) {
    this.initialModel = initialModel;
    this.finisher = finisher;
    this.postProcessor = postProcessor;
  }
  
  public T initialModel() {
    return initialModel;
  }

  public void finish(final T finalModel) {
    finisher.accept(initialModel, finalModel);
    if (postProcessor != null) {
      postProcessor.accept(finalModel);
    }
  }

}
