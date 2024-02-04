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

package hu.aestallon.storageexplorer.ui.dialog.entryinspector;

import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import hu.aestallon.storageexplorer.ui.inspector.ObjectEntryInspectorView;

public class ObjectEntryInspectorDialog extends JFrame {

  private static final Logger log = LoggerFactory.getLogger(ObjectEntryInspectorDialog.class);

  public ObjectEntryInspectorDialog(ObjectEntryInspectorView inspectorView) {
    super(inspectorView.storageEntry().toString());

    add(inspectorView);
    pack();
  }

}
