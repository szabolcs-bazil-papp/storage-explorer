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

package com.aestallon.storageexplorer.swing.ui.misc;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;

public class StorageInstanceRenderer extends BasicComboBoxRenderer {

  @Override
  public Component getListCellRendererComponent(JList<?> list, Object value,
                                                int index, boolean isSelected,
                                                boolean cellHasFocus) {

    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

    if (value == null) {
      setText("None selected");
      setIcon(null);
      return this;
    }

    final StorageInstance instance = (StorageInstance) value;
    setText(instance.name());

    final ImageIcon icon = IconProvider.getIconForStorageInstance(instance);
    setIcon(icon);

    setHorizontalTextPosition(SwingConstants.RIGHT);
    setIconTextGap(10);
    return this;
  }

}
