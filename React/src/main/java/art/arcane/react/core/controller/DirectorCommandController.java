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
import art.arcane.react.content.directorcommand.CommandReact;
import art.arcane.react.util.cache.AtomicCache;
import art.arcane.react.util.director.DirectorContext;
import art.arcane.react.util.director.DirectorContextHandler;
import art.arcane.react.util.format.C;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.context.DirectorContextRegistry;
import art.arcane.volmlib.util.director.runtime.*;
import art.arcane.volmlib.util.director.visual.DirectorVisualCommand;
import art.arcane.volmlib.util.math.RNG;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DirectorCommandController implements IController, CommandExecutor, TabCompleter, DirectorInvocationHook {
  private static final String ROOT_COMMAND = "react";
  private static final String ROOT_PERMISSION = "react.use";

  private final transient AtomicCache<DirectorRuntimeEngine> directorCache = new AtomicCache<>();
  private final transient AtomicCache<DirectorVisualCommand> helpCache = new AtomicCache<>();

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
    PluginCommand command = React.instance.getCommand(ROOT_COMMAND);
    if (command == null) {
      React.warn("Failed to find command '" + ROOT_COMMAND + "'");
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
    return directorCache.aquireNastyPrint(() -> DirectorEngineFactory.create(
        new CommandReact(),
        null,
        buildDirectorContexts(),
        this::dispatchDirector,
        this,
        List.of()
    ));
  }

  private DirectorContextRegistry buildDirectorContexts() {
    DirectorContextRegistry contexts = new DirectorContextRegistry();

    for (Map.Entry<Class<?>, DirectorContextHandler<?>> entry : DirectorContextHandler.contextHandlers.entrySet()) {
      registerContextHandler(contexts, entry.getKey(), entry.getValue());
    }

    return contexts;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void registerContextHandler(DirectorContextRegistry contexts, Class<?> type, DirectorContextHandler<?> handler) {
    contexts.register((Class) type, (invocation, map) -> {
      if (invocation.getSender() instanceof BukkitDirectorSender sender) {
        return ((DirectorContextHandler) handler).handle(new VolmitSender(sender.sender()));
      }

      return null;
    });
  }

  private void dispatchDirector(DirectorExecutionMode mode, Runnable runnable) {
    if (mode == DirectorExecutionMode.SYNC) {
      // Command execution already runs on the correct server thread context.
      // Re-queueing it can break Folia region ownership and make commands appear non-responsive.
      runnable.run();
    } else {
      runnable.run();
    }
  }

  @Override
  public void beforeInvoke(DirectorInvocation invocation, DirectorRuntimeNode node) {
    if (invocation.getSender() instanceof BukkitDirectorSender sender) {
      DirectorContext.touch(new VolmitSender(sender.sender()));
    }
  }

  @Override
  public void afterInvoke(DirectorInvocation invocation, DirectorRuntimeNode node) {
    DirectorContext.remove();
  }

  @Nullable
  @Override
  public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
    if (!command.getName().equalsIgnoreCase(ROOT_COMMAND)) {
      return List.of();
    }

    List<String> v = runDirectorTab(sender, alias, args);

    if (sender instanceof Player) {
      React.audiences.sender(sender).playSound(Sound.sound(
          Key.key("minecraft:block.amethyst_block.chime"),
          Sound.Source.PLAYER,
          0.25f,
          RNG.r.f(0.125f, 1.95f)
      ));
    }

    return v;
  }

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
    if (!command.getName().equalsIgnoreCase(ROOT_COMMAND)) {
      return false;
    }

    if (!sender.hasPermission(ROOT_PERMISSION) && !sender.hasPermission("react.*") && !sender.isOp()) {
      sender.sendMessage("You lack the Permission '" + ROOT_PERMISSION + "'");
      return true;
    }

    executeCommand(sender, label, args);
    return true;
  }

  private void executeCommand(CommandSender sender, String label, String[] args) {
    if (sendHelpIfRequested(sender, args)) {
      playSuccessSound(sender);
      return;
    }

    DirectorExecutionResult result = runDirector(sender, label, args);

    if (result.isSuccess()) {
      playSuccessSound(sender);
      return;
    }

    playFailureSound(sender);
    if (result.getMessage() == null || result.getMessage().trim().isEmpty()) {
      sender.sendMessage(C.RED + "Unknown React Command");
    }
  }

  private boolean sendHelpIfRequested(CommandSender sender, String[] args) {
    if (args.length == 0) {
      VolmitSender volmitSender = new VolmitSender(sender);
      volmitSender.sendDirectorHelp(getHelpRoot(), 0);
      return true;
    }

    Optional<DirectorVisualCommand.HelpRequest> request = DirectorVisualCommand.resolveHelp(getHelpRoot(), Arrays.asList(args));
    if (request.isEmpty()) {
      return false;
    }

    VolmitSender volmitSender = new VolmitSender(sender);
    volmitSender.sendDirectorHelp(request.get().command(), request.get().page());
    return true;
  }

  private DirectorVisualCommand getHelpRoot() {
    return helpCache.aquireNastyPrint(() -> DirectorVisualCommand.createRoot(getDirector()));
  }

  private DirectorExecutionResult runDirector(CommandSender sender, String label, String[] args) {
    try {
      return getDirector().execute(new DirectorInvocation(new BukkitDirectorSender(sender), label, Arrays.asList(args)));
    } catch (Throwable e) {
      React.warn("Director command execution failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
      React.reportError(e);
      return DirectorExecutionResult.notHandled();
    }
  }

  private List<String> runDirectorTab(CommandSender sender, String alias, String[] args) {
    try {
      return getDirector().tabComplete(new DirectorInvocation(new BukkitDirectorSender(sender), alias, Arrays.asList(args)));
    } catch (Throwable e) {
      React.warn("Director tab completion failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
      return List.of();
    }
  }

  private void playFailureSound(CommandSender sender) {
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
  }

  private void playSuccessSound(CommandSender sender) {
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

  private record BukkitDirectorSender(
      CommandSender sender) implements DirectorSender {
    @Override
    public String getName() {
      return sender.getName();
    }

    @Override
    public boolean isPlayer() {
      return sender instanceof Player;
    }

    @Override
    public void sendMessage(String message) {
      if (message != null && !message.trim().isEmpty()) {
        sender.sendMessage(message);
      }
    }
  }
}
