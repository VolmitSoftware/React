package art.arcane.react.api.sampler;

public final class SamplerMetadata {
  private SamplerMetadata() {
  }

  public static Sampler.Metadata forId(String id, long intervalMs) {
    String key = id == null ? "" : id;
    if (key.startsWith("react-nearby-") || key.startsWith("react-heatmap-")
        || key.startsWith("react-map-") || key.startsWith("react-entity-scan-")) {
      return new Sampler.Metadata(intervalMs, 1_000L, Sampler.Quality.MEASURED,
          key.endsWith("-ms") || key.endsWith("-ms-per-second") ? "ms/s" : "/s");
    }
    String unit = switch (key) {
      case "processor-system-load", "processor-process-load", "processor-outside", "explosion-packet-reduction" -> "ratio";
      case "ticks-per-second" -> "ticks/s";
      case "tick-time", "react-sync-tick-time", "react-async-tick-time", "react-job-queue-time",
          "react-job-budget", "gc-pause-p95", "ping-jitter", "player-ping-p95", "chunk-load-listener-ms",
          "chunk-gen-listener-ms", "per-world-tick-time", "world-save-event-interval", "top-world-mspt", "top-chunk-cost",
          "physics-event-span", "hopper-event-span", "fluid-event-span", "redstone-event-span" -> "ms";
      case "event-time" -> "ms/s";
      case "event-handles-per-tick" -> "calls/tick";
      case "redstone-burst-rate", "tick-spike-rate" -> "/min";
      case "chunks-loaded", "chunks-generated", "chunk-unloads", "spawner-spawns", "entities-spawns",
          "hopper", "fluid", "commands", "physics", "redstone", "crop-fast-forward",
          "hopper-chain-coalescing", "spawner-light-cache-skipped", "lazy-gravity-skipped",
          "backlog-growth-rate", "pdc-write-batcher" -> "/s";
      case "gc-time-percent" -> "%";
      case "incident-score" -> "score";
      case "entity-ai-active-count", "entities-hostile", "projectiles", "chunk-tickets", "chunks-force-loaded",
          "entities-animals", "worlds", "bukkit-pending-tasks", "chunks", "react-jobs-queue",
          "block-entities-ticking", "physics-entities", "ground-items", "villagers", "jvm-threads", "entities",
          "players", "block-entities", "scheduler-backlog", "events-listeners" -> "count";
      default -> "";
    };
    Sampler.Quality quality = switch (key) {
      case "incident-score", "top-chunk-cost", "top-world-mspt", "entity-ai-active-count",
          "physics-entities", "per-world-tick-time", "lazy-gravity-skipped" -> Sampler.Quality.ESTIMATED;
      default -> unit.isEmpty() ? Sampler.Quality.UNKNOWN : Sampler.Quality.MEASURED;
    };
    return new Sampler.Metadata(intervalMs, 0L, quality, unit);
  }
}
