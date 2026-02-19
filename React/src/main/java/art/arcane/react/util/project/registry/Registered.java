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

import art.arcane.curse.Curse;
import art.arcane.curse.model.CursedComponent;
import art.arcane.curse.model.CursedField;
import art.arcane.react.React;
import art.arcane.react.util.project.config.ConfigFileSupport;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

import java.io.File;

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

  default Material getIcon() {
    return Material.SLIME_BALL;
  }

  String getId();

  @SuppressWarnings({"unchecked", "rawtypes"})
  default void loadConfiguration() {
    File canonicalFile = React.instance.getDataFile(getConfigCategory(), getId() + ".toml");
    File legacyFile = React.instance.getDataFile(getConfigCategory(), getId() + ".json");

    try {
      Object loadedObject = ConfigFileSupport.load(
          canonicalFile,
          legacyFile,
          (Class) getClass(),
          this,
          true,
          getConfigCategory() + ":" + getId(),
          "Created missing config [" + getConfigCategory() + "/" + getId() + ".toml] from defaults."
      );

      CursedComponent loaded = Curse.on(loadedObject);
      CursedComponent current = Curse.on(this);
      current.instanceFields().filter(i -> !i.isFinal() && !i.isTransient()).forEach((self) -> {
        CursedField from = loaded.field(self.field().getName());
        Object oFrom = from.get();

        if (oFrom != null) {
          self.set(oFrom);
        }
      });
      React.verbose("Loaded config for " + getName() + " in " + canonicalFile.getPath());
    } catch (Throwable e) {
      React.warn("Failed to load config for " + getName() + ": " + e.getMessage());
    }
  }
}
