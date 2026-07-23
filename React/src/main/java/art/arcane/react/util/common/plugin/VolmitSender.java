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


import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.format.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a volume sender. A command sender with extra crap in it
 *
 * @author cyberpwn
 */
public class VolmitSender implements CommandSender {
  private final CommandSender s;
  public boolean useConsoleCustomColors = true;
  public boolean useCustomColorsIngame = true;
  public int spinh = -20;
  public int spins = 7;
  public int spinb = 8;
  private String tag;
  @Getter
  @Setter
  private String command;

  /**
   * Wrap a command sender
   *
   * @param s the command sender
   */
  public VolmitSender(CommandSender s) {
    tag = "";
    this.s = s;
  }

  public VolmitSender(CommandSender s, String tag) {
    this.tag = tag;
    this.s = s;
  }

  public static long getTick() {
    return M.ms() / 16;
  }

  public static String pulse(String colorA, String colorB, double speed) {
    return "<gradient:" + colorA + ":" + colorB + ":" + pulse(speed) + ">";
  }

  public static String pulse(double speed) {
    return Form.f(invertSpread((((getTick() * 15D * speed) % 1000D) / 1000D)), 3).replaceAll("\\Q,\\E", ".").replaceAll("\\Q?\\E", "-");
  }

  public static double invertSpread(double v) {
    return ((1D - v) * 2D) - 1D;
  }

  /**
   * Get the command tag
   *
   * @return the command tag
   */
  public String getTag() {
    return tag;
  }

  /**
   * Set a command tag (prefix for sendMessage)
   *
   * @param tag the tag
   */
  public void setTag(String tag) {
    this.tag = tag;
  }

  /**
   * Is this sender a player?
   *
   * @return true if it is
   */
  public boolean isPlayer() {
    return getS() instanceof Player;
  }

  /**
   * Force cast to player (be sure to check first)
   *
   * @return a casted player
   */
  public Player player() {
    return (Player) getS();
  }

  /**
   * Get the origin sender this object is wrapping
   *
   * @return the command sender
   */
  public CommandSender getS() {
    return s;
  }

  @Override
  public Component name() {
    return s.name();
  }

  @Override
  public boolean isPermissionSet(String name) {
    return s.isPermissionSet(name);
  }

  @Override
  public boolean isPermissionSet(Permission perm) {
    return s.isPermissionSet(perm);
  }

  @Override
  public boolean hasPermission(String name) {
    return s.hasPermission(name);
  }

  @Override
  public boolean hasPermission(Permission perm) {
    return s.hasPermission(perm);
  }

  @Override
  public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
    return s.addAttachment(plugin, name, value);
  }

  @Override
  public PermissionAttachment addAttachment(Plugin plugin) {
    return s.addAttachment(plugin);
  }

  @Override
  public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
    return s.addAttachment(plugin, name, value, ticks);
  }

  @Override
  public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
    return s.addAttachment(plugin, ticks);
  }

  @Override
  public void removeAttachment(PermissionAttachment attachment) {
    s.removeAttachment(attachment);
  }

  @Override
  public void recalculatePermissions() {
    s.recalculatePermissions();
  }

  @Override
  public Set<PermissionAttachmentInfo> getEffectivePermissions() {
    return s.getEffectivePermissions();
  }

  @Override
  public boolean isOp() {
    return s.isOp();
  }

  @Override
  public void setOp(boolean value) {
    s.setOp(value);
  }

  public void hr() {
    s.sendMessage("========================================================");
  }

  public void sendTitle(String title, String subtitle, int i, int s, int o) {
    player().showTitle(Title.title(
        createComponent(title),
        createComponent(subtitle),
        Title.Times.times(Duration.ofMillis(i), Duration.ofMillis(s), Duration.ofMillis(o))));
  }

  public void sendProgress(double percent, String thing) {
    //noinspection IfStatementWithIdenticalBranches
    if (percent < 0) {
      int l = 44;
      int g = (int) (1D * l);
      sendTitle(C.REACT + thing + " ", 0, 500, 250);
      sendActionNoProcessing("" + "" + pulse("#4f7fd6", "#1f2f4f", 1D) + "<underlined> " + Form.repeat(" ", g) + "<reset>" + Form.repeat(" ", l - g));
    } else {
      int l = 44;
      int g = (int) (percent * l);
      sendTitle(C.REACT + thing + " " + C.BLUE + "<font:minecraft:uniform>" + Form.pc(percent, 0), 0, 500, 250);
      sendActionNoProcessing("" + "" + pulse("#4f7fd6", "#1f2f4f", 1D) + "<underlined> " + Form.repeat(" ", g) + "<reset>" + Form.repeat(" ", l - g));
    }
  }

  public void sendAction(String action) {
    player().sendActionBar(createNoPrefixComponent(action));
  }

  public void sendActionNoProcessing(String action) {
    player().sendActionBar(createNoPrefixComponentNoProcessing(action));
  }

  public void sendTitle(String subtitle, int i, int s, int o) {
    player().showTitle(Title.title(
        createNoPrefixComponent(" "),
        createNoPrefixComponent(subtitle),
        Title.Times.times(Duration.ofMillis(i), Duration.ofMillis(s), Duration.ofMillis(o))));
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public boolean canUseCustomColors(VolmitSender volmitSender) {
    return volmitSender.isPlayer() ? useCustomColorsIngame : useConsoleCustomColors;
  }

  private Component createNoPrefixComponent(String message) {
    if (!canUseCustomColors(this)) {
      String t = C.translateAlternateColorCodes('&', MiniMessage.miniMessage().stripTags(message));
      return MiniMessage.miniMessage().deserialize(t);
    }

    String t = C.translateAlternateColorCodes('&', message);
    String a = C.aura(t, spinh, spins, spinb, 0.36);
    return MiniMessage.miniMessage().deserialize(a);
  }

  private Component createNoPrefixComponentNoProcessing(String message) {
    return MiniMessage.builder().postProcessor(c -> c).build().deserialize(message);
  }

  private Component createComponent(String message) {
    return MiniMessage.miniMessage().deserialize(createMiniMessage(message));
  }

  private String createMiniMessage(String message) {
    if (!canUseCustomColors(this)) {
      String t = C.translateAlternateColorCodes('&', MiniMessage.miniMessage().stripTags(getTag() + message));
      return t;
    }

    String t = C.translateAlternateColorCodes('&', getTag() + message);
    return C.aura(t, spinh, spins, spinb);
  }

  private Component createComponentRaw(String message) {
    return MiniMessage.miniMessage().deserialize(createMiniMessageRaw(message));
  }

  private String createMiniMessageRaw(String message) {
    if (!canUseCustomColors(this)) {
      String t = C.translateAlternateColorCodes('&', MiniMessage.miniMessage().stripTags(getTag() + message));
      return t;
    }

    String t = C.translateAlternateColorCodes('&', getTag() + message);
    return t;
  }

  private boolean deliverRichMessage(String miniMessage) {
    try {
      s.getClass().getMethod("sendRichMessage", String.class).invoke(s, miniMessage);
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  public <T> void showWaiting(String passive, CompletableFuture<T> f) {
    AtomicInteger v = new AtomicInteger();
    AtomicReference<T> g = new AtomicReference<>();
    v.set(J.ar(() -> {
      if (f.isDone() && g.get() != null) {
        J.car(v.get());
        sendAction(" ");
        return;
      }

      sendProgress(-1, passive);
    }, 0));
    J.a(() -> {
      try {
        g.set(f.get());
      } catch (InterruptedException e) {
        e.printStackTrace();
      } catch (ExecutionException e) {
        e.printStackTrace();
      }
    });

  }

  @Override
  public void sendMessage(String message) {
    if (s instanceof CommandDummy) {
      return;
    }

    if ((!useCustomColorsIngame && s instanceof Player) || !useConsoleCustomColors) {
      s.sendMessage(C.translateAlternateColorCodes('&', getTag() + message));
      return;
    }

    if (message.contains("<NOMINI>")) {
      s.sendMessage(C.translateAlternateColorCodes('&', getTag() + message.replaceAll("\\Q<NOMINI>\\E", "")));
      return;
    }

    if (deliverRichMessage(createMiniMessage(message))) {
      return;
    }

    s.sendMessage(C.translateAlternateColorCodes('&', getTag() + message));
  }

  public void sendComponent(Component component) {
    if (component != null && !(s instanceof CommandDummy)) {
      s.sendMessage(component);
    }
  }

  public void sendMessageBasic(String message) {
    s.sendMessage(C.translateAlternateColorCodes('&', getTag() + message));
  }

  public void sendMessageRaw(String message) {
    if (s instanceof CommandDummy) {
      return;
    }

    if ((!useCustomColorsIngame && s instanceof Player) || !useConsoleCustomColors) {
      s.sendMessage(C.translateAlternateColorCodes('&', message));
      return;
    }

    if (message.contains("<NOMINI>")) {
      s.sendMessage(message.replaceAll("\\Q<NOMINI>\\E", ""));
      return;
    }

    if (deliverRichMessage(createMiniMessageRaw(message))) {
      return;
    }

    s.sendMessage(C.translateAlternateColorCodes('&', getTag() + message));
  }

  @Override
  public void sendMessage(String[] messages) {
    for (String str : messages)
      sendMessage(str);
  }

  @Override
  public void sendMessage(UUID uuid, String message) {
    sendMessage(message);
  }

  @Override
  public void sendMessage(UUID uuid, String[] messages) {
    sendMessage(messages);
  }

  @Override
  public Server getServer() {
    return s.getServer();
  }

  @Override
  public String getName() {
    return s.getName();
  }

  @Override
  public Spigot spigot() {
    return s.spigot();
  }


}
