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


import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RuntimeMessages;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.localization.MessageArgument;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;

/**
 * Represents a pawn command
 *
 * @author cyberpwn
 */
public abstract class MortarCommand implements ICommand {
  private final KList<MortarCommand> children;
  private final KList<String> nodes;
  private final KList<String> requiredPermissions;
  private final String node;
  private String category;
  private String description;

  /**
   * Override this with a super constructor as most commands shouldn't change
   * these parameters
   *
   * @param node  the node (primary node) i.e. volume
   * @param nodes the aliases. i.e. v, vol, bile
   */
  public MortarCommand(String node, String... nodes) {
    category = "";
    this.node = node;
    this.nodes = new KList<>(nodes);
    requiredPermissions = new KList<>();
    children = buildChildren();
    description = null;
  }

  @Override
  public KList<String> handleTab(VolmitSender sender, String[] args) {
    KList<String> v = new KList<>();
    if (args.length == 0) {
      for (MortarCommand i : getChildren()) {
        v.add(i.getNode());
      }
    }

    addTabOptions(sender, args, v);

    if (v.isEmpty()) {
      return null;
    }

    if (sender.isPlayer()) {
      sender.player().playSound(Sound.sound(
          Key.key("minecraft:entity.item_frame.rotate_item"),
          Sound.Source.PLAYER,
          0.25f,
          1.7f
      ));
    }

    return v;
  }

  public abstract void addTabOptions(VolmitSender sender, String[] args, KList<String> list);

  protected abstract String getArgsUsage();

  public String getDescription() {
    return description == null ? ReactLanguage.plain(RuntimeMessages.LEGACY_NO_DESCRIPTION) : description;
  }

  protected void setDescription(String description) {
    this.description = description;
  }

  protected void requiresPermission(MortarPermission node) {
    if (node == null) {
      return;
    }

    requiresPermission(node.toString());
  }

  protected void requiresPermission(String node) {
    if (node == null) {
      return;
    }

    requiredPermissions.add(node);
  }

  public void rejectAny(int past, VolmitSender sender, String[] a) {
    if (a.length > past) {
      int p = past;

      StringBuilder m = new StringBuilder();

      for (String i : a) {
        p--;
        if (p < 0) {
          m.append(i).append(", ");
        }
      }

      if (!m.toString().trim().isEmpty()) {
        ReactLanguage.send(
            sender,
            RuntimeMessages.LEGACY_PARAMETERS_IGNORED,
            MessageArgument.untrusted("parameters", m.toString())
        );
      }
    }
  }

  @Override
  public String getNode() {
    return node;
  }

  @Override
  public KList<String> getNodes() {
    return nodes;
  }

  @Override
  public KList<String> getAllNodes() {
    return getNodes().copy().qadd(getNode());
  }

  @Override
  public void addNode(String node) {
    getNodes().add(node);
  }

  public KList<MortarCommand> getChildren() {
    return children;
  }

  private KList<MortarCommand> buildChildren() {
    KList<MortarCommand> p = new KList<>();

    for (Field i : getClass().getDeclaredFields()) {
      if (i.isAnnotationPresent(Command.class)) {
        try {
          i.setAccessible(true);
          MortarCommand pc = (MortarCommand) i.getType().getConstructor().newInstance();
          Command c = i.getAnnotation(Command.class);

          if (!c.value().trim().isEmpty()) {
            pc.setCategory(c.value().trim());
          } else {
            pc.setCategory(getCategory());
          }

          p.add(pc);
        } catch (IllegalArgumentException | IllegalAccessException |
                 InstantiationException |
                 InvocationTargetException | NoSuchMethodException |
                 SecurityException e) {
          e.printStackTrace();
          e.printStackTrace();
        }
      }
    }

    p.sort(Comparator.comparing(MortarCommand::getNode));

    return p;
  }

  @Override
  public KList<String> getRequiredPermissions() {
    return requiredPermissions;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }
}
