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

import art.arcane.react.platform.CommandTargets;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.director.handlers.ReactRendererHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

@Director(
    name = "react",
    aliases = {"re"},
    origin = DirectorOrigin.BOTH,
    description = "The root React command",
    descriptionKey = "command.description.root"
)
public class CommandReact implements DirectorExecutor {
  private CommandConfig config;
  private CommandDistance distance;
  private CommandAction action;
  private CommandFeature feature;
  private CommandTweak tweak;
  private CommandChunk chunk;
  private CommandEnvironment environment;
  private CommandBenchmark benchmark;
  private CommandDebug debug;
  private CommandDev dev;
  private CommandIntegration integration;
  private CommandPluginApi pluginApi;
  private CommandBridge bridge;
  private CommandTest test;
  private CommandWeb web;
  private CommandNetwork network;

  @Director(
      name = "language",
      aliases = {"languages"},
      description = "Choose your language or the server language",
      descriptionKey = "command.description.language"
  )
  public void language() {
    host().openLanguage(sender());
  }

  @Director(
      name = "monitor",
      aliases = {"m", "mon"},
      description = "Monitor the server via action bar",
      descriptionKey = "command.description.monitor",
      origin = DirectorOrigin.PLAYER
  )
  public void monitor() {
    host().monitor(sender());
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
      ) CommandTargets.ReactRenderer renderer
  ) {
    host().map(sender(), renderer == null ? "unknown" : renderer.getId());
  }

  @Director(
      name = "monitoring-only",
      aliases = {"monitor-only", "monitoring-mode", "mo"},
      description = "Toggle monitoring-only mode without changing configuration",
      descriptionKey = "command.description.monitoring_only"
  )
  public void monitoringOnly() {
    host().monitoringOnly(sender());
  }
}
