package art.arcane.react.nms;

import art.arcane.react.React;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NmsBridges {
    private static volatile NmsBridge bridge;
    private static volatile boolean attempted;
    private static volatile String failureReason = "";

    private NmsBridges() {}

    public static NmsBridge get() {
        if (attempted) {
            return bridge;
        }
        synchronized (NmsBridges.class) {
            if (attempted) {
                return bridge;
            }
            attempted = true;
            bridge = resolve();
            return bridge;
        }
    }

    public static String failureReason() {
        return failureReason;
    }

    public static void reset() {
        synchronized (NmsBridges.class) {
            if (bridge != null) {
                bridge.uninstallFurnaceTickHook();
                bridge.uninstallBrewingTickHook();
                bridge.uninstallFallingBlockTickHook();
                bridge.uninstallExplosionHook();
                bridge.uninstallExplosionPacketSuppressor();
                bridge.uninstallHopperTickHook();
            }
            bridge = null;
            attempted = false;
            failureReason = "";
        }
    }

    private static NmsBridge resolve() {
        String detected = detectVersionTag();
        if (detected.isEmpty()) {
            failureReason = "Could not detect server NMS version tag";
            React.info("NMS bridge disabled: " + failureReason);
            return null;
        }

        List<String> candidates = List.of(
                "art.arcane.react.nms." + detected + ".NmsBridgeImpl"
        );

        for (String candidate : candidates) {
            try {
                Class<?> implClass = Class.forName(candidate);
                Object instance = implClass.getDeclaredConstructor().newInstance();
                if (instance instanceof NmsBridge resolved) {
                    React.info("NMS bridge active: " + candidate);
                    return resolved;
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable t) {
                failureReason = t.getClass().getSimpleName() + ": " + t.getMessage();
                React.reportError("NMS bridge load failed for " + candidate + " — " + failureReason, t);
                return null;
            }
        }

        failureReason = "No matching NMS bridge implementation for tag '" + detected + "'";
        React.info(failureReason + " — features remain measurement-only");
        return null;
    }

    private static final Pattern MC_VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    public static boolean onBundledVersion() {
        return !detectVersionTag().isEmpty();
    }

    private static String detectVersionTag() {
        try {
            String bukkitVersion = Bukkit.getBukkitVersion();
            if (bukkitVersion != null && !bukkitVersion.isEmpty()) {
                String tag = tagFor(extractMcVersion(bukkitVersion));
                if (!tag.isEmpty()) {
                    return tag;
                }
            }
            String serverVersion = Bukkit.getVersion();
            if (serverVersion != null && !serverVersion.isEmpty()) {
                String tag = tagFor(extractMcVersion(serverVersion));
                if (!tag.isEmpty()) {
                    return tag;
                }
            }
            return "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    static String extractMcVersion(String raw) {
        Matcher matcher = MC_VERSION_PATTERN.matcher(raw);
        if (!matcher.find()) {
            return "";
        }
        String major = matcher.group(1);
        String minor = matcher.group(2);
        String patch = matcher.group(3);
        if ("26".equals(major) && "2".equals(minor)) {
            return "26.2";
        }
        if (patch == null) {
            return major + "." + minor;
        }
        return major + "." + minor + "." + patch;
    }

    static String tagFor(String mcVersion) {
        if (mcVersion == null || mcVersion.isEmpty()) {
            return "";
        }
        return switch (mcVersion) {
            case "26.2", "26.1.2", "1.21.11" -> "v26_2_R1";
            default -> "";
        };
    }
}
