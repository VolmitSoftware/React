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

package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.platform.bukkit.BukkitCommandSender;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.director.ReactDirector;
import art.arcane.react.util.plugin.IController;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DirectorCommandController implements IController, CommandExecutor, TabCompleter {
  private final transient ReactDirector director = new ReactDirector();

  @Override
  public String getName() {
    return "Command";
  }

  @Override
  public String getId() {
    return "command";
  }

  @Override
  public void start() {
    PluginCommand command = React.instance.getCommand(ReactDirector.ROOT_COMMAND);
    if (command == null) {
      React.warn("Failed to find command '" + ReactDirector.ROOT_COMMAND + "'");
      return;
    }

    command.setExecutor(this);
    command.setTabCompleter(this);
    J.a(this::getDirector);
  }

  @Override
  public void stop() {

  }

  @Override
  public void postStart() {

  }

  public DirectorRuntimeEngine getDirector() {
    return director.getDirector();
  }

  @Nullable
  @Override
  public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
    if (!command.getName().equalsIgnoreCase(ReactDirector.ROOT_COMMAND)) {
      return List.of();
    }

    return director.complete(new BukkitCommandSender(sender), alias, args);
  }

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
    if (!command.getName().equalsIgnoreCase(ReactDirector.ROOT_COMMAND)) {
      return false;
    }

    director.execute(new BukkitCommandSender(sender), label, args);
    return true;
  }
}
