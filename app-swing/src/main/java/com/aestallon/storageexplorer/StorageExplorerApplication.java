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

package com.aestallon.storageexplorer;

import javax.swing.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import com.aestallon.storageexplorer.core.service.StorageInstanceProvider;
import com.aestallon.storageexplorer.swing.ui.AppFrame;
import com.aestallon.storageexplorer.swing.ui.event.LafChanged;
import com.aestallon.storageexplorer.swing.ui.misc.WelcomePopup;
import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;

@SpringBootApplication(
    exclude = DataSourceAutoConfiguration.class,
    scanBasePackages = {
        "com.aestallon.storageexplorer.export",
        "com.aestallon.storageexplorer.core",
        "com.aestallon.storageexplorer.swing"
    })
public class StorageExplorerApplication {

  private final AppFrame frame;

  public StorageExplorerApplication(AppFrame frame) {
    this.frame = frame;
  }

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
  CommandLineRunner frameLauncher(AppFrame appFrame,
                                  StorageInstanceProvider storageInstanceProvider) {
    return args -> {
      appFrame.launch();
      storageInstanceProvider.fetchAllKnown();

      if (storageInstanceProvider.provide().findAny().isEmpty()) {
        WelcomePopup.show(appFrame);
      }
    };
  }

  @EventListener
  public void onLafChanged(final LafChanged event) {
    SwingUtilities.invokeLater(() -> {
      switch (event.laf()) {
        case LIGHT -> FlatIntelliJLaf.setup();
        case DARK -> FlatDarculaLaf.setup();
      }
      SwingUtilities.updateComponentTreeUI(frame);
    });
  }

}
