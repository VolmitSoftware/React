package art.arcane.react.api.web;

import art.arcane.react.platform.WebHost;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Applies {@code web.toml} when it is saved, where no platform hotload covers it (proxies and mod servers): the file is
 * checked every second, read through the host like at startup, and applied to the running server. A file that cannot be
 * read or validated leaves the current settings in place.
 */
public final class WebConfigWatcher {
  private static final long CHECK_INTERVAL_MS = 1_000L;
  private final File file;
  private final WebHost host;
  private final ReactWebServer web;
  private ScheduledExecutorService scheduler;
  private long stamp;

  public WebConfigWatcher(File file, WebHost host, ReactWebServer web) {
    this.file = file;
    this.host = host;
    this.web = web;
  }

  public synchronized void start() {
    if (scheduler != null) {
      return;
    }
    stamp = stampOf(file);
    ScheduledExecutorService created = Executors.newSingleThreadScheduledExecutor((Runnable task) -> {
      Thread thread = new Thread(task, "react-web-config");
      thread.setDaemon(true);
      return thread;
    });
    scheduler = created;
    created.scheduleWithFixedDelay(this::check, CHECK_INTERVAL_MS, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
  }

  public synchronized void stop() {
    if (scheduler != null) {
      scheduler.shutdownNow();
      scheduler = null;
    }
  }

  void check() {
    long current = stampOf(file);
    if (current == stamp || !file.isFile()) {
      return;
    }
    stamp = current;
    try {
      Optional<WebConfiguration> loaded = host.loadConfiguration();
      stamp = stampOf(file);
      if (loaded.isEmpty()) {
        return;
      }
      if (web.applyHotloadSnapshot(loaded.get())) {
        WebLog.info("Applied web.toml.");
      } else {
        WebLog.warn("web.toml was read, but React Web could not start with it; the previous settings stay active.");
      }
    } catch (Exception failure) {
      WebLog.warn("Kept the current web settings; web.toml could not be applied: " + failure.getMessage());
    }
  }

  private static long stampOf(File file) {
    return file.lastModified() * 31L + file.length();
  }
}
