package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.content.tweak.ShorthandCommandService;
import art.arcane.react.content.tweak.TweakShorthands;
import art.arcane.react.platform.ShorthandHost;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;

public final class BukkitShorthandHost implements ShorthandHost {
  @Override
  public Subscription register(TweakShorthands settings) {
    ShorthandCommandService service = ShorthandCommandService.create(settings);
    try {
      service.register();
    } catch (RuntimeException | Error failure) {
      try {
        service.unregister();
      } catch (Throwable cleanup) {
        React.reportError("Failed to unregister shorthand commands: " + cleanup.getMessage(), cleanup);
      }
      throw failure;
    }
    return service::unregister;
  }

  @Override
  public boolean runOnCommandThread(Runnable operation) {
    boolean correctThread = J.isFoliaThreading()
        ? Bukkit.isGlobalTickThread()
        : Bukkit.isPrimaryThread();
    if (correctThread) {
      operation.run();
      return true;
    }
    return FoliaScheduler.runGlobal(React.instance, operation);
  }
}
