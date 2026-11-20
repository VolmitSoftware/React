package art.arcane.react.platform;

/**
 * What a click on a settings menu tile asks React to do. The platform host only reports the click; React's settings
 * menu performs the action.
 */
public sealed interface SettingsAction {
  record Open(String path, int page) implements SettingsAction {
  }

  record Apply(String returnPath, int returnPage, String valuePath, Object value) implements SettingsAction {
  }

  record Preset(String preset) implements SettingsAction {
  }

  record Input(String returnPath, int returnPage, String valuePath, Class<?> type, String label) implements SettingsAction {
  }

  record ServerLanguage() implements SettingsAction {
  }

  record NullSection() implements SettingsAction {
  }
}
