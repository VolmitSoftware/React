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
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.format.C;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.director.visual.DirectorVisualCommand;
import art.arcane.volmlib.util.director.visual.DirectorVisualCommand.DirectorVisualParameter;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;
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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a volume sender. A command sender with extra crap in it
 *
 * @author cyberpwn
 */
public class VolmitSender implements CommandSender {
  @Getter
  private static final KMap<String, String> helpCache = new KMap<>();
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

  public static <T> KList<T> paginate(KList<T> all, int linesPerPage, int page, AtomicBoolean hasNext) {
    int totalPages = (int) Math.ceil((double) all.size() / linesPerPage);
    page = page < 0 ? 0 : page >= totalPages ? totalPages - 1 : page;
    hasNext.set(page < totalPages - 1);
    KList<T> d = new KList<>();

    for (int i = linesPerPage * page; i < Math.min(all.size(), linesPerPage * (page + 1)); i++) {
      d.add(all.get(i));
    }

    return d;
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
    React.audiences.player(player()).showTitle(Title.title(
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
    React.audiences.player(player()).sendActionBar(createNoPrefixComponent(action));
  }

  public void sendActionNoProcessing(String action) {
    React.audiences.player(player()).sendActionBar(createNoPrefixComponentNoProcessing(action));
  }

  public void sendTitle(String subtitle, int i, int s, int o) {
    React.audiences.player(player()).showTitle(Title.title(
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
    if (!canUseCustomColors(this)) {
      String t = C.translateAlternateColorCodes('&', MiniMessage.miniMessage().stripTags(getTag() + message));
      return MiniMessage.miniMessage().deserialize(t);
    }

    String t = C.translateAlternateColorCodes('&', getTag() + message);
    String a = C.aura(t, spinh, spins, spinb);
    return MiniMessage.miniMessage().deserialize(a);
  }

  private Component createComponentRaw(String message) {
    if (!canUseCustomColors(this)) {
      String t = C.translateAlternateColorCodes('&', MiniMessage.miniMessage().stripTags(getTag() + message));
      return MiniMessage.miniMessage().deserialize(t);
    }

    String t = C.translateAlternateColorCodes('&', getTag() + message);
    return MiniMessage.miniMessage().deserialize(t);
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

    try {
      React.audiences.sender(s).sendMessage(createComponent(message));
    } catch (Throwable e) {
      System.out.println("=============================================");
      e.printStackTrace();
      if (e.getCause() != null) {
        e.getCause().printStackTrace();
      }
      System.out.println("=============================================");
      String t = C.translateAlternateColorCodes('&', getTag() + message);
      String a = C.aura(t, spinh, spins, spinb);

      React.debug("<NOMINI>Failure to parse " + a);
      s.sendMessage(C.translateAlternateColorCodes('&', getTag() + message));
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

    try {
      React.audiences.sender(s).sendMessage(createComponentRaw(message));
    } catch (Throwable e) {
      String t = C.translateAlternateColorCodes('&', getTag() + message);
      String a = C.aura(t, spinh, spins, spinb);

      System.out.println("=============================================");
      e.printStackTrace();
      if (e.getCause() != null) {
        e.getCause().printStackTrace();
      }
      System.out.println("=============================================");
      React.debug("<NOMINI>Failure to parse " + a);
      s.sendMessage(C.translateAlternateColorCodes('&', getTag() + message));
    }
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

  private String pickRandoms(int max, DirectorVisualCommand i) {
    KList<String> m = new KList<>();
    for (int ix = 0; ix < max; ix++) {
      m.add((i.isNode()
          ? (i.getNode().getParameters().isNotEmpty())
          ? "<#9bb8e8>✦ <#4f7fd6>"
          + i.getParentPath()
          + " <#67a2ff>"
          + i.getName() + " "
          + i.getNode().getParameters().shuffleCopy(RNG.r).convert((f)
              -> (f.isRequired() || RNG.r.b(0.5)
              ? "<#f2e15e>" + f.getNames().getRandom() + "="
              + "<#6f9cff>" + parameterExample(f)
              : ""))
          .toString(" ")
          : ""
          : ""));
    }

    return m.removeDuplicates().convert((iff) -> iff.replaceAll("\\Q  \\E", " ")).toString("\n");
  }

  public void sendHeader(String name, int overrideLength) {
    int len = overrideLength;
    int h = name.length() + 2;
    String s = Form.repeat(" ", len - h - 4);
    String si = Form.repeat("(", 3);
    String so = Form.repeat(")", 3);
    String sf = "[";
    String se = "]";

    if (name.trim().isEmpty()) {
      sendMessageRaw("<font:minecraft:uniform><strikethrough><gradient:#4f7fd6:#1f2f4f>" + sf + s + "<reset><font:minecraft:uniform><strikethrough><gradient:#1f2f4f:#4f7fd6>" + s + se);
    } else {
      sendMessageRaw("<font:minecraft:uniform><strikethrough><gradient:#4f7fd6:#1f2f4f>" + sf + s + si + "<reset> <gradient:#1f5f9f:#4f7fd6>" + name + "<reset> <font:minecraft:uniform><strikethrough><gradient:#1f2f4f:#4f7fd6>" + so + s + se);
    }
  }

  public void sendHeader(String name) {
    sendHeader(name, 40);
  }

  public void sendDirectorHelp(DirectorVisualCommand v) {
    sendDirectorHelp(v, 0);
  }

  public void sendDirectorHelp(DirectorVisualCommand v, int page) {
    if (!isPlayer()) {
      for (DirectorVisualCommand i : v.getNodes()) {
        sendDirectorHelpNode(i);
      }

      return;
    }

    sendMessageRaw("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n");

    if (v.getNodes().isNotEmpty()) {
      sendHeader(v.getPath() + (page > 0 ? (" {" + (page + 1) + "}") : ""));
      if (isPlayer() && v.getParent() != null) {
        sendMessageRaw("<hover:show_text:'" + "<#243a66>Click to go back to <#4f7fd6>" + Form.capitalize(v.getParent().getName()) + " Help" + "'><click:run_command:" + v.getParent().getPath() + "><font:minecraft:uniform><#7aa4ff>〈 Back</click></hover>");
      }

      AtomicBoolean next = new AtomicBoolean(false);
      for (DirectorVisualCommand i : paginate(v.getNodes(), 17, page, next)) {
        sendDirectorHelpNode(i);
      }

      String s = "";
      int l = 75 - (page > 0 ? 10 : 0) - (next.get() ? 10 : 0);

      if (page > 0) {
        s += "<hover:show_text:'<#7aa4ff>Click to go back to page " + page + "'><click:run_command:" + v.getPath() + " help=" + page + "><gradient:#1f5f9f:#4f7fd6>〈 Page " + page + "</click></hover><reset> ";
      }

      s += "<reset><font:minecraft:uniform><strikethrough><gradient:#2f4f8f:#5a8ee8>" + Form.repeat(" ", l) + "<reset>";

      if (next.get()) {
        s += " <hover:show_text:'<#7aa4ff>Click to go to back to page " + (page + 2) + "'><click:run_command:" + v.getPath() + " help=" + (page + 2) + "><gradient:#2f4f8f:#5a8ee8>Page " + (page + 2) + " ❭</click></hover>";
      }

      sendMessageRaw(s);
    } else {
      sendMessage(C.RED + "There are no subcommands in this group! Contact support, this is a command design issue!");
    }
  }

  public void sendDirectorHelpNode(DirectorVisualCommand i) {
    if (isPlayer() || s instanceof CommandDummy) {
      sendMessageRaw(helpCache.computeIfAbsent(i.getPath(), (k) -> {
        String newline = "<reset>\n";

        String realText = i.getPath() + " >" + "<#E6F2FF>⇀<gradient:#5a8ee8:#1f2f4f> " + i.getName();
        String hoverTitle = i.getNames().copy().reverse().convert((f) -> "<#67a2ff>" + f).toString(", ");
        String description = "<#7aa4ff>✎ <#9fc0ff><font:minecraft:uniform>" + i.getDescription();
        String usage = "<#5a8ee8>✒ <#2f3f66><font:minecraft:uniform>";

        String onClick;
        if (i.isNode()) {
          if (i.getNode().getParameters().isEmpty()) {
            usage += "There are no parameters. Click to type command.";
            onClick = "suggest_command";
          } else {
            usage += "Hover over all of the parameters to learn more.";
            onClick = "suggest_command";
          }
        } else {
          usage += "This is a command category. Click to run.";
          onClick = "run_command";
        }

        String suggestion = "";
        String suggestions = "";
        if (i.isNode() && i.getNode().getParameters().isNotEmpty()) {
          suggestion += newline + "<#9bb8e8>✦ <#4f7fd6><font:minecraft:uniform>" + i.getParentPath() + " <#67a2ff>" + i.getName() + " "
              + i.getNode().getParameters().convert((f) -> "<#6f9cff>" + parameterExample(f)).toString(" ");
          suggestions += newline + "<font:minecraft:uniform>" + pickRandoms(Math.min(i.getNode().getParameters().size() + 1, 5), i);
        }

        StringBuilder nodes = new StringBuilder();
        if (i.isNode()) {
          for (DirectorVisualParameter p : i.getNode().getParameters()) {
            String nTitle = "<gradient:#6f9cff:#7aa4ff>" + p.getName();
            String nHoverTitle = p.getNames().convert((ff) -> "<#67a2ff>" + ff).toString(", ");
            String nDescription = "<#7aa4ff>✎ <#9fc0ff><font:minecraft:uniform>" + p.getDescription();
            String nUsage;
            String fullTitle;
            if (p.isContextual() && (isPlayer() || s instanceof CommandDummy)) {
              fullTitle = "<#ffd36e>[" + nTitle + "<#ffd36e>] ";
              nUsage = "<#ff9900>➱ <#ffd36e><font:minecraft:uniform>The value may be derived from environment context.";
            } else if (p.isRequired()) {
              fullTitle = "<red>[" + nTitle + "<red>] ";
              nUsage = "<#CD5C5C>⚠ <#F08080><font:minecraft:uniform>This parameter is required.";
            } else if (p.hasDefault()) {
              fullTitle = "<#F7F7F7>⊰" + nTitle + "<#F7F7F7>⊱";
              nUsage = "<#1E90FF>✔ <#87CEEB><font:minecraft:uniform>Defaults to \"" + p.getParam().defaultValue() + "\" if undefined.";
            } else {
              fullTitle = "<#F7F7F7>⊰" + nTitle + "<#F7F7F7>⊱";
              nUsage = "<#5a7fd8>✔ <#87CEEB><font:minecraft:uniform>This parameter is optional.";
            }
            String type = "<#5f7fd4>✢ <#9ebeff><font:minecraft:uniform>This parameter is of type " + p.getType().getSimpleName() + ".";

            nodes
                .append("<hover:show_text:'")
                .append(nHoverTitle).append(newline)
                .append(nDescription).append(newline)
                .append(nUsage).append(newline)
                .append(type)
                .append("'>")
                .append(fullTitle)
                .append("</hover>");
          }
        } else {
          nodes = new StringBuilder("<gradient:#7fa8dd:#9bb8e8> - Category of Commands");
        }

        return "<hover:show_text:'" +
            hoverTitle + newline +
            description + newline +
            usage +
            suggestion +
            suggestions +
            "'>" +
            "<click:" +
            onClick +
            ":" +
            realText +
            "</click>" +
            "</hover>" +
            " " +
            nodes;
      }));
    } else {
      sendMessage(i.getPath());
    }
  }

  private String parameterExample(DirectorVisualParameter parameter) {
    if (parameter == null) {
      return "value";
    }

    String explicit = normalizeExample(parameter.example());
    if (!explicit.isEmpty()) {
      return explicit;
    }

    if (parameter.hasDefault() && parameter.getParam() != null) {
      String defaultValue = normalizeExample(parameter.getParam().defaultValue());
      if (!defaultValue.isEmpty()) {
        return defaultValue;
      }
    }

    return inferExample(parameter);
  }

  private String normalizeExample(String value) {
    if (value == null) {
      return "";
    }

    String normalized = value.trim();
    if (normalized.isEmpty()) {
      return "";
    }

    String lower = normalized.toLowerCase(Locale.ROOT);
    if (lower.equals("noexample")
        || lower.equals("no_example")
        || lower.equals("no-example")
        || lower.equals("none")
        || lower.equals("null")
        || lower.equals("n/a")) {
      return "";
    }

    return normalized;
  }

  private String inferExample(DirectorVisualParameter parameter) {
    Class<?> type = parameter.getType();
    if (type != null) {
      if (type.isEnum()) {
        Object[] constants = type.getEnumConstants();
        if (constants != null && constants.length > 0) {
          return constants[0].toString().toLowerCase(Locale.ROOT);
        }
      }

      if (type == Boolean.class || type == boolean.class) {
        return "true";
      }

      if (type == Integer.class || type == int.class) {
        return "10";
      }

      if (type == Long.class || type == long.class) {
        return "120";
      }

      if (type == Double.class || type == double.class || type == Float.class || type == float.class) {
        return "1.5";
      }

      if (type == Short.class || type == short.class || type == Byte.class || type == byte.class) {
        return "1";
      }
    }

    String names = parameter.getNames() == null ? "" : String.join(" ", parameter.getNames());
    String key = (parameter.getName() == null ? "" : parameter.getName()) + " " + names;
    String lowered = key.toLowerCase(Locale.ROOT);

    if (lowered.contains("world")) {
      return "world";
    }

    if (lowered.contains("player") || lowered.contains("user") || lowered.contains("target") || lowered.equals("u")) {
      return "player";
    }

    if (lowered.contains("radius") || lowered.contains("range") || lowered.contains("distance")) {
      return "8";
    }

    if (lowered.contains("seed")) {
      return "1337";
    }

    if (lowered.contains("dimension") || lowered.contains("dim") || lowered.contains("pack") || lowered.contains("type")) {
      return "overworld";
    }

    if (lowered.contains("url")) {
      return "https://example.com";
    }

    if (lowered.contains("path") || lowered.contains("file")) {
      return "config.toml";
    }

    if (lowered.contains("time") || lowered.contains("delay") || lowered.contains("interval") || lowered.contains("timeout")) {
      return "30";
    }

    if (lowered.contains("name")) {
      return "example";
    }

    if (lowered.contains("id")) {
      return "example-id";
    }

    return "value";
  }

}
