package hu.aestallon.storageexplorer.swing.ui.inspector;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import hu.aestallon.storageexplorer.storage.model.entry.SequenceEntry;

public class SequenceEntryInspectorView extends JPanel implements InspectorView<SequenceEntry> {

  private final SequenceEntry sequenceEntry;
  
  public SequenceEntryInspectorView(SequenceEntry sequenceEntry) {
    this.sequenceEntry = sequenceEntry;
    
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(new EmptyBorder(5, 5, 5, 5));
    
    add(new JLabel("Current value:"));
    add(Box.createHorizontalBox());
    add(new JLabel(String.valueOf(sequenceEntry.current())));
  }
  
  @Override
  public SequenceEntry storageEntry() {
    return sequenceEntry;
  }
}
