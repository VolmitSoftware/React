package com.volmit.react.util.plugin;

import com.volmit.react.React;
import com.volmit.react.util.format.C;

public class SplashScreen {
    public static final String splash = "\n" +
            C.DARK_GRAY + "██████" + C.AQUA + "╗ " + C.DARK_GRAY + "███████" + C.AQUA + "╗ " + C.DARK_GRAY + "█████" + C.AQUA + "╗  " + C.DARK_GRAY + "██████" + C.AQUA + "╗" + C.DARK_GRAY + "████████" + C.AQUA + "╗\n" +
            C.DARK_GRAY + "██" + C.AQUA + "╔══" + C.DARK_GRAY + "██" + C.AQUA + "╗" + C.DARK_GRAY + "██" + C.AQUA + "╔════╝" + C.DARK_GRAY + "██" + C.AQUA + "╔══" + C.DARK_GRAY + "██" + C.AQUA + "╗" + C.DARK_GRAY + "██" + C.AQUA + "╔════╝╚══" + C.DARK_GRAY + "██" + C.AQUA + "╔══╝" + C.AQUA + "   React, " + C.DARK_AQUA + "Smart Server Performance\n" +
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

