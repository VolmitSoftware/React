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

package com.volmit.react.util.messaging;

import com.volmit.react.React;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public class ActionBarHelper {
    private static final boolean HAS_NATIVE_ADVENTURE;

    static {
        boolean hasNative = false;
        try {
            // Check if Player has native function (which means it's 1.20+)
            Player.class.getMethod("sendActionBar", Component.class);
            hasNative = true;
        } catch (NoSuchMethodException e) {
            // Fall back to BukkitAudiences
        }
        HAS_NATIVE_ADVENTURE = hasNative;
    }

    public static void sendActionBar(Player player, Component component) {
        if (HAS_NATIVE_ADVENTURE) {
            // Reflection for native function
            try {
                Method sendActionBarMethod = player.getClass().getMethod("sendActionBar", Component.class);
                sendActionBarMethod.invoke(player, component);
            } catch (Exception e) {
                // Fallback to audiences
                fallbackToAudiences(player, component);
            }
        } else {
            fallbackToAudiences(player, component);
        }
    }

    public static void sendTitlePart(Player player, TitlePart<Component> part, Component component) {
        if (HAS_NATIVE_ADVENTURE) {
            // Reflection for native function
            try {
                Method sendTitlePartMethod = player.getClass().getMethod("sendTitlePart", TitlePart.class,
                        Object.class);
                sendTitlePartMethod.invoke(player, part, component);
            } catch (Exception e) {
                // Fallback to audiences
                fallbackToAudiencesTitlePart(player, part, component);
            }
        } else {
            fallbackToAudiencesTitlePart(player, part, component);
        }
    }

    public static void sendTitlePart(Player player, TitlePart<Title.Times> part, Title.Times times) {
        if (HAS_NATIVE_ADVENTURE) {
            // Reflection for native function
            try {
                Method sendTitlePartMethod = player.getClass().getMethod("sendTitlePart", TitlePart.class,
                        Object.class);
                sendTitlePartMethod.invoke(player, part, times);
            } catch (Exception e) {
                // Fallback to audiences
                fallbackToAudiencesTitlePartTimes(player, part, times);
            }
        } else {
            fallbackToAudiencesTitlePartTimes(player, part, times);
        }
    }

    private static void fallbackToAudiences(Player player, Component component) {
        try {
            if (React.audiences != null) {
                React.audiences.player(player).sendActionBar(component);
            }
        } catch (Exception e) { // if I can't fix it through the other ways, too bad.
        }
    }

    private static void fallbackToAudiencesTitlePart(Player player, TitlePart<Component> part, Component component) {
        try {
            if (React.audiences != null) {
                React.audiences.player(player).sendTitlePart(part, component);
            }
        } catch (Exception e) { // if I can't fix it through the other ways, too bad.
        }
    }

    private static void fallbackToAudiencesTitlePartTimes(Player player, TitlePart<Title.Times> part,
            Title.Times times) {
        try {
            if (React.audiences != null) {
                React.audiences.player(player).sendTitlePart(part, times);
            }
        } catch (Exception e) { // if I can't fix it through the other ways, too bad.
        }
    }
}