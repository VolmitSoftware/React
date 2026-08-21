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

package art.arcane.react.content.PAPI;

import art.arcane.volmlib.util.bukkit.papi.VolmitPlaceholderExpansion;

import java.util.Objects;
import java.util.logging.Logger;

public final class PapiExpansion extends VolmitPlaceholderExpansion {
  public static final String IDENTIFIER = "react";
  public static final String AUTHOR = "Volmit Software";
  public static final String VERSION = "2.0.0";
  public static final String REQUIRED_PLUGIN = "React";

  public PapiExpansion(ReactPlaceholderSource source, Logger logger) {
    super(IDENTIFIER, AUTHOR, VERSION, REQUIRED_PLUGIN, Objects.requireNonNull(source, "source").registry(), logger);
  }
}
