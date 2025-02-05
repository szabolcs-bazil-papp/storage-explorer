package com.aestallon.storageexplorer.swing.ui.misc;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.swing.ui.event.LafChanged;
import jakarta.annotation.Nullable;

@Service
public class LafService {

  private LafChanged.Laf laf;
  private final ApplicationEventPublisher eventPublisher;

  public LafService(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
    laf = LafChanged.Laf.LIGHT;
  }

  public void changeLaf(@Nullable LafChanged.Laf laf) {
    final LafChanged.Laf lafToSet;
    if (laf == null) {
      lafToSet = (this.laf == LafChanged.Laf.LIGHT) ? LafChanged.Laf.DARK : LafChanged.Laf.LIGHT;
    } else {
      lafToSet = laf;
    }

    if (this.laf == lafToSet) {
      return;
    }

    this.laf = lafToSet;
    eventPublisher.publishEvent(new LafChanged(lafToSet));
  }

  public LafChanged.Laf getLaf() {
    return laf;
  }

}
