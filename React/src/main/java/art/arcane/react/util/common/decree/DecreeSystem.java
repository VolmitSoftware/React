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

package art.arcane.react.util.decree;


import art.arcane.volmlib.util.decree.DecreeSystemSupport;
import art.arcane.react.React;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.react.util.decree.virtual.VirtualDecreeCommand;
import art.arcane.react.util.format.C;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.react.util.scheduling.J;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface DecreeSystem extends CommandExecutor, TabCompleter {
    KList<DecreeParameterHandler<?>> handlers = React.initialize("art.arcane.react.util.decree.handlers", null).convert((i) -> (DecreeParameterHandler<?>) i);

    static KList<String> enhanceArgs(String[] args) {
        return new KList<>(DecreeSystemSupport.enhanceArgs(args));
    }

    static KList<String> enhanceArgs(String[] args, boolean trim) {
        return new KList<>(DecreeSystemSupport.enhanceArgs(args, trim));
    }

    /**
     * Get the handler for the specified type
     *
     * @param type The type to handle
     * @return The corresponding {@link DecreeParameterHandler}, or null
     */
    static DecreeParameterHandler<?> getHandler(Class<?> type) {
        DecreeParameterHandler<?> handler = DecreeSystemSupport.getHandler(handlers, type, (h, t) -> h.supports(t));
        if (handler != null) {
            return handler;
        }

        React.error("Unhandled type in Decree Parameter: " + type.getName() + ". This is bad!");
        return null;
    }

    /**
     * The root class to start command searching from
     */
    VirtualDecreeCommand getRoot();

    default boolean call(VolmitSender sender, String[] args) {
        DecreeContext.touch(sender);
        try {
            return getRoot().invoke(sender, enhanceArgs(args));
        } finally {
            DecreeContext.remove();
        }
    }

    @Nullable
    @Override
    default List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        DecreeContext.touch(new VolmitSender(sender));
        try {
            KList<String> enhanced = new KList<>(args);
            KList<String> v = getRoot().tabComplete(enhanced, enhanced.toString(" "));
            v.removeDuplicates();

            if (sender instanceof Player) {
                React.audiences.sender(sender).playSound(Sound.sound(
                        Key.key("minecraft:block.amethyst_block.chime"),
                        Sound.Source.PLAYER,
                        0.25f,
                        RNG.r.f(0.125f, 1.95f)
                ));
            }

            return v;
        } finally {
            DecreeContext.remove();
        }
    }

    @Override
    default boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("react.use")) {
            sender.sendMessage("You lack the Permission 'react.use'");
            return true;
        }

        J.a(() -> {
            if (!call(new VolmitSender(sender), args)) {
                if (sender instanceof Player) {
                    React.audiences.sender(sender).playSound(Sound.sound(
                            Key.key("minecraft:block.respawn_anchor.deplete"),
                            Sound.Source.PLAYER,
                            0.77f,
                            0.25f
                    ));
                    React.audiences.sender(sender).playSound(Sound.sound(
                            Key.key("minecraft:block.beacon.deactivate"),
                            Sound.Source.PLAYER,
                            0.2f,
                            0.45f
                    ));
                }

                sender.sendMessage(C.RED + "Unknown React Command");
            } else {
                if (sender instanceof Player) {
                    React.audiences.sender(sender).playSound(Sound.sound(
                            Key.key("minecraft:block.amethyst_cluster.break"),
                            Sound.Source.PLAYER,
                            0.77f,
                            1.65f
                    ));
                    React.audiences.sender(sender).playSound(Sound.sound(
                            Key.key("minecraft:block.respawn_anchor.charge"),
                            Sound.Source.PLAYER,
                            0.125f,
                            2.99f
                    ));
                }
            }
        });
        return true;
    }
}
