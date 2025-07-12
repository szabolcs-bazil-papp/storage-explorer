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

import javax.swing.*;

public class StorageEntryEditorIntentView extends JPanel {

  private final StorageEntryEditorController controller;

  /*
   * @formatter:off
   * +-------------------------------------------------------------------------+
   * | Text about the entry you are about to edit, and what options you        |
   * | have.                                                                   |
   * +-------------------------------------------------------------------------+
   * | [ ] Yes, I want to modify this single version entry                     | <- if StColl or SV
   * +-------------------------------------------------------------------------+
   * | ( ) I want to OVERRIDE the contents of version ${v} of this entry       |
   * | ( ) I want to SAVE A NEW VERSION of this entry using version ${v} as    | <- otherwise
   * |       the basis of my modifications                                     |
   * +-------------------------------------------------------------------------+
   * | [ ] I declare I have the proper authorisation                           |
   * | [ ] I declare I understand the implications of the software licence     |
   * | [ ] I declare I am aware this is an experimental feature                |
   * +-------------------------------------------------------------------------+
   * @formatter:on
   */

  StorageEntryEditorIntentView(StorageEntryEditorController controller) {
    this.controller = controller;

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

  }

}
