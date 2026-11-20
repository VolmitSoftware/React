package art.arcane.react.api.web;

import art.arcane.react.api.web.dto.OwnerDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerListingTest {
  @Test
  void dtosSortByNameAndPointNestedModsAtTheirOwnersSampler() {
    List<OwnerDto> dtos = OwnerListing.dtos("mod", List.of(
        new OwnerListing.Owner("fabric-api-base", "Fabric API Base", "1.0", true, "fabric-api"),
        new OwnerListing.Owner("create", "Create", "6.0", true, "create"),
        new OwnerListing.Owner("fabric-api", "Fabric API", "0.1", true, null)));

    assertEquals(List.of("create", "fabric-api", "fabric-api-base"), dtos.stream().map(dto -> dto.id).toList());
    assertEquals("mod-create", dtos.get(0).samplerId);
    assertNull(dtos.get(0).includedIn);
    assertEquals("mod-fabric-api", dtos.get(1).samplerId);
    assertNull(dtos.get(1).includedIn);
    assertEquals("mod-fabric-api", dtos.get(2).samplerId);
    assertEquals("fabric-api", dtos.get(2).includedIn);
  }

  @Test
  void dtosKeepPluginStateAndFallBackToTheIdForAMissingName() {
    List<OwnerDto> dtos = OwnerListing.dtos("plugin", List.of(
        new OwnerListing.Owner("WorldEdit", "WorldEdit", "7.3", false, "WorldEdit"),
        new OwnerListing.Owner("Iris", " ", null, true, "Iris")));

    assertEquals("Iris", dtos.get(0).name);
    assertEquals("", dtos.get(0).version);
    assertTrue(dtos.get(0).enabled);
    assertEquals("plugin-iris", dtos.get(0).samplerId);
    assertEquals("WorldEdit", dtos.get(1).name);
    assertFalse(dtos.get(1).enabled);
    assertEquals("plugin-worldedit", dtos.get(1).samplerId);
  }
}
