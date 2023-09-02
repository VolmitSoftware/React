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

package com.volmit.react.util.plugin;

import com.volmit.react.React;
import com.volmit.react.util.format.C;

public class SplashScreen {
    public static final String splash = "\n" +
            C.DARK_GRAY + "██████" + C.AQUA + "╗ " + C.DARK_GRAY + "███████" + C.AQUA + "╗ " + C.DARK_GRAY + "█████" + C.AQUA + "╗  " + C.DARK_GRAY + "██████" + C.AQUA + "╗" + C.DARK_GRAY + "████████" + C.AQUA + "╗\n" +
            C.DARK_GRAY + "██" + C.AQUA + "╔══" + C.DARK_GRAY + "██" + C.AQUA + "╗" + C.DARK_GRAY + "██" + C.AQUA + "╔════╝" + C.DARK_GRAY + "██" + C.AQUA + "╔══" + C.DARK_GRAY + "██" + C.AQUA + "╗" + C.DARK_GRAY + "██" + C.AQUA + "╔════╝╚══" + C.DARK_GRAY + "██" + C.AQUA + "╔══╝" + C.AQUA + "   React, " + C.DARK_AQUA + "Smart Server Performance " + C.RED + "[INITIAL RELEASE]\n" +
            C.DARK_GRAY + "██████" + C.AQUA + "╔╝" + C.DARK_GRAY + "█████" + C.AQUA + "╗  " + C.DARK_GRAY + "███████" + C.AQUA + "║" + C.DARK_GRAY + "██" + C.AQUA + "║        " + C.DARK_GRAY + "██" + C.AQUA + "║   " + C.GRAY + "   Version: " + C.AQUA + React.instance.getDescription().getVersion() + "\n" +
            C.DARK_GRAY + "██" + C.AQUA + "╔══" + C.DARK_GRAY + "██" + C.AQUA + "╗" + C.DARK_GRAY + "██" + C.AQUA + "╔══╝  " + C.DARK_GRAY + "██" + C.AQUA + "╔══" + C.DARK_GRAY + "██" + C.AQUA + "║" + C.DARK_GRAY + "██" + C.AQUA + "║        " + C.DARK_GRAY + "██" + C.AQUA + "║   " + C.GRAY + "   By: " + C.RED + "A" + C.GOLD + "r" + C.YELLOW + "c" + C.GREEN + "a" + C.DARK_GRAY + "n" + C.AQUA + "e " + C.AQUA + "A" + C.BLUE + "r" + C.DARK_BLUE + "t" + C.DARK_PURPLE + "s" + C.DARK_AQUA + " (Volmit Software)\n" +
            C.DARK_GRAY + "██" + C.AQUA + "║  " + C.DARK_GRAY + "██" + C.AQUA + "║" + C.DARK_GRAY + "███████" + C.AQUA + "╗" + C.DARK_GRAY + "██" + C.AQUA + "║  " + C.DARK_GRAY + "██" + C.AQUA + "║╚" + C.DARK_GRAY + "██████" + C.AQUA + "╗   " + C.DARK_GRAY + "██" + C.AQUA + "║   " + C.GRAY + "   Java Version: " + C.AQUA + getJavaVersion() + "     \n" +
            C.AQUA + "╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝ ╚═════╝   ╚═╝   \n";


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

}

