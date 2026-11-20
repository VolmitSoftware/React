package art.arcane.react.core.gui;

import art.arcane.react.core.controller.SampleController;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The platform side of React's settings menu: the main and core config objects, where config files live and how
 * a changed file is written and applied.
 */
public interface SettingsSource {
  record CoreSection(String id, String name, String icon, Object config) {
  }

  boolean canConfigure(UUID viewerId);

  /**
   * The live {@code react.toml} settings object, or null when it is not loaded.
   */
  Object mainSettings();

  File mainFile();

  /**
   * Re-reads {@code react.toml} and applies it. False keeps the previous file contents.
   */
  boolean reloadMain();

  List<CoreSection> coreSections();

  /**
   * Re-reads {@code core/<id>.toml} and applies it.
   */
  boolean reloadCore(String id);

  File dataFile(String category, String fileName);

  SampleController samples();

  default void afterSamplerReload() {
  }

  /**
   * Writes a config file React changed itself, so the platform's hotload does not apply it a second time.
   */
  void writeConfig(File file, String content) throws IOException;

  void openServerLanguage(UUID viewerId);

  Optional<SettingsInputSessions> inputSessions();
}
