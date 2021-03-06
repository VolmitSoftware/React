package com.volmit.react.util;

import org.bukkit.Bukkit;

public enum VersionBukkit {
    VU,
    V7,
    V8,
    V9,
    V11,
    V111,
    V112,
    V113,
    V114,
    V115,
    V116;

    public static boolean tc() {
        return !get().equals(V7);
    }

    public static boolean uc() {
        return !get().equals(V7) && !get().equals(V8);
    }

    public static VersionBukkit get() {
        if (Bukkit.getBukkitVersion().startsWith("1.7")) {
            return V7;
        }

        if (Bukkit.getBukkitVersion().startsWith("1.8")) {
            return V8;
        }

        if (Bukkit.getBukkitVersion().startsWith("1.9")) {
            return V9;
        }

        if (Bukkit.getBukkitVersion().startsWith("1.10")) {
            return V11;
        }

        if (Bukkit.getBukkitVersion().startsWith("1.11")) {
            return V111;
        }

        if (Bukkit.getBukkitVersion().startsWith("1.12")) {
            return V112;
        }

        if (Bukkit.getBukkitVersion().startsWith("1.13")) {
            return V113;
        }

        if (Bukkit.getBukkitVersion().startsWith("1.14")) {
            return V114;
        }

        if (Bukkit.getBukkitVersion().startsWith("1.15")) {
            return V115;
        }

        if (Bukkit.getBukkitVersion().startsWith("1.16")) {
            return V116;
        }

        return VU;
    }
}
