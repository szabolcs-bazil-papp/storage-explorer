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

package com.aestallon.storageexplorer.swing.ui.inspector;

import java.awt.*;
import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.swing.ui.misc.LafService;

public class ObjectEntryDiffView
    extends ObjectEntryInspectorView
    implements InspectorView<ObjectEntry> {

  private static final Logger log = LoggerFactory.getLogger(ObjectEntryDiffView.class);

  private final ObjectEntryLoadResult.SingleVersion base;
  private final long baseVersionNr;
  
  ObjectEntryDiffView(final ObjectEntry objectEntry,
                      final StorageEntryInspectorViewFactory factory,
                      final ObjectEntryLoadResult.SingleVersion base,
                      final long baseVersionNr) {
    super(objectEntry, factory);
    this.base = base;
    this.baseVersionNr = baseVersionNr;

    // This is so sad Alexa play Despacito...
    // TODO: I must stop GUI setup in container ctors!
    ((VersionPane) getComponentAt(getTabCount() - 1)).initialise();
  }

  @Override
  protected VersionPane versionPane(ObjectEntryLoadResult.SingleVersion version, long versionNr,
                                    ObjectEntryLoadResult.MultiVersion multiVersion) {
    return new DiffVersionPane(version, versionNr, multiVersion);
  }

  private final class DiffVersionPane extends VersionPane {

    DiffVersionPane(ObjectEntryLoadResult.SingleVersion version,
                    long versionNr,
                    final ObjectEntryLoadResult.MultiVersion multiVersion) {
      super(version, versionNr, multiVersion);
    }

    @Override
    protected void initialise() {
      if (base == null) {
        return;
      }
      
      super.initialise();
    }

    @Override
    protected Box createTextareaContainer(JToolBar toolbar) {
      final Box box = new Box(BoxLayout.Y_AXIS);
      
      final var titlePanel = new JPanel();
      titlePanel.setLayout(new GridLayout(1, 2));
      final var label1 = new JLabel("Comparing v" + versionNr +"...");
      label1.setFont(LafService.font(LafService.FontToken.H2_SEMIBOLD));
      final var label2 = new JLabel("to v" + baseVersionNr);
      label2.setFont(LafService.font(LafService.FontToken.H2_SEMIBOLD));
      
      titlePanel.add(label1);
      titlePanel.add(label2);
      box.add(titlePanel);
      
      box.add(new StorageEntryVersionDiffView(
          factory.textareaFactory(),
          objectEntry,
          version,
          base));
      return box;
    }
  }
}
