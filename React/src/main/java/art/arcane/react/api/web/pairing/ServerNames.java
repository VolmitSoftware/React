package art.arcane.react.api.web.pairing;

import java.io.File;
import java.util.regex.Pattern;

/** The name React Web shows for a server: {@code serverName} in web.toml, else the MOTD, else the server folder. */
public final class ServerNames {
  static final String VANILLA_MOTD = "A Minecraft Server", UNNAMED = "Minecraft server";
  public static final int MAXIMUM_LENGTH = 64;
  private static final Pattern HEX = Pattern.compile("(?i)[&§]x([&§][0-9a-f]){6}|[&§]#[0-9a-f]{6}|<#[0-9a-f]{6}>");
  private static final Pattern CODES = Pattern.compile("(?i)[&§][0-9a-fk-or]");
  private static final Pattern TAGS = Pattern.compile("</?[a-z_#!][^<>]{0,64}>", Pattern.CASE_INSENSITIVE);
  private static final Pattern SPACE = Pattern.compile("\\s+");

  private ServerNames() {
  }

  /** {@code configured} when set; otherwise the MOTD's first line unless it is empty or the vanilla default; else the folder name. */
  public static String resolve(String configured, String motd, File folder) {
    String set = plain(configured);
    if (!set.isEmpty()) {
      return set;
    }
    String line = plain(motd == null ? "" : motd.lines().findFirst().orElse(""));
    if (!line.isEmpty() && !line.equalsIgnoreCase(VANILLA_MOTD)) {
      return line;
    }
    File named = folder == null ? null : folder.getAbsoluteFile().toPath().normalize().toFile();
    String folderName = named == null ? "" : plain(named.getName());
    return folderName.isEmpty() ? UNNAMED : folderName;
  }

  /** Plain text: colour codes, hex colours and MiniMessage tags removed, whitespace collapsed, at most 64 characters. */
  public static String plain(String text) {
    if (text == null) {
      return "";
    }
    String stripped = TAGS.matcher(CODES.matcher(HEX.matcher(text).replaceAll("")).replaceAll("")).replaceAll("");
    String collapsed = SPACE.matcher(stripped).replaceAll(" ").strip();
    return collapsed.length() > MAXIMUM_LENGTH ? collapsed.substring(0, MAXIMUM_LENGTH).strip() : collapsed;
  }
}
