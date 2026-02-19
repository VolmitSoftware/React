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

package art.arcane.react.util.common.plugin;

import art.arcane.react.React;
import art.arcane.react.util.format.C;
import org.bukkit.Bukkit;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class SplashScreen {
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

  public static final String splash = "\n" +
      C.DARK_GRAY + "██████" + C.AQUA + "╗ " + C.DARK_GRAY + "███████" + C.AQUA + "╗ " + C.DARK_GRAY + "█████" + C.AQUA + "╗  " + C.DARK_GRAY + "██████" + C.AQUA + "╗" + C.DARK_GRAY + "████████" + C.AQUA + "╗\n" +
      C.DARK_GRAY + "██" + C.AQUA + "╔══" + C.DARK_GRAY + "██" + C.AQUA + "╗" + C.DARK_GRAY + "██" + C.AQUA + "╔════╝" + C.DARK_GRAY + "██" + C.AQUA + "╔══" + C.DARK_GRAY + "██" + C.AQUA + "╗" + C.DARK_GRAY + "██" + C.AQUA + "╔════╝╚══" + C.DARK_GRAY + "██" + C.AQUA + "╔══╝" + C.AQUA + "   React, " + C.DARK_AQUA + "Smart Server Performance " + C.RED + "[" + getReleaseTrain(React.instance.getDescription().getVersion()) + " RELEASE]\n" +
      C.DARK_GRAY + "██████" + C.AQUA + "╔╝" + C.DARK_GRAY + "█████" + C.AQUA + "╗  " + C.DARK_GRAY + "███████" + C.AQUA + "║" + C.DARK_GRAY + "██" + C.AQUA + "║        " + C.DARK_GRAY + "██" + C.AQUA + "║   " + C.GRAY + "   Version: " + C.AQUA + React.instance.getDescription().getVersion() + "\n" +
      C.DARK_GRAY + "██" + C.AQUA + "╔══" + C.DARK_GRAY + "██" + C.AQUA + "╗" + C.DARK_GRAY + "██" + C.AQUA + "╔══╝  " + C.DARK_GRAY + "██" + C.AQUA + "╔══" + C.DARK_GRAY + "██" + C.AQUA + "║" + C.DARK_GRAY + "██" + C.AQUA + "║        " + C.DARK_GRAY + "██" + C.AQUA + "║   " + C.GRAY + "   By: " + C.AQUA + "Volmit Software (Arcane Arts)\n" +
      C.DARK_GRAY + "██" + C.AQUA + "║  " + C.DARK_GRAY + "██" + C.AQUA + "║" + C.DARK_GRAY + "███████" + C.AQUA + "╗" + C.DARK_GRAY + "██" + C.AQUA + "║  " + C.DARK_GRAY + "██" + C.AQUA + "║╚" + C.DARK_GRAY + "██████" + C.AQUA + "╗   " + C.DARK_GRAY + "██" + C.AQUA + "║   " + C.GRAY + "   Server: " + C.AQUA + getServerVersion() + "\n" +
      C.AQUA + "╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝ ╚═════╝   ╚═╝   " + C.GRAY + "   Java: " + C.AQUA + getJavaVersion() + C.GRAY + " | Date: " + C.AQUA + getStartupDate() + "\n";


  public static int getJavaVersion() {
    String version = System.getProperty("java.version");
    if (version.startsWith("1.")) {
      version = version.substring(2, 3);
    } else {
      int dot = version.indexOf(".");
      if (dot != -1) {
        version = version.substring(0, dot);
      }
    }
    return Integer.parseInt(version);
  }

  private static String getServerVersion() {
    String version = Bukkit.getVersion();
    int mcMarkerIndex = version.indexOf(" (MC:");
    if (mcMarkerIndex != -1) {
      version = version.substring(0, mcMarkerIndex);
    }
    return version;
  }

  private static String getStartupDate() {
    return LocalDate.now().format(DATE_FORMATTER);
  }

  private static String getReleaseTrain(String version) {
    String value = version;
    int suffixIndex = value.indexOf('-');
    if (suffixIndex >= 0) {
      value = value.substring(0, suffixIndex);
    }
    String[] split = value.split("\\.");
    if (split.length >= 2) {
      return split[0] + "." + split[1];
    }
    return value;
  }

}

