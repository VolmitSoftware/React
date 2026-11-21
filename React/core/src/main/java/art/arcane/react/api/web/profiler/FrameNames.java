package art.arcane.react.api.web.profiler;

import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Readable names for profiled frames. The platform maps runtime class and method names back to Mojang names where the
 * game runs obfuscated (SRG on Forge 1.20.1, intermediary on Fabric 1.20.1 and 1.21.1); nested, anonymous and lambda
 * classes map through their outer class. Mixin handlers merged into a target class read as {@code <mixin modid>.method}.
 */
public final class FrameNames {
  public static final FrameNames IDENTITY = new FrameNames(null, null);
  private static final Pattern MIXIN_BRIDGE = Pattern.compile("^mixinextras\\$bridge\\$(.+?)\\$\\d+$");
  private static final Pattern MIXIN_WITH_MOD = Pattern.compile("^[a-zA-Z]+\\$[a-z0-9]+\\$([a-z0-9_.\\-]+)\\$(.+)$");
  private static final Pattern MIXIN_UNIQUE = Pattern.compile("^md[0-9a-f]+\\$([a-z0-9_.\\-]+)\\$(.+)$");
  private static final Pattern MIXIN_BARE = Pattern.compile("^(?:handler|redirect|modify[a-zA-Z]*|localvar|wrapOperation|wrapWithCondition|wrapMethod|mixinextras)\\$[a-z]{3}\\d{3}\\$(.+)$");

  private final UnaryOperator<String> classes;
  private final UnaryOperator<String> methods;

  public FrameNames(UnaryOperator<String> classes, UnaryOperator<String> methods) {
    this.classes = classes;
    this.methods = methods;
  }

  public String readableClass(String className) {
    if (classes == null) {
      return className;
    }
    String mapped = classes.apply(className);
    if (!mapped.equals(className)) {
      return mapped;
    }
    int cut = className.lastIndexOf('$');
    if (cut <= 0) {
      return className;
    }
    return readableClass(className.substring(0, cut)) + className.substring(cut);
  }

  public String readableMethod(String methodName) {
    return methods == null ? methodName : methods.apply(methodName);
  }

  /** {@code Class.method}, or {@code <mixin modid>.method} for a mixin handler merged into its target. */
  public String display(String readableClass, String methodName) {
    String mixin = mixin(methodName);
    return mixin != null ? mixin : readableClass + '.' + readableMethod(methodName);
  }

  private String mixin(String methodName) {
    if (methodName.indexOf('$') < 0 || methodName.startsWith("lambda$")) {
      return null;
    }
    Matcher bridge = MIXIN_BRIDGE.matcher(methodName);
    if (bridge.matches()) {
      return "<mixin>." + readableMethod(bridge.group(1));
    }
    Matcher unique = MIXIN_UNIQUE.matcher(methodName);
    if (unique.matches()) {
      return "<mixin " + unique.group(1) + ">." + readableMethod(unique.group(2));
    }
    Matcher withMod = MIXIN_WITH_MOD.matcher(methodName);
    if (withMod.matches() && !withMod.group(1).equals("lambda")) {
      return "<mixin " + withMod.group(1) + ">." + readableMethod(withMod.group(2));
    }
    Matcher bare = MIXIN_BARE.matcher(methodName);
    if (bare.matches()) {
      return "<mixin>." + readableMethod(bare.group(1));
    }
    return null;
  }
}
