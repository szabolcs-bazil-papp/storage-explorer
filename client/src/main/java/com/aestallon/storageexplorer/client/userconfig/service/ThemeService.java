package com.aestallon.storageexplorer.client.userconfig.service;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.client.userconfig.event.LafChanged;
import com.aestallon.storageexplorer.client.userconfig.model.ColourDef;
import com.aestallon.storageexplorer.client.userconfig.model.ColourSettings;
import com.aestallon.storageexplorer.client.userconfig.model.Theme;
import com.fasterxml.jackson.core.type.TypeReference;

@Service
public class ThemeService {

  private static final Logger log = LoggerFactory.getLogger(ThemeService.class);

  private static final String THEME = "theme";
  private static final String COLOUR_SETTINGS = "colour.settings";


  // Colour constants
  public static final String C_ERD_BOX_BG = "c_erd_box_bg";
  public static final String C_ERD_BOX_HEADER_BG = "c_erd_box_hdr_bg";
  public static final String C_ERD_BOX_HEADER_BD = "c_erd_box_hdr_bd";
  public static final String C_ERD_BOX_HEADER_TXT = "c_erd_box_hdr_txt";
  public static final String C_ERD_BOX_PROP_KEY = "c_erd_box_prop_key";
  public static final String C_ERD_BOX_PROP_KEY_MISMATCH = "c_erd_box_prop_key_mismatch";
  public static final String C_ERD_BOX_PROP_KEY_WARN = "c_erd_box_prop_key_warn";
  public static final String C_ERD_BOX_PROP_VALUE = "c_erd_box_prop_value";


  private static final ColourSettings COLOUR_SETTINGS_DEFAULT = new ColourSettings()
      .with(C_ERD_BOX_BG, new ColourDef("Entity background", 0xfff0f0f0, 0xff282828))
      .with(C_ERD_BOX_HEADER_BG, new ColourDef("Entity header background", 0xff42BCA5, 0xff266757))
      .with(C_ERD_BOX_HEADER_BD, new ColourDef("Entity border", 0xff323232, 0xffc8c8c8))
      .with(C_ERD_BOX_HEADER_TXT, new ColourDef("Entity header text", 0xffffffff, 0xffffffff))
      .with(C_ERD_BOX_PROP_KEY, new ColourDef("Property name", 0xff000000, 0xffffffff))
      .with(C_ERD_BOX_PROP_KEY_MISMATCH,
          new ColourDef("Property name (mismatch with nominal type)", 0xffA81010, 0xffA81010))
      .with(C_ERD_BOX_PROP_KEY_WARN,
          new ColourDef("Property name (no nominal type information available)", 0xffD1B52D,
              0xffD1B52D))
      .with(C_ERD_BOX_PROP_VALUE, new ColourDef("Property value", 0xFF780707, 0xffDFC358));



  private final ApplicationEventPublisher eventPublisher;
  private final UserConfigPersistenceService persistenceService;
  private final AtomicReference<Theme> theme;
  private final AtomicReference<ColourSettings> colourSettings;

  public ThemeService(ApplicationEventPublisher eventPublisher,
                      UserConfigPersistenceService persistenceService) {
    this.eventPublisher = eventPublisher;
    this.persistenceService = persistenceService;
    this.theme = new AtomicReference<>(persistenceService.readSettingsAt(
        THEME,
        new TypeReference<>() {},
        () -> Theme.LIGHT));
    this.colourSettings = new AtomicReference<>(persistenceService.readSettingsAt(
        COLOUR_SETTINGS,
        new TypeReference<>() {},
        () -> COLOUR_SETTINGS_DEFAULT));
  }

  public void applyTheme() {
    final var currTheme = theme.get();
    final var laf = switch (currTheme) {
      case LIGHT -> LafChanged.Laf.LIGHT;
      case DARK -> LafChanged.Laf.DARK;
    };
    eventPublisher.publishEvent(new LafChanged(laf));
  }

  public Theme currentTheme() {
    return theme.get();
  }

  @EventListener(LafChanged.class)
  public void onLafChanged(final LafChanged event) {
    final var lafToSet = event.laf();
    final var themeToSave = switch (lafToSet) {
      case LIGHT -> Theme.LIGHT;
      case DARK -> Theme.DARK;
    };
    theme.set(themeToSave);
    persistenceService.writeSettingsTo(THEME, themeToSave);
  }

  public int colour(final String key) {
    return colour(key, currentTheme());
  }

  public int colour(final String key, Theme theme) {
    ColourDef def = colourSettings.get().getDefs().get(key);
    if (def == null) {
      final ColourDef hcDef = COLOUR_SETTINGS_DEFAULT.getDefs().get(key);
      final var newDef = new ColourDef(hcDef.getName(), hcDef.getLight(), hcDef.getDark());
      colourSettings.updateAndGet(it -> it.with(key, newDef));
      def = newDef;
    }

    return switch (theme) {
      case LIGHT -> def.getLight();
      case DARK -> def.getDark();
    };
  }

  public void colour(final String key, final Theme theme, final int colour) {
    final var settings = colourSettings.updateAndGet(it -> {
      ColourDef def = it.getDefs().get(key);
      if (def == null) {
        final var hcDef = COLOUR_SETTINGS_DEFAULT.getDefs().get(key);
        def = new ColourDef(hcDef.getName(), hcDef.getLight(), hcDef.getDark());
        it.getDefs().put(key, def);
      }

      switch (theme) {
        case LIGHT -> def.setLight(colour);
        case DARK -> def.setDark(colour);
      }
      return it;
    });
    persistenceService.writeSettingsTo(COLOUR_SETTINGS, settings);
  }

  public ColourSettings colourSettings() {
    final var settings = colourSettings.get();

    final var copy = new ColourSettings();
    copy.setDefs(new HashMap<>(settings.getDefs()));
    return copy;
  }


}
