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

package hu.aestallon.storageexplorer.config;

import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.core.object.ObjectDefinitionApi;
import org.smartbit4all.domain.data.storage.ObjectStorage;
import org.smartbit4all.storage.fs.StorageFS;
import org.springframework.beans.factory.annotation.Value;

public class StorageExplorerAppConfig {

  private static final Logger log = LoggerFactory.getLogger(StorageExplorerAppConfig.class);

  ObjectStorage objectStorage(@Value("${fs.base.directory:./fs}") String fsBaseDirectory,
      ObjectDefinitionApi objectDefinitionApi) {
    return new StorageFS(new File(fsBaseDirectory), objectDefinitionApi);
  }

}
