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
import javax.swing.border.EmptyBorder;
import com.aestallon.storageexplorer.core.model.entry.ListEntry;
import com.aestallon.storageexplorer.core.model.entry.MapEntry;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.SequenceEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.service.StorageEntryModificationService;

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

  final JLabel description;
  final JCheckBox svModificationConsent =
      new JCheckBox("Yes, I want to modify this single version entry");
  final ButtonGroup mvModificationOptions = new ButtonGroup();
  final JRadioButton optionOverride =
      new JRadioButton("I want to OVERRIDE the contents of version ${v} of this entry");
  final JRadioButton optionSaveNewVersion = new JRadioButton(
      "I want to SAVE A NEW VERSION of this entry using version ${v} as the basis of my modifications");
  final JCheckBox authorisationConsent = new JCheckBox("""
      <HTML>I declare I have the proper authorisation from the administrators<BR />
      of the relevant smartbit4all storage to enact this modification.</HTML>""");
  final JCheckBox licenceConsent =
      new JCheckBox("""
          <HTML><P>I declare I understand the implications of the software licence</P>
          <P>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
          even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
          Lesser General Public License for more details.</P></HTML>""");
  final JCheckBox experimentalConsent =
      new JCheckBox("""
          <HTML>I declare I am aware this is an experimental feature,<BR />
          with both known and hidden errors in its behaviour</HTML>""");

  StorageEntryEditorIntentView(StorageEntryEditorController controller) {
    this.controller = controller;

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    description = new JLabel(description());
    add(Box.createVerticalGlue());
    add(description);
    description.setBorder(new EmptyBorder(5, 200, 5, 200));


    if (controller.singleVersionMode()) {
      add(svModificationConsent);
      svModificationConsent.setBorder(new EmptyBorder(5, 200, 5, 200));
      if (controller.modificationMode() != null) {
        svModificationConsent.setSelected(true);
      }
    } else {
      mvModificationOptions.add(optionOverride);
      mvModificationOptions.add(optionSaveNewVersion);
      if (controller.modificationMode() instanceof StorageEntryModificationService.ModificationMode.Overwrite) {
        optionOverride.setSelected(true);
      } else if (controller.modificationMode() instanceof StorageEntryModificationService.ModificationMode.SaveNewVersion) {
        optionSaveNewVersion.setSelected(true);
      }

      add(optionOverride);
      optionOverride.setBorder(new EmptyBorder(5, 200, 5, 200));
      add(optionSaveNewVersion);
      optionSaveNewVersion.setBorder(new EmptyBorder(5, 200, 5, 200));
      optionOverride.setText(
          "<HTML><P>I want to <B>OVERRIDE</B> the contents of version <B>v"
          + controller.srcVersionNr() + "</B> of this entry</P></HTML>");
      optionSaveNewVersion.setText(
          "<HTML><P>I want to <B>SAVE A NEW VERSION</B> of this entry, using version <B>v"
          + controller.srcVersionNr()
          + " </B> as the basis of my modifications</P></HTML>");
    }

    add(Box.createVerticalStrut(20));

    add(authorisationConsent);
    authorisationConsent.setBorder(new EmptyBorder(5, 200, 5, 200));
    add(licenceConsent);
    licenceConsent.setBorder(new EmptyBorder(5, 200, 5, 200));
    add(experimentalConsent);
    experimentalConsent.setBorder(new EmptyBorder(5, 200, 5, 200));
    add(Box.createVerticalGlue());
  }

  private String description() {
    final StringBuilder sb = new StringBuilder();
    if (controller.singleVersionMode()) {
      sb
          .append("<HTML><P>You have selected to edit a(n) ")
          .append(switch (controller.storageEntry()) {
            case ObjectEntry o -> o.typeName() + " object";
            case ListEntry l -> "list";
            case MapEntry m -> "map";
            case SequenceEntry s -> "sequence";
          })
          .append(", which is stored with a <B>single version policy</B> (uri: ")
          .append(controller.storageEntry().uri())
          .append(").</P>")
          .append("""
              <P>Your modification shall essentially overwrite the persisted data, 
              and the current state shall be lost. Your modification shall result 
              in the update of the <CODE>last modified timestamp</CODE> stored as 
              the entry's metadata.</P>""");
    } else {
      sb
          .append("<HTML><P>You have selected to edit version <B>v")
          .append(controller.srcVersionNr())
          .append("</B> of a(n) ")
          .append(StorageEntry.typeNameOf(controller.storageEntry()))
          .append(" object, which is stored with a <B>multi-version policy</B> (uri: ")
          .append(controller.storageEntry().uri())
          .append(").</P>")
          .append("<P>You have the following two options to modify the entry's content:</P>")
          .append("<P></P>")
          .append("<P>You can <B>OVERRIDE</B> the contents of the entry's version <B>v")
          .append(controller.srcVersionNr())
          .append("</B> with your edit. ")
          .append("This will result in the loss of the previous contents of the exact version, ")
          .append("replaced by your modifications, but no version metadata shall be updated.</P>")
          .append("<P>You can <B>SAVE A NEW VERSION</B> of the entry, which will result in the ")
          .append("creation of an entirely new object version.")
          .append("</P>");
    }

    return sb
        .append("""
            <P></P><P>To enable the reversion of your modifications, a copy of entry's 
            initial state shall be written to a temporary file, should your
            modification succeed.</P>""")
        .append("""
            <P></P><P>This editor will guide you through a modification and review phase.
            You can always reference the bottom left hand side of this frame 
            for further information about the exact operation you are performing
            in any given phase.</P></HTML>""")
        .toString();
  }

}
