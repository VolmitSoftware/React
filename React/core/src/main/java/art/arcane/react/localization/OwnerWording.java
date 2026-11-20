package art.arcane.react.localization;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites "plugin" wording to "mod" wording for mod loaders, and Bukkit as the server software to "the server". Identifiers such as {@code plugin-api},
 * {@code plugin.yml} and {@code {plugin}} placeholders keep their spelling.
 */
public final class OwnerWording {
  private static final Pattern PLUGIN = Pattern.compile("(?<![{<_/.:])(?:(?<=[&\u00A7][0-9a-fk-orA-FK-OR])|(?<!\\w))(plugin|Plugin|PLUGIN)(s|S)?(?![\\w}>\\-_/:]|\\.\\w)");
  private static final Pattern BUKKIT = Pattern.compile("(?:\\bthe )?\\bBukkit\\b(?![\\w\\-_/:]|\\.\\w)");
  private static final int MAX_CACHED = 4096;
  private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

  private OwnerWording() {
  }

  public static String forMods(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    String cached = CACHE.get(text);
    if (cached != null) {
      return cached;
    }
    String rewritten = rewrite(text);
    if (CACHE.size() >= MAX_CACHED) {
      CACHE.clear();
    }
    CACHE.put(text, rewritten);
    return rewritten;
  }

  private static String rewrite(String text) {
    return rewritePlugins(BUKKIT.matcher(text).replaceAll("the server"));
  }

  private static String rewritePlugins(String text) {
    Matcher matcher = PLUGIN.matcher(text);
    if (!matcher.find()) {
      return text;
    }
    StringBuilder result = new StringBuilder(text.length());
    int last = 0;
    do {
      result.append(text, last, matcher.start());
      String word = matcher.group(1);
      String noun = word.equals("PLUGIN") ? "MOD" : word.equals("Plugin") ? "Mod" : "mod";
      result.append(noun);
      if (matcher.group(2) != null) {
        result.append(matcher.group(2));
      }
      last = matcher.end();
    } while (matcher.find());
    result.append(text, last, text.length());
    return result.toString();
  }
}
