-keep class art.arcane.react.api.** { *; }
-keep class art.arcane.react.util.arcane.volmlib.integration.** { *; }
-keepclassmembers class * extends org.bukkit.event.Event {
    public static org.bukkit.event.HandlerList getHandlerList();
    public org.bukkit.event.HandlerList getHandlers();
}
