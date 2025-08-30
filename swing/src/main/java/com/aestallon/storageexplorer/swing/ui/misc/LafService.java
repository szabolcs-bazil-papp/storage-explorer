package com.aestallon.storageexplorer.swing.ui.misc;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.UIResource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.swing.ui.event.LafChanged;
import jakarta.annotation.Nullable;

@Service
public class LafService {

  public enum FontToken {
    H00("h00.font"),
    H0("h0.font"),
    H1_SEMIBOLD("h1.font"),
    H1_REGULAR("h1.regular.font"),
    H2_SEMIBOLD("h2.font"),
    H2_REGULAR("h2.regular.font"),
    H3_SEMIBOLD("h3.font"),
    H3_REGULAR("h3.regular.font"),
    H4("h4.font"),
    LARGE("large.font"),
    DEFAULT("defaultFont"),
    MEDIUM("medium.font"),
    SMALL("small.font"),
    MINI("mini.font"),
    MONOSPACED("monospaced.font"),
    LIGHT("light.font"),
    SEMIBOLD("semibold.font");

    private final String token;

    FontToken(String token) { this.token = token; }

  }
  
  public static Font font(FontToken token) {
    return wrap(UIManager.getFont(token.token));
  }

  private static Font wrap(Font font) {
    return (font instanceof UIResource) ? font.deriveFont(font.getStyle()) : font;
  }

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
