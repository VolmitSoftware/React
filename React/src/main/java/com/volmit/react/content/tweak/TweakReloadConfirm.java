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

package com.volmit.react.content.tweak;

import com.volmit.react.React;
import com.volmit.react.api.tweak.ReactTweak;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class TweakReloadConfirm extends ReactTweak implements Listener {
    public static final String ID = "reload-confirm";


    public TweakReloadConfirm() {
        super(ID);
    }

    @EventHandler
    public void on(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().split(" ")[0];
        if (command.equalsIgnoreCase("/reload")) {
            Player player = event.getPlayer();
            CommandSender sender = event.getPlayer();
            if (player.isOp() || player.hasPermission("*")) {
                React.info("Reloading the server with the Shorthand!");
                event.setMessage("/reload confirm");
            }
        }
    }


    @Override
    public void onActivate() {
    }

    public void onDeactivate() {
    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @Override
    public void onTick() {
    }
}
