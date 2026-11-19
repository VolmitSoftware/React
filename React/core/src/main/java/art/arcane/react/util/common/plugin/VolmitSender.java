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

import art.arcane.react.platform.ReactCommandSender;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.util.format.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.plugin.ComponentText;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;

import java.util.Objects;

/**
 * A command sender with React's tag and custom colors on top of the platform sender.
 *
 * @author cyberpwn
 */
public class VolmitSender {
  private final ReactCommandSender s;
  public boolean useConsoleCustomColors = true;
  public boolean useCustomColorsIngame = true;
  public int spinh = -20;
  public int spins = 7;
  public int spinb = 8;
  private String tag;

  @Getter
  @Setter
  private String command;

  public VolmitSender(ReactCommandSender s) {
    this(s, "");
  }

  public VolmitSender(ReactCommandSender s, String tag) {
    this.tag = tag;
    this.s = Objects.requireNonNull(s, "sender");
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

  public String getTag() {
    return tag;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }

  public boolean isPlayer() {
    return s.isPlayer();
  }

  /**
   * The player behind this sender, or null for a console.
   */
  public ReactPlayer player() {
    return s.player().orElse(null);
  }

  public ReactCommandSender getS() {
    return s;
  }

  public boolean hasPermission(String name) {
    return s.hasPermission(name);
  }

  public boolean isOp() {
    return s.isOp();
  }

  public String getName() {
    return s.name();
  }

  public void hr() {
    s.send(ComponentText.literal("========================================================"));
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public boolean canUseCustomColors(VolmitSender volmitSender) {
    return volmitSender.isPlayer() ? useCustomColorsIngame : useConsoleCustomColors;
  }

  private ComponentText createComponent(String message) {
    return canUseCustomColors(this)
        ? ComponentText.markup(createMiniMessage(message))
        : ComponentText.legacyOnly(getTag() + message);
  }

  private String createMiniMessage(String message) {
    String t = C.translateAlternateColorCodes('&', getTag() + message);
    return C.aura(t, spinh, spins, spinb);
  }

  private ComponentText createComponentRaw(String message) {
    return canUseCustomColors(this)
        ? ComponentText.markup(createMiniMessageRaw(message))
        : ComponentText.legacyOnly(getTag() + message);
  }

  private String createMiniMessageRaw(String message) {
    return C.translateAlternateColorCodes('&', getTag() + message);
  }

  public void sendMessage(String message) {
    if ((!useCustomColorsIngame && s.isPlayer()) || !useConsoleCustomColors) {
      s.send(ComponentText.legacyOnly(getTag() + message));
      return;
    }

    if (message.contains("<NOMINI>")) {
      s.send(ComponentText.legacy(getTag() + message.replace("<NOMINI>", "")));
      return;
    }

    s.send(createComponent(message));
  }

  public void sendComponent(Component component) {
    if (component != null) {
      s.send(ComponentText.component(component));
    }
  }

  public void sendMessageBasic(String message) {
    s.send(ComponentText.legacy(getTag() + message));
  }

  public void sendMessageRaw(String message) {
    if ((!useCustomColorsIngame && s.isPlayer()) || !useConsoleCustomColors) {
      s.send(ComponentText.legacyOnly(message));
      return;
    }

    if (message.contains("<NOMINI>")) {
      s.send(ComponentText.legacy(message.replace("<NOMINI>", "")));
      return;
    }

    s.send(createComponentRaw(message));
  }

  public void sendMessage(String[] messages) {
    for (String str : messages) {
      sendMessage(str);
    }
  }
}
