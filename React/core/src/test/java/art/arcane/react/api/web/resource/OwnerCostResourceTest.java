package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.dto.OwnerCostDto;
import art.arcane.react.platform.EventMeasurement;
import art.arcane.react.platform.cost.OwnerCostMode;
import art.arcane.react.platform.cost.OwnerCostModel;
import art.arcane.react.platform.cost.OwnerCostProbe;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

class OwnerCostResourceTest {
  @Test
  void listsEveryCatalogOwnerWithKindsSharesAndSamplerIds() {
    Fixture fixture = new Fixture();
    fixture.drain.add(new Object[]{"create", "Create", "block_entity", 6_000_000L, 4L});
    fixture.drain.add(new Object[]{"create", "Create", "event", 2_000_000L, 2L});
    fixture.drain.add(new Object[]{"lithium", "Lithium", "entity", 2_000_000L, 9L});
    fixture.publish();

    OwnerCostDto dto = OwnerCostResource.snapshot(fixture.measurement, "mod", 0, (String id) -> !id.equals("mod-ferritecore"));

    Assertions.assertEquals("mod", dto.ownerNoun);
    Assertions.assertTrue(dto.measuring);
    Assertions.assertEquals(10D, dto.totalMsPerSecond, 1.0E-9D);
    Assertions.assertEquals(List.of("create", "lithium", "ferritecore"), Arrays.stream(dto.owners).map(owner -> owner.id).toList());
    OwnerCostDto.OwnerDto create = dto.owners[0];
    Assertions.assertEquals("mod-create", create.samplerId);
    Assertions.assertEquals("Create", create.name);
    Assertions.assertEquals(8D, create.msPerSecond, 1.0E-9D);
    Assertions.assertEquals(0.8D, create.share, 1.0E-9D);
    Assertions.assertEquals(6, create.calls);
    Assertions.assertEquals(Map.of("block_entity", 6D, "event", 2D), create.kinds);
    Assertions.assertEquals(0D, dto.owners[2].msPerSecond);
    Assertions.assertEquals("FerriteCore", dto.owners[2].name);
    Assertions.assertNull(dto.owners[2].samplerId);
    Assertions.assertEquals(List.of("event", "block_entity", "entity"), Arrays.stream(dto.kinds).map(kind -> kind.kind).toList());
    Assertions.assertEquals(0.6D, dto.kinds[1].share, 1.0E-9D);

    Assertions.assertEquals(1, OwnerCostResource.snapshot(fixture.measurement, "mod", 1, (String id) -> true).owners.length);
  }

  @Test
  void withoutACostModelTheListIsEmpty() {
    OwnerCostDto dto = OwnerCostResource.snapshot(null, null, 0, (String id) -> true);

    Assertions.assertEquals("plugin", dto.ownerNoun);
    Assertions.assertEquals(0, dto.owners.length);
    Assertions.assertEquals(0, dto.kinds.length);
  }

  private static final class Fixture {
    private final List<Object[]> drain = new ArrayList<>();
    private long nanos = 1_000_000_000L;
    private long millis = 1_000_000L;
    private final OwnerCostModel model = new OwnerCostModel(new OwnerCostProbe() {
      @Override
      public boolean available() {
        return true;
      }

      @Override
      public String unavailableReason() {
        return "";
      }

      @Override
      public void setActive(boolean active) {
      }

      @Override
      public boolean active() {
        return true;
      }

      @Override
      public void drain(OwnerCostConsumer consumer) {
        for (Object[] entry : drain) {
          consumer.accept((String) entry[0], (String) entry[1], (String) entry[2], (Long) entry[3], (Long) entry[4], 0L);
        }
        drain.clear();
      }
    }, () -> millis, () -> nanos);
    private final EventMeasurement measurement = new EventMeasurement() {
      @Override
      public boolean isMeasuring() {
        return model.measuring();
      }

      @Override
      public long getWindowPublishedAtMs() {
        return model.publishedAtMs();
      }

      @Override
      public double getWindowSeconds() {
        return model.windowSeconds();
      }

      @Override
      public void markSamplerActivity() {
      }

      @Override
      public double getEventTimeMsPerSecond() {
        return model.totalMsPerSecond();
      }

      @Override
      public int getListenerCount() {
        return 0;
      }

      @Override
      public double getCallsPerTick() {
        return 0D;
      }

      @Override
      public Map<String, Double> snapshotPluginEventTimeMS() {
        return model.snapshotMs();
      }

      @Override
      public Map<String, Integer> snapshotPluginEventCalls() {
        return model.snapshotCalls();
      }

      @Override
      public OwnerCostModel ownerCostModel() {
        return model;
      }

      @Override
      public Map<String, String> ownerCatalog() {
        return new TreeMap<>(Map.of("create", "Create", "lithium", "Lithium", "ferritecore", "FerriteCore"));
      }
    };

    private Fixture() {
      model.mode(OwnerCostMode.ALWAYS);
      model.start();
      model.tick();
    }

    private void publish() {
      nanos += 1_000_000_000L;
      millis += 1_000L;
      model.collect();
    }
  }
}
