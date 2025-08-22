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

package com.aestallon.storageexplorer.cli;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.shell.command.annotation.CommandScan;
import org.springframework.shell.jline.PromptProvider;
import com.aestallon.storageexplorer.cli.service.StorageInstanceContext;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;

@SpringBootApplication(
    exclude = DataSourceAutoConfiguration.class,
    scanBasePackages = {
        "com.aestallon.storageexplorer.client",
        "com.aestallon.storageexplorer.cli"
    })
@CommandScan
public class StorageExplorerApplication {

  public static void main(String[] args) {
    SpringApplication.run(StorageExplorerApplication.class, args);
  }
  
  @Bean
  PromptProvider promptProvider(StorageInstanceContext storageInstanceContext) {
    return () -> new AttributedString(
        "[%s]:>".formatted(storageInstanceContext.current().map(StorageInstance::name).orElse("UNBOUND")),
        AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW)
    );
  }

}
