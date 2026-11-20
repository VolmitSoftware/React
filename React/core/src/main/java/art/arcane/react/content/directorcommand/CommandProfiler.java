package art.arcane.react.content.directorcommand;

import art.arcane.react.api.web.ReactWebServer;
import art.arcane.react.api.web.profiler.ProfileDtos;
import art.arcane.react.api.web.profiler.SamplingProfiler;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.platform.CommandHost;
import art.arcane.react.util.director.DirectorHosts;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** {@code /react profiler start|stop|status}, the sampling profiler that React Web's profiler page reads. */
@Director(
    name = "profiler",
    aliases = {"prof"},
    origin = DirectorOrigin.BOTH,
    description = "Sample what the server is running",
    descriptionKey = "command.description.profiler"
)
public class CommandProfiler implements DirectorExecutor {
  private static final int TOP_OWNERS = 5;

  @Director(
      name = "start",
      description = "Start the profiler",
      descriptionKey = "command.description.profiler.start"
  )
  public void start(
      @Param(name = "interval", description = "Milliseconds between samples, from 1 to 1000", descriptionKey = "command.parameter.profiler.interval", defaultValue = "0", aliases = {"i"})
      int interval,
      @Param(name = "threads", description = "server or all", descriptionKey = "command.parameter.profiler.threads", defaultValue = "server", aliases = {"t"})
      String threads,
      @Param(name = "seconds", description = "Seconds before it stops, from 1 to 3600", descriptionKey = "command.parameter.profiler.seconds", defaultValue = "0", aliases = {"s"})
      int seconds
  ) {
    VolmitSender sender = sender();
    Optional<ReactWebServer> web = web(sender, "profiler start");
    if (web.isEmpty()) {
      return;
    }
    String mode = threads == null ? SamplingProfiler.SERVER : threads.trim().toLowerCase(Locale.ROOT);
    if (!mode.equals(SamplingProfiler.SERVER) && !mode.equals(SamplingProfiler.ALL)) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.PROFILER_INVALID_THREADS);
      return;
    }
    int chosenInterval = interval > 0 ? interval : web.get().getConfig().getProfilerIntervalMs();
    int chosenSeconds = seconds > 0 ? seconds : web.get().getConfig().getProfilerMaxSeconds();
    ProfileDtos.State state = web.get().profiler().start(chosenInterval, mode, chosenSeconds);
    long remaining = state.stopsAt() == null ? 0 : Math.max(0, (state.stopsAt() - System.currentTimeMillis()) / 1000L);
    ReactLanguage.sendPrefixed(sender, CommandMessages.PROFILER_STARTED,
        MessageArgument.untrusted("value", state.intervalMs()),
        MessageArgument.untrusted("mode", state.threads()),
        MessageArgument.untrusted("seconds", remaining));
  }

  @Director(
      name = "stop",
      description = "Stop the profiler",
      descriptionKey = "command.description.profiler.stop"
  )
  public void stop() {
    VolmitSender sender = sender();
    Optional<ReactWebServer> web = web(sender, "profiler stop");
    if (web.isEmpty()) {
      return;
    }
    ProfileDtos.State state = web.get().profiler().stop();
    ReactLanguage.sendPrefixed(sender, CommandMessages.PROFILER_STOPPED,
        MessageArgument.untrusted("count", state.samples()),
        MessageArgument.untrusted("duration", Form.duration(state.durationMs(), 0)));
  }

  @Director(
      name = "status",
      description = "Show the profiler state and the busiest owners",
      descriptionKey = "command.description.profiler.status"
  )
  public void status() {
    VolmitSender sender = sender();
    Optional<ReactWebServer> web = web(sender, "profiler status");
    if (web.isEmpty()) {
      return;
    }
    SamplingProfiler profiler = web.get().profiler();
    ProfileDtos.State state = profiler.state();
    ReactLanguage.sendPrefixed(sender, CommandMessages.PROFILER_STATUS,
        MessageArgument.untrusted("state", ReactLanguage.plain(state.running() ? CommandMessages.PROFILER_RUNNING : CommandMessages.PROFILER_IDLE)),
        MessageArgument.untrusted("value", state.intervalMs()),
        MessageArgument.untrusted("mode", state.threads()),
        MessageArgument.untrusted("count", state.samples()),
        MessageArgument.untrusted("duration", Form.duration(state.durationMs(), 0)));
    if (state.samples() == 0) {
      return;
    }
    List<ProfileDtos.OwnerRank> owners = profiler.owners().owners();
    for (int index = 0; index < Math.min(TOP_OWNERS, owners.size()); index++) {
      ProfileDtos.OwnerRank owner = owners.get(index);
      ReactLanguage.sendPrefixed(sender, CommandMessages.PROFILER_OWNER,
          MessageArgument.untrusted("rank", index + 1),
          MessageArgument.untrusted("name", owner.name()),
          MessageArgument.untrusted("percent", owner.percent()));
    }
  }

  static Optional<ReactWebServer> web(VolmitSender sender, String command) {
    Optional<ReactWebServer> web = DirectorHosts.webServer();
    if (web.isEmpty()) {
      CommandHost.unavailable(sender, command);
    }
    return web;
  }
}
