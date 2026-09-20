package art.arcane.react.nms;

import art.arcane.react.React;
import org.bukkit.Bukkit;

import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.monitor.NativeMonitor;

public final class NmsBridges {
    private static volatile NativeMonitor bridge;
    private static volatile boolean attempted;
    private static volatile String failureReason = "";

    private NmsBridges() {}

    public static NativeMonitor get() {
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

    private static NativeMonitor resolve() {
        try {
            NativeMonitor resolved = NativeAdapters.find(NativeMonitor.class).orElse(null);
            if (resolved == null) {
                failureReason = "No native monitoring backend for " + Bukkit.getMinecraftVersion();
                React.info(failureReason);
            } else {
                React.info("Native monitoring backend active: " + resolved.version());
            }
            return resolved;
        } catch (Throwable failure) {
            failureReason = failure.getClass().getSimpleName() + ": " + failure.getMessage();
            React.reportError("Native monitoring backend failed to load", failure);
            return null;
        }
    }

    public static boolean onBundledVersion() {
        return get() != null;
    }
}
