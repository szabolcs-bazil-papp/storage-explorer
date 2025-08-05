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

package com.aestallon.storageexplorer.swing.ui.dialog.newscript;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import com.aestallon.storageexplorer.client.storage.StorageInstanceProvider;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.swing.ui.arcscript.ArcScriptController;
import com.aestallon.storageexplorer.swing.ui.controller.AbstractDialogController;

public class NewScriptController extends AbstractDialogController<NewScriptDialogModel> {

  public static NewScriptController create(final StorageInstance storageInstance,
                                           final StorageInstanceProvider storageInstanceProvider,
                                           final ArcScriptController arcScriptController) {
    return new NewScriptController(
        new NewScriptDialogModel(storageInstance, storageInstanceProvider.provide().toList()),
        (before, after) -> CompletableFuture
            .runAsync(() -> arcScriptController.newScript(after.selection())));
  }

  protected NewScriptController(NewScriptDialogModel initialModel,
                                Finisher<NewScriptDialogModel> finisher) {
    super(initialModel, finisher);
  }

  protected NewScriptController(NewScriptDialogModel initialModel,
                                Finisher<NewScriptDialogModel> finisher,
                                Consumer<NewScriptDialogModel> postProcessor) {
    super(initialModel, finisher, postProcessor);
  }


}
