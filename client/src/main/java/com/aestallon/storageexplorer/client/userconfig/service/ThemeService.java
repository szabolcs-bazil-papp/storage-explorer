package com.aestallon.storageexplorer.client.userconfig.service;

import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.client.userconfig.event.LafChanged;
import com.aestallon.storageexplorer.client.userconfig.model.Theme;
import com.fasterxml.jackson.core.type.TypeReference;

@Service
public class ThemeService {

  private static final Logger log = LoggerFactory.getLogger(ThemeService.class);

  private static final String THEME = "theme";

  private final ApplicationEventPublisher eventPublisher;
  private final UserConfigPersistenceService persistenceService;
  private final AtomicReference<Theme> theme;

  public ThemeService(ApplicationEventPublisher eventPublisher,
                      UserConfigPersistenceService persistenceService) {
    this.eventPublisher = eventPublisher;
    this.persistenceService = persistenceService;
    this.theme = new AtomicReference<>(persistenceService.readSettingsAt(
        THEME,
        new TypeReference<>() {},
        () -> Theme.LIGHT));
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


}
