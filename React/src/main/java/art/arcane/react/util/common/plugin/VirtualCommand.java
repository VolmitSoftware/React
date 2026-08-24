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

package art.arcane.react.util.plugin;


import art.arcane.react.React;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RuntimeMessages;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.reflect.V;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;

/**
 * Represents a virtual command. A chain of iterative processing through
 * subcommands.
 *
 * @author cyberpwn
 */
public class VirtualCommand {
  private final ICommand command;
  private final String tag;

  private final KMap<KList<String>, VirtualCommand> children;

  public VirtualCommand(ICommand command) {
    this(command, "");
  }

  public VirtualCommand(ICommand command, String tag) {
    this.command = command;
    children = new KMap<>();
    this.tag = tag;

    for (Field i : command.getClass().getDeclaredFields()) {
      if (i.isAnnotationPresent(Command.class)) {
        try {
          Command cc = i.getAnnotation(Command.class);
          ICommand cmd = (ICommand) i.getType().getConstructor().newInstance();
          new V(command, true, true).set(i.getName(), cmd);
          children.put(cmd.getAllNodes(), new VirtualCommand(cmd, cc.value().trim().isEmpty() ? tag : cc.value().trim()));
        } catch (Exception e) {
          React.reportError("Failed to construct virtual command field " + i.getName(), e);
        }
      }
    }
  }

  public String getTag() {
    return tag;
  }

  public ICommand getCommand() {
    return command;
  }

  public KMap<KList<String>, VirtualCommand> getChildren() {
    return children;
  }

  public boolean hit(CommandSender sender, KList<String> chain) {
    return hit(sender, chain, null);
  }

  public boolean hit(CommandSender sender, KList<String> chain, String label) {
    VolmitSender vs = new VolmitSender(sender);
    vs.setTag(tag);

    if (label != null) {
      vs.setCommand(label);
    }

    if (chain.isEmpty()) {
      if (!checkPermissions(sender, command)) {
        return true;
      }

      return command.handle(vs, new String[0]);
    }

    String nl = chain.get(0);

    for (KList<String> i : children.k()) {
      for (String j : i) {
        if (j.equalsIgnoreCase(nl)) {
          vs.setCommand(chain.get(0));
          VirtualCommand cmd = children.get(i);
          KList<String> c = chain.copy();
          c.remove(0);
          if (cmd.hit(sender, c, vs.getCommand())) {
            if (vs.isPlayer()) {
              vs.player().playSound(Sound.sound(
                  Key.key("minecraft:item.axe.strip"),
                  Sound.Source.PLAYER,
                  0.35f,
                  1.8f
              ));
            }

            return true;
          }
        }
      }
    }

    if (!checkPermissions(sender, command)) {
      return true;
    }

    return command.handle(vs, chain.toArray(new String[0]));
  }

  public KList<String> hitTab(CommandSender sender, KList<String> chain, String label) {
    VolmitSender vs = new VolmitSender(sender);
    vs.setTag(tag);

    if (label != null)
      vs.setCommand(label);

    if (chain.isEmpty()) {
      if (!checkPermissions(sender, command)) {
        return null;
      }

      return command.handleTab(vs, new String[0]);
    }

    String nl = chain.get(0);

    for (KList<String> i : children.k()) {
      for (String j : i) {
        if (j.equalsIgnoreCase(nl)) {
          vs.setCommand(chain.get(0));
          VirtualCommand cmd = children.get(i);
          KList<String> c = chain.copy();
          c.remove(0);
          KList<String> v = cmd.hitTab(sender, c, vs.getCommand());
          if (v != null) {
            return v;
          }
        }
      }
    }

    if (!checkPermissions(sender, command)) {
      return null;
    }

    return command.handleTab(vs, chain.toArray(new String[0]));
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private boolean checkPermissions(CommandSender sender, ICommand command2) {
    boolean failed = false;

    for (String i : command.getRequiredPermissions()) {
      if (!sender.hasPermission(i)) {
        failed = true;
        if (sender instanceof Player) {
          Player player = (Player) sender;
          J.runEntity(player, () -> ReactLanguage.send(
              sender,
              RuntimeMessages.LEGACY_PERMISSION_ENTRY,
              MessageArgument.untrusted("permission", i)
          ));
        } else {
          J.s(() -> ReactLanguage.send(
              sender,
              RuntimeMessages.LEGACY_PERMISSION_ENTRY,
              MessageArgument.untrusted("permission", i)
          ));
        }
      }
    }

    if (failed) {
      ReactLanguage.send(sender, RuntimeMessages.LEGACY_INSUFFICIENT_PERMISSIONS);
      return false;
    }

    return true;
  }
}
