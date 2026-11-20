package art.arcane.react.proxy.bungee;

import art.arcane.react.proxy.ProxyLibraries;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.logging.Level;

/** BungeeCord entry point. Loads React's libraries, then runs React for the proxy and every server behind it. */
public final class ReactBungeePlugin extends Plugin {
  private BungeeReact react;

  @Override
  public void onEnable() {
    try {
      ProxyLibraries.load(getDataFolder().toPath(), null, getLogger());
      react = new BungeeReact(this);
      react.start();
    } catch (RuntimeException | LinkageError failure) {
      getLogger().log(Level.SEVERE, "React could not start on BungeeCord", failure);
    }
  }

  @Override
  public void onDisable() {
    if (react != null) {
      react.stop();
      react = null;
    }
  }
}
