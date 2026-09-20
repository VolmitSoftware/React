-keep class art.arcane.react.api.** { *; }
-keep class art.arcane.react.util.arcane.volmlib.integration.** { *; }
-keepclassmembers class * extends org.bukkit.event.Event {
    public static org.bukkit.event.HandlerList getHandlerList();
    public org.bukkit.event.HandlerList getHandlers();
}
-keep class art.arcane.react.util.arcane.volmlib.nativelib.** { *; }

-keep class art.arcane.volmlib.nativelib.**.scoreboard.NativeScoreboardPackets { public <init>(); }
