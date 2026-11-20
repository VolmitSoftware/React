package art.arcane.react.api.rendering;

import art.arcane.react.platform.MapHubAction;
import art.arcane.react.platform.MapHubClick;
import art.arcane.react.platform.MapHubIcon;
import art.arcane.react.platform.MapHubScreen;
import art.arcane.react.platform.MapHubTile;
import art.arcane.react.platform.cost.OwnerCostSampler;
import art.arcane.react.platform.cost.OwnerSampler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapHubModelTest {
  private static final Set<String> SAMPLERS = Set.of("heap-used");
  private final MapHubModel model = new MapHubModel(renderer -> SAMPLERS.contains(renderer.getId()) ? "Heap Used" : null);

  @Test
  void sortedRenderersGroupsMetricsFirstAndDropsUnknown() {
    List<ReactRenderer> sorted = model.sortedRenderers(List.of(
        fake("react-zeta"), fake("unknown"), fake("iris-metrics"), fake("iris-alpha"),
        fake("adapt-metrics"), fake("react-metrics"), fake("react-zeta")
    ));

    assertEquals(List.of("adapt-metrics", "iris-metrics", "iris-alpha", "react-metrics", "react-zeta"), ids(sorted));
  }

  @Test
  void groupOfMatchesPrefixesKeywordsAndSamplers() {
    assertEquals(0, model.groupOf(fake("adapt-x")).order());
    assertEquals(MapHubIcon.GROUP_TICK, model.groupOf(fake("tick-time")).icon());
    assertEquals(11, model.groupOf(fake("heap-used-gc")).order());
    assertEquals(22, model.groupOf(fake("heap-used")).order());
    assertEquals(30, model.groupOf(fake("lava-overlay")).order());
    assertEquals(99, model.groupOf(fake("mystery")).order());
  }

  @Test
  void ownerCostMapsShareOneGroupWithOverviewsFirstThenMostExpensive() {
    List<ReactRenderer> sorted = model.sortedRenderers(List.of(
        owner("plugin-entityculling", 0.5D), fake("plugin-event-impact-list-map"), owner("plugin-worldedit", 4D),
        owner("plugin-zeta", 0D), fake("plugin-event-impact-pie-map"), owner("plugin-alpha", 0D), owner("plugin-chunky", 9D)
    ));

    assertEquals(List.of("plugin-event-impact-list-map", "plugin-event-impact-pie-map", "plugin-chunky", "plugin-worldedit",
        "plugin-entityculling", "plugin-alpha", "plugin-zeta"), ids(sorted));
    assertEquals(99, model.groupOf(fake("plugin-api-adapt-earth-mover-guard-checks")).order());
    for (ReactRenderer renderer : sorted) {
      assertEquals(MapHubModel.OWNER_GROUP, model.groupOf(renderer).order(), renderer.getId());
      assertEquals(MapHubIcon.GROUP_OWNERS, model.groupOf(renderer).icon());
    }

    MapHubScreen screen = model.group(sorted, MapHubModel.OWNER_GROUP, 0, Map.of());
    MapHubTile chunky = tile(screen, "map-renderer-plugin-chunky");
    assertTrue(chunky.lore().stream().anyMatch(line -> line.contains(" 9 ms/s, #1 of 5")), String.valueOf(chunky.lore()));
    MapHubTile list = tile(screen, "map-renderer-plugin-event-impact-list-map");
    assertTrue(list.lore().stream().noneMatch(line -> line.contains("ms/s, #")));

    MapHubTile group = tile(model.hub(sorted, Map.of()), "map-hub-group-" + MapHubModel.OWNER_GROUP);
    assertTrue(group.lore().get(1).contains("#1") && group.lore().get(1).contains(" 9 ms/s"), String.valueOf(group.lore()));
    assertEquals(3, group.lore().stream().filter(line -> line.contains("ms/s")).count());
  }

  @Test
  void everyOwnerFamilyJoinsTheGroupFamilyByFamilyBusiestFirst() {
    List<ReactRenderer> sorted = model.sortedRenderers(List.of(
        family("network-out-create", OwnerSampler.FAMILY_GAUGES + 1, 900D), family("chunk-loader-ae2", OwnerSampler.FAMILY_GAUGES, 4D),
        family("worldgen-mod-terralith", OwnerSampler.FAMILY_WORLDGEN, 2D), owner("mod-create", 1D),
        family("chunk-loader-create", OwnerSampler.FAMILY_GAUGES, 40D), family("worldgen-step-features", OwnerSampler.FAMILY_WORLDGEN, Double.NaN),
        fake("worldgen-mod-pie-map")
    ));

    List<ReactRenderer> owners = model.filterByGroup(sorted, MapHubModel.OWNER_GROUP);
    assertEquals(List.of("worldgen-mod-pie-map", "mod-create", "worldgen-mod-terralith", "chunk-loader-create", "chunk-loader-ae2",
        "network-out-create"), ids(owners));
    MapHubTile loader = tile(model.group(sorted, MapHubModel.OWNER_GROUP, 0, Map.of()), "map-renderer-chunk-loader-ae2");
    assertTrue(loader.lore().stream().anyMatch(line -> line.contains("Now: 4, #2 of 2")), String.valueOf(loader.lore()));
  }

  @Test
  void pageLayoutAndClamp() {
    MapHubModel.PageLayout small = MapHubModel.pageLayout(4);
    assertEquals(new MapHubModel.PageLayout(2, 1, 9, 1, false, 1), small);
    MapHubModel.PageLayout large = MapHubModel.pageLayout(100);
    assertEquals(new MapHubModel.PageLayout(6, 5, 45, 3, true, 5), large);
    assertEquals(0, MapHubModel.clampPage(-3, 3));
    assertEquals(2, MapHubModel.clampPage(9, 3));
    assertEquals(0, MapHubModel.clampPage(4, 0));
  }

  @Test
  void centeredPositionsAreSymmetric() {
    assertEquals(0, MapHubModel.centeredPosition(0, 1));
    assertEquals(List.of(-1, 0, 1), positions(3));
    assertEquals(List.of(-2, -1, 0, 1), positions(4));
    assertEquals(List.of(-4, -3, -2, -1, 0, 1, 2, 3, 4), positions(9));
  }

  @Test
  void groupScreenPagesWithNavigationActions() {
    List<ReactRenderer> renderers = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      renderers.add(fake("mystery-" + (100 + i)));
    }
    List<ReactRenderer> sorted = model.sortedRenderers(renderers);

    MapHubScreen screen = model.group(sorted, 99, 1, Map.of());
    assertEquals(6, screen.rows());
    MapHubTile first = screen.tiles().get(0);
    assertEquals("map-renderer-mystery-145", first.key());
    assertEquals(-4, first.position());
    assertEquals(0, first.slot());
    assertInstanceOf(MapHubAction.SelectMap.class, first.actionFor(MapHubClick.LEFT));
    assertInstanceOf(MapHubAction.AddMap.class, first.actionFor(MapHubClick.SHIFT_RIGHT));

    MapHubTile previous = tile(screen, "map-prev");
    assertEquals(new MapHubAction.OpenGroup(99, 0), previous.actionFor(MapHubClick.LEFT));
    assertEquals(new MapHubAction.OpenGroup(99, 0), previous.actionFor(MapHubClick.RIGHT));
    MapHubTile next = tile(screen, "map-next");
    assertEquals(new MapHubAction.OpenGroup(99, 2), next.actionFor(MapHubClick.LEFT));
    assertEquals(new MapHubAction.OpenHub(), tile(screen, "map-hub-back").actionFor(MapHubClick.LEFT));
    assertNull(model.group(sorted, 3, 0, Map.of()));
  }

  @Test
  void hubListsAllGroupsAndMegamapSummary() {
    List<ReactRenderer> sorted = model.sortedRenderers(List.of(fake("adapt-metrics"), fake("iris-metrics"), fake("tick-time")));
    MapHubScreen screen = model.hub(sorted, Map.of("iris-metrics", new MegamapStatus(2, 2, 4, null)));

    assertEquals(1, screen.rows());
    assertEquals(List.of("map-hub-all", "map-hub-megamap", "map-hub-group-0", "map-hub-group-1", "map-hub-group-10"),
        screen.tiles().stream().map(MapHubTile::key).toList());
    assertEquals(List.of(-2, -1, 0, 1, 2), screen.tiles().stream().map(MapHubTile::position).toList());
    assertEquals(new MapHubAction.OpenGroup(MapHubModel.ALL_GROUPS, 0), screen.tiles().get(0).actionFor(MapHubClick.LEFT));
    assertTrue(screen.tiles().get(1).actions().isEmpty());
  }

  private static MapHubTile tile(MapHubScreen screen, String key) {
    return screen.tiles().stream().filter(tile -> tile.key().equals(key)).findFirst().orElseThrow();
  }

  private static List<Integer> positions(int count) {
    List<Integer> positions = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      positions.add(MapHubModel.centeredPosition(i, count));
    }
    return positions;
  }

  private static List<String> ids(List<ReactRenderer> renderers) {
    return renderers.stream().map(ReactRenderer::getId).toList();
  }

  private static ReactRenderer family(String id, int family, double value) {
    return new FamilyRenderer(id, family, value);
  }

  private record FamilyRenderer(String id, int family, double value) implements ReactRenderer, OwnerSampler {
    @Override
    public String getId() {
      return id;
    }

    @Override
    public void render() {
    }

    @Override
    public int ownerFamily() {
      return family;
    }

    @Override
    public double ownerRankValue() {
      return value;
    }
  }

  private static ReactRenderer owner(String id, double cost) {
    return new OwnerRenderer(id, cost);
  }

  private record OwnerRenderer(String id, double cost) implements ReactRenderer, OwnerCostSampler {
    @Override
    public String getId() {
      return id;
    }

    @Override
    public void render() {
    }

    @Override
    public double recentCostMsPerSecond() {
      return cost;
    }
  }

  private static ReactRenderer fake(String id) {
    return new ReactRenderer() {
      @Override
      public String getId() {
        return id;
      }

      @Override
      public void render() {
      }
    };
  }
}
