/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.util.project.registry;

import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.RegistryHost;
import art.arcane.volmlib.util.format.Form;

import java.io.File;
import java.io.IOException;

public interface Registered {
  default boolean autoRegister() {
    return true;
  }

  default String getConfigCategory() {
    return "core";
  }

  default String getName() {
    return Form.capitalizeWords(getId().replace("-", " "));
  }

  default String getIcon() {
    return "minecraft:slime_ball";
  }

  String getId();

  default void loadConfiguration() {
    reloadFromDisk(true);
  }

  default boolean reloadConfiguration() {
    return reloadFromDisk(false);
  }

  default Object prepareConfigurationSnapshot(File sourceFile, String rawContent) throws IOException {
    return registryHost().parseConfiguration(
        sourceFile,
        rawContent,
        getClass(),
        getConfigCategory() + ":" + getId()
    );
  }

  default boolean applyConfigurationSnapshot(Object loadedObject) {
    if (loadedObject == null || !getClass().isInstance(loadedObject)) {
      return false;
    }
    copyConfigurationFields(loadedObject);
    ReactPlatforms.get().verbose("Loaded config for " + getName());
    return true;
  }

  default boolean reloadFromDisk(boolean overwriteOnReadFailure) {
    try {
      RegistryHost host = registryHost();
      File canonicalFile = host.configFile(getConfigCategory(), getId() + ".toml");
      Object loadedObject = host.loadConfiguration(
          canonicalFile,
          getClass(),
          this,
          overwriteOnReadFailure,
          getConfigCategory() + ":" + getId(),
          "Created missing config [" + getConfigCategory() + "/" + getId() + ".toml] from defaults."
      );

      copyConfigurationFields(loadedObject);
      ReactPlatforms.get().verbose("Loaded config for " + getName() + " in " + canonicalFile.getPath());
      return true;
    } catch (Throwable e) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      if (platform != null) {
        platform.warn("Failed to load config for " + getName() + ": " + e.getMessage(), e);
      }
      return false;
    }
  }

  private static RegistryHost registryHost() {
    ReactPlatform platform = ReactPlatforms.get();
    return platform.registryHost().orElseThrow(() -> new IllegalStateException("No config storage on " + platform.platformName()));
  }

  private void copyConfigurationFields(Object loadedObject) {
    ConfigurationFields.copy(loadedObject, this);
  }
}
