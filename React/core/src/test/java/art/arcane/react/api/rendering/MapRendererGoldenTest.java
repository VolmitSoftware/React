package art.arcane.react.api.rendering;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.platform.DirtyRect;
import art.arcane.react.platform.MapRenderTarget;
import art.arcane.react.util.data.TinyColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

class MapRendererGoldenTest {
  private static final Map<String, String> GOLDEN = orderedGolden(
      "unknown", "84236d2c5216ff1e",
      "unknown-wall", "bbd45b611d56f63a",
      "sampler", "c55fc483deeb599f",
      "sampler-wall", "ce7a0b114cfbe216",
      "primitives", "ed7962cb1705d7c6"
  );

  private static Map<String, String> orderedGolden(String... pairs) {
    Map<String, String> golden = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      golden.put(pairs[i], pairs[i + 1]);
    }
    return golden;
  }

  @AfterEach
  void tearDown() {
    Graph.bindLatest(null);
  }

  @Test
  void everyCoreRendererKindDrawsTheSameBytes() throws Exception {
    Graph.bindLatest(samplerId -> new Graph.Latest(true, 42.5D));
    Map<String, String> actual = new LinkedHashMap<>();
    actual.put("unknown", hash(compose(new RendererUnknown(), null)));
    actual.put("unknown-wall", hash(compose(new RendererUnknown(), new MegamapGrid.MegamapTile(2, 2, 1, 0))));
    ReactRenderer sampler = SamplerRenderer.of(new FixedSampler("golden-tick-sampler"));
    actual.put("sampler", hash(compose(sampler, null)));
    actual.put("sampler-wall", hash(compose(sampler, new MegamapGrid.MegamapTile(3, 2, 2, 1))));
    actual.put("primitives", hash(compose(new PrimitiveRenderer(), null)));

    Assertions.assertEquals(GOLDEN, actual);
  }

  @Test
  void flushPublishesExactlyThePaintedPixels() {
    MapComposer composer = new MapComposer(1, UUID.randomUUID(), new PrimitiveRenderer());
    DirtyRect first = composer.compose(null, MapRenderTarget.DETACHED, null, null, 0L, 32, 1L);
    Assertions.assertFalse(first.isEmpty());
    byte[] published = composer.published().clone();
    DirtyRect second = composer.compose(null, MapRenderTarget.DETACHED, null, null, 0L, 32, 2L);
    Assertions.assertTrue(second.isEmpty(), "an unchanged redraw publishes nothing");
    Assertions.assertArrayEquals(published, composer.published());
  }

  static byte[] compose(ReactRenderer renderer, MegamapGrid.MegamapTile tile) {
    MapRendererRegistry registry = new MapRendererRegistry();
    registry.register(renderer);
    MapComposer composer = new MapComposer(7, UUID.randomUUID(), renderer);
    DirtyRect changed = composer.compose(registry, new MapRenderTarget(7, null, null), tile, null, 0L, 32, 1_000L);
    byte[] surface = composer.published().clone();
    int painted = 0;
    for (byte value : surface) {
      if (value != ReactRenderContext.UNTOUCHED) {
        painted++;
      }
    }
    Assertions.assertEquals(DirtyRect.FULL, changed, renderer.getId() + " did not paint the whole map");
    Assertions.assertEquals(surface.length, painted, renderer.getId() + " left pixels unpainted");
    return surface;
  }

  static String hash(byte[] surface) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(surface)).substring(0, 16);
  }

  private static final class PrimitiveRenderer implements ReactRenderer {
    @Override
    public String getId() {
      return "golden-primitives";
    }

    @Override
    public void render() {
      clear(MapTheme.SURFACE_0);
      dashHeader("Golden", "12.5 ms", MapTheme.INFO);
      panel(new Region(4, 20, 60, 40), MapTheme.SURFACE_2, MapTheme.LINE_STRONG);
      line(4, 120, 120, 70, new TinyColor(240, 80, 40));
      fillRgb(70, 20, 50, 10, MapColors.lerpRgb(0.3D, 0x101820, 0xF0C050));
      textIn(new Region(70, 34, 54, 10), 0, 0, "Fit this long label", MapTheme.TEXT);
      textCenteredIn(new Region(0, 100, 128, 10), 0, "centred", MapTheme.TEXT_MUTED);
      drawMegamapNotice("2x2");
    }
  }

  private static final class FixedSampler implements Sampler {
    private final String id;

    private FixedSampler(String id) {
      this.id = id;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public double sample() {
      return 42.5D;
    }

    @Override
    public String formattedValue(double t) {
      return String.format(java.util.Locale.ROOT, "%.1f", t);
    }

    @Override
    public String formattedSuffix(double t) {
      return "ms";
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }
  }
}
