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

package art.arcane.react.util.format;


import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.util.plugin.VolmitSender;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.apache.commons.lang3.Validate;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Colors
 *
 * @author cyberpwn
 */
public enum C {
  /**
   * Represents black
   */
  BLACK('0', 0x00),
  /**
   * Represents dark blue
   */
  DARK_BLUE('1', 0x1),
  /**
   * Represents dark green
   */
  DARK_GREEN('2', 0x2),
  /**
   * Represents dark blue (aqua)
   */
  DARK_AQUA('3', 0x3),
  /**
   * Represents dark red
   */
  DARK_RED('4', 0x4),
  /**
   * Represents dark purple
   */
  DARK_PURPLE('5', 0x5),
  /**
   * Represents gold
   */
  GOLD('6', 0x6),
  /**
   * Represents gray
   */
  GRAY('7', 0x7),
  /**
   * Represents dark gray
   */
  DARK_GRAY('8', 0x8),
  /**
   * Represents blue
   */
  BLUE('9', 0x9),
  /**
   * Represents green
   */
  GREEN('a', 0xA),

  REACT("<#2118d9>", 'b', 0xB),

  /**
   * Represents aqua
   */
  AQUA('b', 0xB),
  /**
   * Represents red
   */
  RED('c', 0xC),
  /**
   * Represents light purple
   */
  LIGHT_PURPLE('d', 0xD),
  /**
   * Represents yellow
   */
  YELLOW('e', 0xE),
  /**
   * Represents white
   */
  WHITE('f', 0xF),
  /**
   * Represents magical characters that change around randomly
   */
  MAGIC("<obf>", 'k', 0x10, true),
  /**
   * Makes the text bold.
   */
  BOLD('l', 0x11, true),
  /**
   * Makes a line appear through the text.
   */
  STRIKETHROUGH('m', 0x12, true),
  /**
   * Makes the text appear underlined.
   */
  UNDERLINE("<underlined>", 'n', 0x13, true),
  /**
   * Makes the text italic.
   */
  ITALIC('o', 0x14, true),

  /**
   * Resets all previous chat colors or formats.
   */
  RESET('r', 0x15),


  ;
  /**
   * The special character which prefixes all chat colour codes. Use this if you
   * need to dynamically convert colour codes from your custom format.
   */
  public static final char COLOR_CHAR = '\u00A7';
  public final static C[] COLORCYCLE = new C[]{C.GOLD, C.YELLOW, C.GREEN, C.AQUA, C.LIGHT_PURPLE, C.AQUA, C.GREEN, C.YELLOW, C.GOLD, C.RED};
  /** Section-sign legacy text that keeps hex colours as {@code §x§r§r§g§g§b§b}, the form Paper and the mod adapters read back. */
  public static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
      .character(COLOR_CHAR)
      .hexColors()
      .useUnusualXRepeatedCharacterHexFormat()
      .build();
  private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)" + COLOR_CHAR + "[0-9A-FK-OR]");
  private final static C[] COLORS = new C[]{C.BLACK, C.DARK_BLUE, C.DARK_GREEN, C.DARK_AQUA, C.DARK_RED, C.DARK_PURPLE, C.GOLD, C.GRAY, C.DARK_GRAY, C.BLUE, C.GREEN, C.AQUA, C.RED, C.LIGHT_PURPLE, C.YELLOW, C.WHITE};
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private final static Map<Integer, C> BY_ID = new HashMap<>();
  private final static Map<Character, C> BY_CHAR = new HashMap<>();
  private final static Map<C, String> chatHexMap = new HashMap<>();

  static {
    chatHexMap.put(C.BLACK, "#000000");
    chatHexMap.put(C.DARK_BLUE, "#0000AA");
    chatHexMap.put(C.REACT, "#2118d9");
    chatHexMap.put(C.DARK_GREEN, "#00AA00");
    chatHexMap.put(C.DARK_AQUA, "#00AAAA");
    chatHexMap.put(C.DARK_RED, "#AA0000");
    chatHexMap.put(C.DARK_PURPLE, "#AA00AA");
    chatHexMap.put(C.GOLD, "#FFAA00");
    chatHexMap.put(C.GRAY, "#AAAAAA");
    chatHexMap.put(C.DARK_GRAY, "#555555");
    chatHexMap.put(C.BLUE, "#5555FF");
    chatHexMap.put(C.GREEN, "#55FF55");
    chatHexMap.put(C.AQUA, "#55FFFF");
    chatHexMap.put(C.RED, "#FF5555");
    chatHexMap.put(C.LIGHT_PURPLE, "#FF55FF");
    chatHexMap.put(C.YELLOW, "#FFFF55");
    chatHexMap.put(C.WHITE, "#FFFFFF");
  }

  static {
    for (C color : values()) {
      BY_ID.put(color.intCode, color);
      BY_CHAR.put(color.code, color);
    }
  }

  private final int intCode;
  private final char code;
  private final String token;
  private final boolean isFormat;
  private final String toString;

  C(char code, int intCode) {
    this("^", code, intCode, false);
  }

  C(String token, char code, int intCode) {
    this(token, code, intCode, false);
  }

  C(char code, int intCode, boolean isFormat) {
    this("^", code, intCode, false);
  }

  C(String token, char code, int intCode, boolean isFormat) {
    this.code = code;
    this.token = token.equalsIgnoreCase("^") ? "<" + name().toLowerCase(Locale.ROOT) + ">" : token;
    this.intCode = intCode;
    this.isFormat = isFormat;
    this.toString = new String(new char[]{COLOR_CHAR, code});
  }

  public static float[] spin(float[] c, int shift) {
    return new float[]{spin(c[0], shift), spinc(c[1], shift), spinc(c[2], shift)};
  }

  public static float[] spin(float[] c, int a, int b, int d) {
    return new float[]{spin(c[0], a), spinc(c[1], b), spinc(c[2], d)};
  }

  public static float spin(float c, int shift) {
    float g = ((((int) Math.floor(c * 360)) + shift) % 360) / 360F;
    return g < 0 ? 1f - g : g;
  }

  public static float spinc(float c, int shift) {
    float g = ((((int) Math.floor(c * 255)) + shift)) / 255F;
    return Math.max(0f, Math.min(g, 1f));
  }

  public static java.awt.Color spin(java.awt.Color c, int h, int s, int b) {
    float[] hsb = java.awt.Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
    hsb = spin(hsb, h, s, b);
    return java.awt.Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
  }

  public static String spinToHex(C color, int h, int s, int b) {
    return "#" + Integer.toHexString(spin(color.awtColor(), h, s, b).getRGB()).substring(2);
  }

  public static String mini(String s) {
    String msg = compress(s);
    StringBuilder b = new StringBuilder();
    boolean c = false;

    for (char i : msg.toCharArray()) {
      if (!c) {
        if (i == C.COLOR_CHAR) {
          c = true;
          continue;
        }
        b.append(i);
      } else {
        c = false;
        C o = C.getByChar(i);
        b.append(o.token);
      }
    }

    return b.toString();
  }

  public static String aura(String s, int hrad, int srad, int vrad) {
    return aura(s, hrad, srad, vrad, 0.3D);
  }

  public static String aura(String s, int hrad, int srad, int vrad, double pulse) {
    String msg = compress(s);
    StringBuilder b = new StringBuilder();
    boolean c = false;

    for (char i : msg.toCharArray()) {
      if (c) {
        c = false;

        C o = C.getByChar(i);

        if (hrad != 0 || srad != 0 || vrad != 0) {
          if (pulse > 0) {
            b.append(VolmitSender.pulse(spinToHex(o, hrad, srad, vrad), spinToHex(o, -hrad, -srad, -vrad), pulse));
          } else {
            b.append("<gradient:")
                .append(spinToHex(o, hrad, srad, vrad))
                .append(":")
                .append(spinToHex(o, -hrad, -srad, -vrad))
                .append(">");
          }
        } else {
          b.append(C.getByChar(i).token);
        }

        continue;
      }

      if (i == C.COLOR_CHAR) {
        c = true;
        continue;
      }

      b.append(i);
    }

    return b.toString();
  }

  public static String compress(String c) {
    return LEGACY.serialize(LEGACY.deserialize(c));
  }

  /**
   * Gets the color represented by the specified color code
   *
   * @param code Code to check
   * @return Associative {@link C} with the given code, or null if it
   * doesn't exist
   */
  public static C getByChar(char code) {
    try {
      C c = BY_CHAR.get(code);
      return c == null ? C.WHITE : c;
    } catch (Exception e) {
      verbose("Color lookup failed for code '" + code + "'");
      return C.WHITE;
    }
  }

  /**
   * Gets the color represented by the specified color code
   *
   * @param code Code to check
   * @return Associative {@link C} with the given code, or null if it
   * doesn't exist
   */
  public static C getByChar(String code) {
    try {
      Validate.notNull(code, "Code cannot be null");
      Validate.isTrue(code.length() > 0, "Code must have at least one char");

      return BY_CHAR.get(code.charAt(0));
    } catch (Exception e) {
      verbose("Color lookup failed for value '" + code + "'");
      return C.WHITE;
    }
  }

  /**
   * Strips the given message of all color codes
   *
   * @param input String to strip of color
   * @return A copy of the input string, without any coloring
   */
  public static String stripColor(final String input) {
    if (input == null) {
      return null;
    }

    return STRIP_COLOR_PATTERN.matcher(input).replaceAll("");
  }

  public static String strip(final String input) {
    if (input == null) {
      return null;
    }

    return MiniMessage.miniMessage().stripTags(stripColor(input));
  }

  @SuppressWarnings("unlikely-arg-type")
  public static String chatToHex(C clr) {
    if (chatHexMap.containsKey(clr)) {
      return chatHexMap.get(clr);
    }

    return "#000000";
  }

  /**
   * Translates a string using an alternate color code character into a string
   * that uses the internal ChatColor.COLOR_CODE color code character. The
   * alternate color code character will only be replaced if it is immediately
   * followed by 0-9, A-F, a-f, K-O, k-o, R or r.
   *
   * @param altColorChar    The alternate color code character to replace. Ex:
   *                        {@literal &}
   * @param textToTranslate Text containing the alternate color code character.
   * @return Text containing the ChatColor.COLOR_CODE color code character.
   */
  public static String translateAlternateColorCodes(char altColorChar, String textToTranslate) {
    if (textToTranslate == null) {
      return null;
    }

    char[] b = textToTranslate.toCharArray();
    for (int i = 0; i < b.length - 1; i++) {
      if (b[i] == altColorChar && "0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(b[i + 1]) > -1) {
        b[i] = C.COLOR_CHAR;
        b[i + 1] = Character.toLowerCase(b[i + 1]);
      }
    }
    return new String(b);
  }

  public static C fromItemMeta(byte c) {
    for (C i : C.values()) {
      if (i.getItemMeta() == c) {
        return i;
      }
    }

    return null;
  }

  public static C randomColor() {
    return COLORS[(int) (Math.random() * (COLORS.length - 1))];
  }

  /**
   * Gets the ChatColors used at the end of the given input string.
   *
   * @param input Input string to retrieve the colors from.
   * @return Any remaining ChatColors to pass onto the next line.
   */
  public static String getLastColors(String input) {
    StringBuilder result = new StringBuilder();
    int length = input.length();

    // Search backwards from the end as it is faster
    for (int index = length - 1; index > -1; index--) {
      char section = input.charAt(index);
      if (section == COLOR_CHAR && index < length - 1) {
        char c = input.charAt(index + 1);
        C color = getByChar(c);

        if (color != null) {
          result.insert(0, color);

          // Once we find a color or reset we can stop searching
          if (color.isColor() || color.equals(RESET)) {
            break;
          }
        }
      }
    }

    return result.toString();
  }

  /**
   * Gets the char value associated with this color
   *
   * @return A char value of this color code
   */
  public char getChar() {
    return code;
  }

  @Override
  public String toString() {
    return intCode == -1 ? token : toString;
  }

  public String hex() {
    return chatToHex(this);
  }

  public java.awt.Color awtColor() {
    return java.awt.Color.decode(hex());
  }

  /**
   * Checks if this code is a format code as opposed to a color code.
   *
   * @return whether this ChatColor is a format code
   */
  public boolean isFormat() {
    return isFormat;
  }

  /**
   * Checks if this code is a color code as opposed to a format code.
   *
   * @return whether this ChatColor is a color code
   */
  public boolean isColor() {
    return !isFormat && this != RESET;
  }

  public byte getMeta() {
    return switch (this) {
      case AQUA -> (byte) 11;
      case BLACK -> (byte) 0;
      case BLUE, DARK_AQUA -> (byte) 9;
      case BOLD, UNDERLINE, STRIKETHROUGH, RESET, MAGIC, ITALIC -> (byte) -1;
      case DARK_BLUE -> (byte) 1;
      case DARK_GRAY -> (byte) 8;
      case DARK_GREEN -> (byte) 2;
      case DARK_PURPLE -> (byte) 5;
      case DARK_RED -> (byte) 4;
      case GOLD -> (byte) 6;
      case GRAY -> (byte) 7;
      case GREEN -> (byte) 10;
      case LIGHT_PURPLE -> (byte) 13;
      case RED -> (byte) 12;
      case YELLOW -> (byte) 14;
      default -> (byte) 15;
    };
  }

  public byte getItemMeta() {
    return switch (this) {
      case AQUA, DARK_AQUA -> (byte) 9;
      case BLUE -> (byte) 3;
      case BOLD, UNDERLINE, RESET, STRIKETHROUGH, MAGIC, ITALIC -> (byte) -1;
      case DARK_BLUE -> (byte) 11;
      case DARK_GRAY -> (byte) 7;
      case DARK_GREEN -> (byte) 13;
      case DARK_PURPLE -> (byte) 10;
      case DARK_RED, RED -> (byte) 14;
      case GOLD, YELLOW -> (byte) 4;
      case GRAY -> (byte) 8;
      case GREEN -> (byte) 5;
      case LIGHT_PURPLE -> (byte) 2;
      case WHITE -> (byte) 0;
      default -> (byte) 15;
    };
  }

  private static void verbose(String message) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.verbose(message);
    }
  }
}
