package com.volmit.react.util.world;

import art.arcane.curse.Curse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.util.Optional;
import java.util.UUID;

public class CustomMobChecker {
    public static boolean isCustom(Entity entity) {
        try {
            if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
                Optional<?> o = Curse.on(Curse.on(Curse.on(Class.forName("io.lumine.mythic.bukkit.MythicBukkit"))
                                .method("inst").invoke()).method("getMobManager").invoke())
                        .method("getActiveMob", UUID.class).invoke(entity.getUniqueId());

                return o.isPresent();
            }
        } catch (Throwable ignored) {

        }

        return false;
    }
}
