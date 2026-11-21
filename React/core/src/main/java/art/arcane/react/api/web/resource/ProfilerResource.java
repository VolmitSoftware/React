package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.WebAuth;
import art.arcane.react.api.web.WebError;
import art.arcane.react.api.web.WebMutation;
import art.arcane.react.api.web.WebMutationReporter;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.profiler.CallTree;
import art.arcane.react.api.web.profiler.ProfileDtos;
import art.arcane.react.api.web.profiler.SamplingProfiler;
import io.javalin.http.Context;

import java.util.Locale;
import java.util.function.IntSupplier;

/** {@code /api/v1/profiler}: the sampling profiler's state and controls, owners, hot methods, call tree and threads. */
public final class ProfilerResource {
  private static final int MAX_HOT = 1000;

  private final SamplingProfiler profiler;
  private final WebMutationReporter reporter;
  private final IntSupplier defaultInterval;
  private final IntSupplier defaultMaxSeconds;

  public ProfilerResource(SamplingProfiler profiler, WebMutationReporter reporter, IntSupplier defaultInterval, IntSupplier defaultMaxSeconds) {
    this.profiler = profiler;
    this.reporter = reporter;
    this.defaultInterval = defaultInterval;
    this.defaultMaxSeconds = defaultMaxSeconds;
  }

  public record ControlBody(String action, Integer intervalMs, String threads, Integer maxSeconds) {
  }

  public void state(Context ctx) {
    ctx.json(new Envelope<>(profiler.state()));
  }

  public void control(Context ctx) {
    WebAuth.requireScope(ctx, WebAuth.OPERATE, "Profiler controls need an operator or admin pairing.");
    ControlBody body = ctx.bodyAsClass(ControlBody.class);
    String action = body == null || body.action() == null ? "" : body.action().trim().toLowerCase(Locale.ROOT);
    ProfileDtos.State state;
    String detail;
    switch (action) {
      case "start" -> {
        String threads = body.threads() == null ? SamplingProfiler.SERVER : body.threads().trim().toLowerCase(Locale.ROOT);
        if (!threads.equals(SamplingProfiler.SERVER) && !threads.equals(SamplingProfiler.ALL)) {
          throw WebError.badRequest("threads must be server or all");
        }
        int interval = body.intervalMs() == null ? defaultInterval.getAsInt() : body.intervalMs();
        int seconds = body.maxSeconds() == null ? defaultMaxSeconds.getAsInt() : body.maxSeconds();
        if (interval < SamplingProfiler.MIN_INTERVAL_MS || interval > SamplingProfiler.MAX_INTERVAL_MS) {
          throw WebError.badRequest("intervalMs must be between 1 and 1000");
        }
        if (seconds < 1 || seconds > SamplingProfiler.MAX_SECONDS) {
          throw WebError.badRequest("maxSeconds must be between 1 and 3600");
        }
        state = profiler.start(interval, threads, seconds);
        detail = state.intervalMs() + " ms, " + state.threads() + " threads, " + seconds + " s";
      }
      case "stop" -> {
        state = profiler.stop();
        detail = state.samples() + " samples";
      }
      case "reset" -> {
        state = profiler.reset();
        detail = "cleared";
      }
      default -> throw WebError.badRequest("action must be start, stop or reset");
    }
    reporter.report(ctx, new WebMutation("profiler." + action, "profiler", detail, "APPLIED"));
    ctx.json(new Envelope<>(state));
  }

  public void owners(Context ctx) {
    ctx.json(new Envelope<>(profiler.owners(booleanParam(ctx, "idle", false))));
  }

  public void hot(Context ctx) {
    int limit = intParam(ctx, "limit", 50, 1, MAX_HOT);
    ctx.json(new Envelope<>(profiler.hot(ctx.queryParam("owner"), limit, booleanParam(ctx, "idle", false))));
  }

  public void tree(Context ctx) {
    double minPercent = doubleParam(ctx, "minPercent", 0.5D);
    int maxDepth = intParam(ctx, "maxDepth", 64, 1, CallTree.MAX_DEPTH);
    ctx.json(new Envelope<>(profiler.tree(new CallTree.Query(ctx.queryParam("owner"), ctx.queryParam("thread"), minPercent, maxDepth,
        booleanParam(ctx, "group", true), booleanParam(ctx, "collapse", true), booleanParam(ctx, "idle", false)))));
  }

  public void threads(Context ctx) {
    ctx.json(new Envelope<>(profiler.threads()));
  }

  static int intParam(Context ctx, String name, int fallback, int minimum, int maximum) {
    String raw = ctx.queryParam(name);
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      return Math.max(minimum, Math.min(maximum, Integer.parseInt(raw.trim())));
    } catch (NumberFormatException invalid) {
      throw WebError.badRequest(name + " must be a whole number");
    }
  }

  static boolean booleanParam(Context ctx, String name, boolean fallback) {
    String raw = ctx.queryParam(name);
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "true", "1", "yes" -> true;
      case "false", "0", "no" -> false;
      default -> throw WebError.badRequest(name + " must be true or false");
    };
  }

  static double doubleParam(Context ctx, String name, double fallback) {
    String raw = ctx.queryParam(name);
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      double value = Double.parseDouble(raw.trim());
      if (!Double.isFinite(value) || value < 0D || value > 100D) {
        throw WebError.badRequest(name + " must be between 0 and 100");
      }
      return value;
    } catch (NumberFormatException invalid) {
      throw WebError.badRequest(name + " must be a number");
    }
  }
}
