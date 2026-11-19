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

package art.arcane.react.util.director.handlers;

import art.arcane.react.platform.CommandTargets;
import art.arcane.react.util.director.DirectorHosts;
import art.arcane.react.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class WorldHandler implements DirectorParameterHandler<CommandTargets.World> {
  private static final String EXCLUDED_PREFIX = "iris/";
  private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9._-]+");
  private static final Pattern KEY = Pattern.compile("[a-z0-9/._-]+");

  static List<CommandTargets.World> worldOptions() {
    List<CommandTargets.World> options = new ArrayList<>();
    for (CommandTargets.World world : DirectorHosts.command().worlds()) {
      if (!world.keyPath().toLowerCase(Locale.ROOT).startsWith(EXCLUDED_PREFIX)) {
        options.add(world);
      }
    }
    return options;
  }

  @Override
  public KList<CommandTargets.World> getPossibilities() {
    return new KList<>(worldOptions());
  }

  @Override
  public String toString(CommandTargets.World world) {
    return world.key();
  }

  @Override
  public CommandTargets.World parse(String in, boolean force) throws DirectorParsingException {
    String value = in == null ? "" : in.trim();
    List<CommandTargets.World> options = worldOptions();
    if (!value.contains(":")) {
      CommandTargets.World match = null;
      for (CommandTargets.World world : options) {
        boolean matchesName = world.name().equalsIgnoreCase(value);
        boolean matchesKeyPath = world.keyPath().equalsIgnoreCase(value);
        if (!matchesName && !matchesKeyPath) {
          continue;
        }
        if (match != null && !match.equals(world)) {
          throw new DirectorParsingException("Unable to filter which World \"" + in + "\"");
        }
        match = world;
      }

      if (match != null) {
        return match;
      }
      throw new DirectorParsingException("Unable to find World \"" + in + "\"");
    }

    if (!isNamespacedKey(value)) {
      throw new DirectorParsingException("Invalid world key \"" + in + "\"");
    }

    for (CommandTargets.World world : options) {
      if (value.equals(world.key())) {
        return world;
      }
    }
    throw new DirectorParsingException("Unable to find World \"" + in + "\"");
  }

  static boolean isNamespacedKey(String value) {
    int separator = value.indexOf(':');
    if (separator <= 0 || separator == value.length() - 1) {
      return false;
    }
    String[] components = value.split(":", 3);
    return components.length == 2
        && NAMESPACE.matcher(components[0]).matches()
        && KEY.matcher(components[1]).matches();
  }

  @Override
  public boolean supports(Class<?> type) {
    return type.equals(CommandTargets.World.class);
  }

  @Override
  public String getRandomDefault() {
    return "minecraft:overworld";
  }
}
