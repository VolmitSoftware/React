package art.arcane.react.api.rendering;

import art.arcane.react.platform.cost.OwnerCostModel;
import art.arcane.react.platform.cost.OwnerNoun;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class MapRendererRegistry {
  private static final Pattern COLOR_CODES = Pattern.compile("(?i)§[0-9A-FK-ORX]");

  private final Map<String, ReactRenderer> renderers = new ConcurrentHashMap<>();
  private final Map<String, ReactRenderer> resolved = new ConcurrentHashMap<>();
  private final Map<String, Metadata> metadata = new ConcurrentHashMap<>();
  private volatile MapRendererCatalog catalog = MapRendererCatalog.NONE;

  public MapRendererRegistry() {
    renderers.put(RendererUnknown.ID, new RendererUnknown());
  }

  public void catalog(MapRendererCatalog catalog) {
    this.catalog = catalog == null ? MapRendererCatalog.NONE : catalog;
    resolved.clear();
  }

  public Map<String, ReactRenderer> renderers() {
    return renderers;
  }

  public void register(ReactRenderer renderer) {
    if (renderer == null || renderer.getId() == null) {
      return;
    }

    if (renderers.put(renderer.getId(), renderer) != renderer) {
      resolved.clear();
    }
  }

  public void unregister(String rendererId) {
    if (rendererId == null || rendererId.isBlank()) {
      return;
    }

    boolean removed = renderers.remove(rendererId) != null;
    removed |= renderers.remove(normalize(rendererId)) != null;
    if (removed) {
      resolved.clear();
    }
  }

  public void clearCaches() {
    resolved.clear();
    metadata.clear();
  }

  public ReactRenderer unknown() {
    return renderers.get(RendererUnknown.ID);
  }

  public ReactRenderer resolve(String rendererId) {
    String key = Objects.toString(rendererId, "");
    ReactRenderer cached = resolved.get(key);
    if (cached != null) {
      return cached;
    }

    ReactRenderer found = resolveUncached(rendererId);
    if (found != null) {
      resolved.put(key, found);
    }
    return found;
  }

  private ReactRenderer resolveUncached(String rendererId) {
    ReactRenderer unknown = unknown();
    String requested = normalize(rendererId);
    if (requested.isBlank()) {
      return unknown;
    }

    ReactRenderer exact = renderers.get(rendererId);
    if (exact != null) {
      return exact;
    }

    ReactRenderer normalized = renderers.get(requested);
    if (normalized != null) {
      return normalized;
    }

    String requestedAlias = stripFeaturePrefix(requested);
    if (!requestedAlias.equals(requested)) {
      ReactRenderer alias = renderers.get(requestedAlias);
      if (alias != null) {
        return alias;
      }
    }

    String requestedCanonical = canonical(requested);
    String requestedAliasCanonical = canonical(requestedAlias);
    for (ReactRenderer candidate : renderers.values()) {
      if (matches(candidate, requested, requestedAlias, requestedCanonical, requestedAliasCanonical)) {
        return candidate;
      }
    }

    List<ReactRenderer> dynamic = catalog.dynamicCandidates(requested, requestedAlias);
    if (dynamic != null) {
      for (ReactRenderer candidate : dynamic) {
        if (matches(candidate, requested, requestedAlias, requestedCanonical, requestedAliasCanonical)) {
          register(candidate);
          return candidate;
        }
      }
    }

    return unknown;
  }

  private static boolean matches(
      ReactRenderer renderer,
      String requested,
      String requestedAlias,
      String requestedCanonical,
      String requestedAliasCanonical
  ) {
    if (renderer == null || renderer.getId() == null) {
      return false;
    }

    String candidate = normalize(renderer.getId());
    return candidate.equals(requested)
        || candidate.equals(requestedAlias)
        || canonical(candidate).equals(requestedCanonical)
        || canonical(candidate).equals(requestedAliasCanonical);
  }

  public Metadata metadataFor(String rendererId) {
    String id = Objects.toString(rendererId, RendererUnknown.ID);
    return metadata.computeIfAbsent(id, MapRendererRegistry::buildMetadata);
  }

  private static Metadata buildMetadata(String rendererId) {
    String normalized = normalize(rendererId);
    return new Metadata(
        normalized,
        "Renderer: " + displayName(rendererId),
        "Scope: " + scope(normalized),
        "ID: " + rendererId
    );
  }

  public static boolean isSpecific(String rendererId) {
    return !normalize(rendererId).isBlank() && !isUnknown(rendererId);
  }

  public static boolean isUnknown(String rendererId) {
    String normalized = normalize(rendererId);
    return normalized.isBlank() || normalized.equals(RendererUnknown.ID);
  }

  public static String stripFeaturePrefix(String rendererId) {
    String normalized = normalize(rendererId);
    if (normalized.startsWith("feature-") && normalized.length() > "feature-".length()) {
      return normalized.substring("feature-".length());
    }
    return normalized;
  }

  public static String canonical(String rendererId) {
    return normalize(rendererId).replaceAll("[^a-z0-9]", "");
  }

  public static String scope(String normalizedRendererId) {
    if (normalizedRendererId.startsWith("iris-")) {
      return "Iris";
    }
    if (normalizedRendererId.startsWith("adapt-")) {
      return "Adapt";
    }
    if (normalizedRendererId.startsWith("wormholes-")) {
      return "Wormholes";
    }
    if (normalizedRendererId.startsWith("gloss-")) {
      return "Gloss";
    }
    if (normalizedRendererId.startsWith("hiddenore-")) {
      return "HiddenOre";
    }
    if (normalizedRendererId.startsWith("biletools-")) {
      return "BileTools";
    }
    if (normalizedRendererId.startsWith("animus-")) {
      return "Animus";
    }
    if (normalizedRendererId.startsWith("react-")) {
      return "React";
    }
    if (OwnerCostModel.isOwnerScopedId(normalizedRendererId)) {
      return OwnerNoun.pick("Plugins", "Mods");
    }
    return "Core";
  }

  public static String displayName(String rendererId) {
    if (rendererId == null || rendererId.isBlank()) {
      return "Unknown";
    }

    String spaced = rendererId
        .replace('_', ' ')
        .replace('-', ' ')
        .replaceAll("([a-z])([A-Z])", "$1 $2")
        .trim();
    if (spaced.isBlank()) {
      return rendererId;
    }

    return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
  }

  public static String normalize(String rendererId) {
    if (rendererId == null) {
      return "";
    }

    int length = rendererId.length();
    if (length == 0) {
      return rendererId;
    }

    if (rendererId.charAt(0) > ' ' && rendererId.charAt(length - 1) > ' ') {
      boolean normalized = true;
      for (int i = 0; i < length; i++) {
        char character = rendererId.charAt(i);
        if (character > 127 || (character >= 'A' && character <= 'Z')) {
          normalized = false;
          break;
        }
      }

      if (normalized) {
        return rendererId;
      }
    }

    return rendererId.toLowerCase(Locale.ROOT).trim();
  }

  public static String stripColor(String line) {
    return line == null ? null : COLOR_CODES.matcher(line).replaceAll("");
  }

  public static String rendererIdFromLore(List<String> lore) {
    if (lore == null) {
      return RendererUnknown.ID;
    }

    for (String line : lore) {
      String value = loreValue(line, "ID:");
      if (value != null && isSpecific(value)) {
        return value;
      }
    }

    return RendererUnknown.ID;
  }

  public static String mapTokenFromLore(List<String> lore) {
    if (lore == null) {
      return null;
    }

    for (String line : lore) {
      String value = loreValue(line, "Tag:");
      if (value != null && !value.isBlank()) {
        return value;
      }
    }

    return null;
  }

  private static String loreValue(String line, String prefix) {
    if (line == null || line.isBlank()) {
      return null;
    }

    String stripped = stripColor(line);
    if (stripped == null || stripped.isBlank()) {
      return null;
    }

    String trimmed = stripped.trim();
    if (!trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
      return null;
    }

    return trimmed.substring(prefix.length()).trim();
  }

  public record Metadata(String normalizedId, String rendererLine, String scopeLine, String idLine) {
  }
}
