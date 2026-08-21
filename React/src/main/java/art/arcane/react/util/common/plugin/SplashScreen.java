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
import art.arcane.volmlib.util.plugin.SplashScreenSupport;

public class SplashScreen {

  public static final String splash = "\n" +
      C.DARK_GRAY + "██████" + C.AQUA + "╗ " + C.DARK_GRAY + "███████" + C.AQUA + "╗ " + C.DARK_GRAY + "█████" + C.AQUA + "╗  " + C.DARK_GRAY + "██████" + C.AQUA + "╗" + C.DARK_GRAY + "████████" + C.AQUA + "╗\n" +
      C.DARK_GRAY + "██" + C.AQUA + "╔══" + C.DARK_GRAY + "██" + C.AQUA + "╗" + C.DARK_GRAY + "██" + C.AQUA + "╔════╝" + C.DARK_GRAY + "██" + C.AQUA + "╔══" + C.DARK_GRAY + "██" + C.AQUA + "╗" + C.DARK_GRAY + "██" + C.AQUA + "╔════╝╚══" + C.DARK_GRAY + "██" + C.AQUA + "╔══╝" + C.AQUA + "   React, " + C.DARK_AQUA + "Smart Server Performance " + C.RED + "[" + SplashScreenSupport.releaseTrain(React.instance.getDescription().getVersion()) + " RELEASE]\n" +
      C.DARK_GRAY + "██████" + C.AQUA + "╔╝" + C.DARK_GRAY + "█████" + C.AQUA + "╗  " + C.DARK_GRAY + "███████" + C.AQUA + "║" + C.DARK_GRAY + "██" + C.AQUA + "║        " + C.DARK_GRAY + "██" + C.AQUA + "║   " + C.GRAY + "   Version: " + C.AQUA + React.instance.getDescription().getVersion() + "\n" +
      C.DARK_GRAY + "██" + C.AQUA + "╔══" + C.DARK_GRAY + "██" + C.AQUA + "╗" + C.DARK_GRAY + "██" + C.AQUA + "╔══╝  " + C.DARK_GRAY + "██" + C.AQUA + "╔══" + C.DARK_GRAY + "██" + C.AQUA + "║" + C.DARK_GRAY + "██" + C.AQUA + "║        " + C.DARK_GRAY + "██" + C.AQUA + "║   " + C.GRAY + "   By: " + C.AQUA + "Volmit Software (Arcane Arts)" + C.GRAY + " | " + C.AQUA + "VolmitSoftware.com\n" +
      C.DARK_GRAY + "██" + C.AQUA + "║  " + C.DARK_GRAY + "██" + C.AQUA + "║" + C.DARK_GRAY + "███████" + C.AQUA + "╗" + C.DARK_GRAY + "██" + C.AQUA + "║  " + C.DARK_GRAY + "██" + C.AQUA + "║╚" + C.DARK_GRAY + "██████" + C.AQUA + "╗   " + C.DARK_GRAY + "██" + C.AQUA + "║   " + C.GRAY + "   Server: " + C.AQUA + SplashScreenSupport.serverVersionWithoutMcSuffix() + "\n" +
      C.AQUA + "╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝ ╚═════╝   ╚═╝   " + C.GRAY + "   Java: " + C.AQUA + SplashScreenSupport.javaMajorVersion() + C.GRAY + " | Date: " + C.AQUA + SplashScreenSupport.startupDate() + "\n";
}

