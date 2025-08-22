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

package com.aestallon.storageexplorer.cli.service;

import java.util.Optional;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.client.storage.StorageInstanceProvider;
import com.aestallon.storageexplorer.client.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;

@Component
public class StorageInstanceContext {

  private StorageInstance curr;
  private final UserConfigService userConfigService;
  private final StorageInstanceProvider storageInstanceProvider;

  public StorageInstanceContext(UserConfigService userConfigService,
                                StorageInstanceProvider storageInstanceProvider) {
    this.userConfigService = userConfigService;
    this.storageInstanceProvider = storageInstanceProvider;
    storageInstanceProvider.fetchAllKnown();
    curr = userConfigService.getMostRecentStorageInstanceLoad()
        .flatMap(it -> storageInstanceProvider.provide()
            .filter(inst -> inst.id().equals(it))
            .findFirst())
        .orElse(null);
  }

  public Optional<StorageInstance> current() {
    return Optional.ofNullable(curr);
  }

  public void current(final StorageInstance storageInstance) {
    this.curr = storageInstance;
  }

}
