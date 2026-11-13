package art.arcane.react.api.web;

import art.arcane.react.content.feature.FeaturePerWorldTickBudget;

public final class WebRevisions {
  public enum Kind {
    FEATURES,
    TWEAKS,
    CONFIG,
    WORLDS,
    PACKS,
    ACTIONS
  }

  public record Snapshot(long features, long tweaks, long config, long worlds, long packs, long actions) {
    Snapshot bumped(Kind kind) {
      return switch (kind) {
        case FEATURES -> new Snapshot(features + 1L, tweaks, config, worlds, packs, actions);
        case TWEAKS -> new Snapshot(features, tweaks + 1L, config, worlds, packs, actions);
        case CONFIG -> new Snapshot(features, tweaks, config + 1L, worlds, packs, actions);
        case WORLDS -> new Snapshot(features, tweaks, config, worlds + 1L, packs, actions);
        case PACKS -> new Snapshot(features, tweaks, config, worlds, packs + 1L, actions);
        case ACTIONS -> new Snapshot(features, tweaks, config, worlds, packs, actions + 1L);
      };
    }
  }

  // Seeded from the clock so a restarted server never reports a revision a page already loaded at.
  private static volatile Snapshot current = seeded(System.currentTimeMillis());

  private WebRevisions() {
  }

  public static Snapshot current() {
    return current;
  }

  public static void bump(Kind kind) {
    synchronized (WebRevisions.class) {
      current = current.bumped(kind);
    }
  }

  public static void bumpForConfigPath(String path) {
    if (path == null || path.isBlank()) {
      return;
    }
    int firstDot = path.indexOf('.');
    String category = firstDot < 0 ? path : path.substring(0, firstDot);
    String id = null;
    if (firstDot >= 0) {
      int secondDot = path.indexOf('.', firstDot + 1);
      id = secondDot < 0 ? path.substring(firstDot + 1) : path.substring(firstDot + 1, secondDot);
    }
    bumpForConfigFile(category, id);
  }

  public static void bumpForConfigFile(String category, String id) {
    if (category == null) {
      return;
    }
    switch (category) {
      case "feature" -> {
        bump(Kind.FEATURES);
        if (FeaturePerWorldTickBudget.ID.equals(id)) {
          bump(Kind.WORLDS);
        }
      }
      case "tweak" -> bump(Kind.TWEAKS);
      case "action" -> bump(Kind.ACTIONS);
      case "main", "core", "sampler" -> bump(Kind.CONFIG);
      default -> {
      }
    }
  }

  private static Snapshot seeded(long seed) {
    return new Snapshot(seed, seed, seed, seed, seed, seed);
  }
}
