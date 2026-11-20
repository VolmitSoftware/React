package art.arcane.react.content.feature;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class OwnerCostKindBreakdownTest {
  @Test
  void ownersRankByTotalAndKindsKeepTheKnownOrderFirst() {
    OwnerCostKindRenderer.Breakdown breakdown = OwnerCostKindRenderer.breakdown(
        Map.of(
            "lithium", Map.of("entity", 6D, "block_entity", 2D),
            "create", Map.of("block_entity", 12D, "custom", 3D, "event", 1D),
            "idle", Map.of("event", 0D)
        ),
        Map.of("create", "Create", "lithium", "Lithium"),
        2D
    );

    Assertions.assertEquals(List.of("Create", "Lithium"), breakdown.owners().stream().map(OwnerCostKindRenderer.Owner::name).toList());
    Assertions.assertEquals(List.of("event", "block_entity", "entity", "custom"), breakdown.kinds());
    Assertions.assertEquals(12D, breakdown.totalMsPerSecond(), 1.0E-9D);
    Assertions.assertEquals(7D, breakdown.kindMsPerSecond().get("block_entity"), 1.0E-9D);
    Assertions.assertEquals(8D, breakdown.owners().get(0).totalMsPerSecond(), 1.0E-9D);
    Assertions.assertEquals(Map.of("entity", 3D, "block_entity", 1D), breakdown.owners().get(1).kindMsPerSecond());
  }

  @Test
  void ownersWithoutANameKeepTheirIdAndAnEmptyWindowIsEmpty() {
    OwnerCostKindRenderer.Breakdown breakdown = OwnerCostKindRenderer.breakdown(Map.of("unknown", Map.of("entity", 1D)), Map.of(), 1D);

    Assertions.assertEquals("unknown", breakdown.owners().get(0).name());
    Assertions.assertSame(OwnerCostKindRenderer.Breakdown.EMPTY, OwnerCostKindRenderer.breakdown(Map.of(), Map.of(), 1D));
    Assertions.assertSame(OwnerCostKindRenderer.Breakdown.EMPTY, OwnerCostKindRenderer.breakdown(Map.of("a", Map.of("event", 1D)), Map.of(), 0D));
  }
}
