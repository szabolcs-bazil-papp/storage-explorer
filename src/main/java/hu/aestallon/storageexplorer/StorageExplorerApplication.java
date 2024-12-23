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

package hu.aestallon.storageexplorer;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import com.formdev.flatlaf.FlatIntelliJLaf;
import hu.aestallon.storageexplorer.domain.storage.service.StorageIndexProvider;
import hu.aestallon.storageexplorer.ui.AppFrame;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class StorageExplorerApplication {

  public static void main(String[] args) {
    System.setProperty("sun.java2d.uiScale", "100%");
    System.setProperty("org.graphstream.ui", "swing");
    FlatIntelliJLaf.setup();

    new SpringApplicationBuilder(StorageExplorerApplication.class)
        .web(WebApplicationType.NONE)
        .headless(false)
        .build()
        .run(args);
  }

  @Bean
  CommandLineRunner frameLauncher(AppFrame appFrame, StorageIndexProvider storageIndexProvider) {
    return args -> {
      appFrame.launch();
      storageIndexProvider.fetchAllKnown();
    };
  }

}
