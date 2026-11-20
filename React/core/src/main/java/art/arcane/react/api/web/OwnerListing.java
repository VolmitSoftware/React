package art.arcane.react.api.web;

import art.arcane.react.api.web.dto.OwnerDto;
import art.arcane.react.platform.cost.OwnerCostModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** The {@code /api/v1/owners} list: every loaded plugin or mod with the sampler its cost is reported under. */
public final class OwnerListing {
  private OwnerListing() {
  }

  /**
   * One loaded owner. {@code costOwner} is the owner whose cost sampler counts this one's time: its own id, or the id
   * of the mod it is nested in or bundled with.
   */
  public record Owner(String id, String name, String version, boolean enabled, String costOwner) {
  }

  /** Sorted by name, ignoring case, then by id. */
  public static List<OwnerDto> dtos(String ownerNoun, List<Owner> owners) {
    List<OwnerDto> dtos = new ArrayList<>(owners.size());
    for (Owner owner : owners) {
      String id = owner.id() == null ? "" : owner.id();
      String costOwner = owner.costOwner() == null || owner.costOwner().isBlank() ? id : owner.costOwner();
      OwnerDto dto = new OwnerDto();
      dto.id = id;
      dto.name = owner.name() == null || owner.name().isBlank() ? id : owner.name();
      dto.version = owner.version() == null ? "" : owner.version();
      dto.enabled = owner.enabled();
      dto.samplerId = OwnerCostModel.samplerId(ownerNoun, costOwner);
      dto.includedIn = costOwner.equals(id) ? null : costOwner;
      dtos.add(dto);
    }
    dtos.sort(Comparator.comparing((OwnerDto dto) -> dto.name.toLowerCase(Locale.ROOT)).thenComparing(dto -> dto.id));
    return dtos;
  }
}
