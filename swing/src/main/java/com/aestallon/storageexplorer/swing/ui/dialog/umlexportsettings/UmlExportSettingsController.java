package com.aestallon.storageexplorer.swing.ui.dialog.umlexportsettings;

import java.util.function.Consumer;
import com.aestallon.storageexplorer.client.userconfig.model.UmlExportSettings;
import com.aestallon.storageexplorer.client.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.swing.ui.controller.AbstractDialogController;

public class UmlExportSettingsController extends AbstractDialogController<UmlExportSettings> {

  public static UmlExportSettingsController newInstance(UserConfigService userConfigService) {
    return new UmlExportSettingsController(
        userConfigService.umlExportSettings(),
        (before, after) -> userConfigService.updateUmlExportSettings(after));
  }

  public static UmlExportSettingsController dummy() {
    return new UmlExportSettingsController(
        new UmlExportSettings(),
        (before, after) -> System.out.println(after));
  }

  protected UmlExportSettingsController(UmlExportSettings initialModel,
                                        Finisher<UmlExportSettings> finisher) {
    super(initialModel, finisher);
  }

  protected UmlExportSettingsController(UmlExportSettings initialModel,
                                        Finisher<UmlExportSettings> finisher,
                                        Consumer<UmlExportSettings> postProcessor) {
    super(initialModel, finisher, postProcessor);
  }

}
