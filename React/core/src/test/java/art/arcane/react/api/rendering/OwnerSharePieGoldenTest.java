package art.arcane.react.api.rendering;

import art.arcane.react.content.feature.OwnerSharePieRenderer;
import art.arcane.react.localization.catalog.RendererMessages;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

class OwnerSharePieGoldenTest {
  private static final Map<String, String> GOLDEN = ordered(
      "event-mod-pie-map", "48801f2001e8aecd",
      "event-mod-pie-map-wall", "c096bc60c7450cfa",
      "chunk-loader-mod-pie-map", "a92021f3e3f94ee1",
      "network-mod-pie-map", "77542653f6146240",
      "profiler-mod-pie-map", "4c04658424a009e3",
      "profiler-mod-pie-map-stopped", "35445eb8f23eda18"
  );

  private static Map<String, String> ordered(String... pairs) {
    Map<String, String> golden = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      golden.put(pairs[i], pairs[i + 1]);
    }
    return golden;
  }

  private static Map<String, Long> buckets(Object... pairs) {
    Map<String, Long> buckets = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      buckets.put((String) pairs[i], ((Number) pairs[i + 1]).longValue());
    }
    return buckets;
  }

  @Test
  void modSharePiesDrawTheSameBytes() throws Exception {
    Map<String, Long> events = buckets("Create", 4_250L, "Mekanism", 1_800L, "Applied Energistics 2", 620L, "Minecraft", 2_100L, "Tiny", 15L);
    OwnerSharePieRenderer event = new OwnerSharePieRenderer("event-mod-pie-map", RendererMessages.TITLE_MOD_EVENT_SHARE, MapTheme.INFO,
        OwnerSharePieRenderer.Unit.MS_PER_SECOND, () -> events, RendererMessages.PIE_NO_DATA);
    OwnerSharePieRenderer chunks = new OwnerSharePieRenderer("chunk-loader-mod-pie-map", RendererMessages.TITLE_MOD_CHUNK_LOADER_SHARE,
        MapTheme.ACCENT_TEAL, OwnerSharePieRenderer.Unit.CHUNKS, () -> buckets("FTB Chunks", 81L, "Create", 9L, "Minecraft", 25L),
        RendererMessages.PIE_NO_DATA);
    OwnerSharePieRenderer network = new OwnerSharePieRenderer("network-mod-pie-map", RendererMessages.TITLE_MOD_NETWORK_SHARE,
        MapTheme.ACCENT_MAGENTA, OwnerSharePieRenderer.Unit.BYTES_PER_SECOND,
        () -> buckets("Create", 48_200L, "Xaero's Minimap", 3_100L, "Minecraft", 120_400L), RendererMessages.PIE_NO_DATA);
    OwnerSharePieRenderer profiler = new OwnerSharePieRenderer("profiler-mod-pie-map", RendererMessages.TITLE_MOD_PROFILER_SHARE,
        MapTheme.ACCENT_PINK, OwnerSharePieRenderer.Unit.SAMPLES, () -> buckets("Minecraft", 5_200L, "Create", 1_900L, "C2ME", 640L),
        RendererMessages.PIE_PROFILER_STOPPED);
    OwnerSharePieRenderer stopped = new OwnerSharePieRenderer("profiler-mod-pie-map", RendererMessages.TITLE_MOD_PROFILER_SHARE,
        MapTheme.ACCENT_PINK, OwnerSharePieRenderer.Unit.SAMPLES, LinkedHashMap::new, RendererMessages.PIE_PROFILER_STOPPED);

    Map<String, String> actual = new LinkedHashMap<>();
    actual.put("event-mod-pie-map", MapRendererGoldenTest.hash(MapRendererGoldenTest.compose(event, null)));
    actual.put("event-mod-pie-map-wall", MapRendererGoldenTest.hash(MapRendererGoldenTest.compose(event, new MegamapGrid.MegamapTile(2, 2, 1, 0))));
    actual.put("chunk-loader-mod-pie-map", MapRendererGoldenTest.hash(MapRendererGoldenTest.compose(chunks, null)));
    actual.put("network-mod-pie-map", MapRendererGoldenTest.hash(MapRendererGoldenTest.compose(network, null)));
    actual.put("profiler-mod-pie-map", MapRendererGoldenTest.hash(MapRendererGoldenTest.compose(profiler, null)));
    actual.put("profiler-mod-pie-map-stopped", MapRendererGoldenTest.hash(MapRendererGoldenTest.compose(stopped, null)));
    Assertions.assertEquals(GOLDEN, actual);
  }
}
