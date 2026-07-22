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

package art.arcane.react.content.directorcommand;

import art.arcane.curse.Curse;
import art.arcane.react.React;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.core.controller.PlayerController;
import art.arcane.react.core.gui.ReactMapGUI;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.localization.catalog.RuntimeMessages;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.director.handlers.ReactRendererHandler;
import art.arcane.react.util.project.world.WorldDistanceSupport;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.command.CommandSender;

@Director(
    name = "react",
    aliases = {"re"},
    origin = DirectorOrigin.BOTH,
    description = "The root React command",
    descriptionKey = "command.description.root"
)
public class CommandReact implements DirectorExecutor {
  private CommandConfig config;
  private CommandAction action;
  private CommandChunk chunk;
  private CommandEnvironment environment;
  private CommandBenchmark benchmark;
  private CommandDebug debug;
  private CommandDev dev;
  private CommandIntegration integration;
  private CommandBridge bridge;
  private CommandTest test;
  private CommandWeb web;


  @Director(
      name = "monitor",
      aliases = {"m", "mon"},
      description = "Monitor the server via action bar",
      descriptionKey = "command.description.monitor",
      origin = DirectorOrigin.PLAYER
  )
  public void monitor() {
    React.controller(PlayerController.class).getPlayer(player()).toggleActionBar();
    boolean enabled = React.controller(PlayerController.class).getPlayer(player()).isActionBarMonitoring();
    ReactLanguage.sendPrefixed(sender(), enabled ? RuntimeMessages.MONITOR_ENABLED : RuntimeMessages.MONITOR_DISABLED);
  }


  @Director(
      name = "set-player-view-distance",
      aliases = {"vd", "view-distance"},
      description = "Set the current world's view and simulation distance",
      descriptionKey = "command.description.view_distance",
      origin = DirectorOrigin.PLAYER
  )
  public void vd(
      @Param(
          name = "distance",
          description = "The view and simulation distance in chunks",
          descriptionKey = "command.parameter.view_distance.distance"
      )
      int d) {
    if (!WorldDistanceSupport.supportsWorldDistanceSetters()) {
      ReactLanguage.sendPrefixed(sender(), CommandMessages.PAPER_REQUIRED);
      return;
    }

    if (d > 32) {
      d = 32;
    }

    Curse.on(player().getWorld()).method("setViewDistance", int.class).invoke(d);
    Curse.on(player().getWorld()).method("setSimulationDistance", int.class).invoke(d);
    ReactLanguage.sendPrefixed(
        sender(),
        CommandMessages.VIEW_DISTANCE_SET,
        MessageArgument.untrusted("world", player().getWorld().getName()),
        MessageArgument.untrusted("distance", d)
    );
  }

  @Director(
      name = "map",
      description = "Open or select a React map renderer",
      descriptionKey = "command.description.map",
      origin = DirectorOrigin.PLAYER
  )
  public void map(
      @Param(
          name = "renderer",
          description = "The map renderer to open",
          descriptionKey = "command.parameter.map.renderer",
          defaultValue = "unknown",
          customHandler = ReactRendererHandler.class
      ) ReactRenderer renderer
  ) {
    if (renderer == null || "unknown".equalsIgnoreCase(renderer.getId())) {
      ReactMapGUI.open(player());
      return;
    }

    React.controller(MapController.class).openRenderer(player(), renderer);
  }

  @Director(
      name = "reload",
      aliases = {"rl"},
      description = "Reload React",
      descriptionKey = "command.description.reload")
  public void reload() {
    CommandSender commandSender = sender();
    ReactLanguage.send(commandSender, CommandMessages.RELOAD_STARTING);
    Runnable reload = () -> {
      if (React.instance.reload()) {
        ReactLanguage.send(
            commandSender,
            CommandMessages.RELOAD_SUCCESS,
            MessageArgument.untrusted("version", React.instance.getDescription().getVersion())
        );
      } else {
        ReactLanguage.send(commandSender, CommandMessages.RELOAD_FAILED);
      }
    };

    if (J.isFoliaThreading()) {
      if (!FoliaScheduler.runGlobal(React.instance, reload)) {
        ReactLanguage.send(commandSender, CommandMessages.RELOAD_SCHEDULE_FAILED);
      }
      return;
    }

    reload.run();
  }

  @Director(
      name = "version",
      aliases = {"v"},
      description = "Show the React version",
      descriptionKey = "command.description.version")
  public void version() {
    ReactLanguage.send(
        sender(),
        CommandMessages.VERSION,
        MessageArgument.untrusted("version", React.instance.getDescription().getVersion())
    );
  }
}
