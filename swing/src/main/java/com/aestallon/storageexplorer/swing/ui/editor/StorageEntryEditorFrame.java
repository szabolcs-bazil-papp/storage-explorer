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

package com.aestallon.storageexplorer.swing.ui.editor;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.Serial;
import javax.swing.*;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import com.aestallon.storageexplorer.swing.ui.misc.LafService;

public class StorageEntryEditorFrame extends JFrame {

  @Serial
  private static final long serialVersionUID = 1L;

  private final StorageEntryEditorController controller;
  final ContentPane contentPane;
  JLabel toolbarLabel;
  JButton proceed;
  JButton back;

  StorageEntryEditorFrame(final StorageEntryEditorController controller) {
    super("Storage Entry Editor");
    this.controller = controller;
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    setSize(1_000, 700);
    //setExtendedState(Frame.MAXIMIZED_BOTH);
    setLocationRelativeTo(null);

    contentPane = new ContentPane(
        initHeader(), 
        new StorageEntryEditorIntentView(controller),
        initToolbar());
    setContentPane(contentPane);
  }
  
  private JLabel initHeader() {
    final var header = new JLabel("Declare Your Intent");
    header.setFont(LafService.wrap(UIManager.getFont("h1.font")));
    return header;
  }
  
  private JToolBar initToolbar() {
    final var toolbar = new JToolBar();
    toolbar.setFloatable(false);
    toolbar.setOrientation(SwingConstants.HORIZONTAL);
    toolbarLabel = new JLabel(" ");
    toolbar.add(toolbarLabel);
    toolbar.add(Box.createHorizontalGlue());
    toolbar.add(new AbstractAction("Cancel", IconProvider.CLOSE) {
      @Override
      public void actionPerformed(ActionEvent e) {
        StorageEntryEditorFrame.this.dispose();
      }
    });
    back = toolbar.add(new AbstractAction("Back") {
      @Override
      public void actionPerformed(ActionEvent e) {
        controller.back(StorageEntryEditorFrame.this);
      }
    });
    back.setEnabled(false);
    proceed = toolbar.add(new AbstractAction("Proceed", IconProvider.PLAY) {
      @Override
      public void actionPerformed(ActionEvent e) {
        controller.proceed(StorageEntryEditorFrame.this);
      }
    });
    
    return toolbar;
  }

  static final class ContentPane extends JPanel {

    @Serial
    private static final long serialVersionUID = 1L;

    final JLabel header;
    JPanel content;
    final JToolBar toolbar;

    ContentPane(JLabel header, JPanel content, JToolBar toolbar) {
      super(new BorderLayout());
      this.header = header;
      this.content = content;
      this.toolbar = toolbar;

      add(header, BorderLayout.NORTH);
      add(content, BorderLayout.CENTER);
      add(toolbar, BorderLayout.SOUTH);
    }

    void setContent(JPanel content) {
      remove(this.content);
      this.content = content;
      add(content, BorderLayout.CENTER);
      revalidate();
      repaint();
    }
  }
}
