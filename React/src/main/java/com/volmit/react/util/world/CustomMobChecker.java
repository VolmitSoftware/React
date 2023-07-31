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
