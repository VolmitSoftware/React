package art.arcane.react.api.metric.internal;

import art.arcane.react.api.metric.ReactMetric;
import art.arcane.react.api.metric.ReactMetricSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class MetricSourceGatewayTest {

  private static MetricSourceGateway gateway(List<String> log) {
    return new MetricSourceGateway(3, 0L, log::add);
  }

  private static ReactMetricSource source(String id, List<ReactMetric> metrics) {
    return new ReactMetricSource() {
      @Override
      public String sourceId() {
        return id;
      }

      @Override
      public List<ReactMetric> metrics() {
        if (metrics == null) {
          throw new IllegalStateException("boom");
        }

        return metrics;
      }
    };
  }

  @Test
  void wellBehavedSourceIsAccepted() {
    List<String> log = new ArrayList<>();
    MetricSourceGateway gateway = gateway(log);
    ReactMetricSource healthy = source("guardianpets",
        List.of(ReactMetric.gauge("guardianpets.pets.live", "Live", "")));

    Assertions.assertEquals("guardianpets", gateway.sourceIdOf(healthy, "GuardianPets"));
    Assertions.assertEquals(1, gateway.declarationOf(healthy, "guardianpets", "GuardianPets").size());
    Assertions.assertEquals(0L, gateway.totalFaults());
    Assertions.assertTrue(log.isEmpty());
  }

  @Test
  void sourceIdIsNormalizedBeforeValidation() {
    MetricSourceGateway gateway = gateway(new ArrayList<>());
    Assertions.assertEquals("guardianpets",
        gateway.sourceIdOf(source("  GuardianPets ", List.of()), "GuardianPets"));
  }

  @Test
  void aThrowingSourceIdIsRefused() {
    List<String> log = new ArrayList<>();
    MetricSourceGateway gateway = gateway(log);

    String id = gateway.sourceIdOf(new ReactMetricSource() {
      @Override
      public String sourceId() {
        throw new IllegalArgumentException("nope");
      }

      @Override
      public List<ReactMetric> metrics() {
        return List.of();
      }
    }, "Broken");

    Assertions.assertEquals("", id);
    Assertions.assertEquals(1L, gateway.totalRefusals());
    Assertions.assertTrue(log.getFirst().contains("Broken"));
  }

  @Test
  void reservedSiblingIdsAreRefusedWithANamedLogLine() {
    List<String> log = new ArrayList<>();
    MetricSourceGateway gateway = gateway(log);

    Assertions.assertEquals("", gateway.sourceIdOf(source("adapt", List.of()), "Imposter"));
    Assertions.assertTrue(log.getFirst().contains("reserved"));
    Assertions.assertTrue(log.getFirst().contains("Imposter"));
  }

  @Test
  void malformedIdsAreRefused() {
    List<String> log = new ArrayList<>();
    MetricSourceGateway gateway = gateway(log);

    Assertions.assertEquals("", gateway.sourceIdOf(source("a", List.of()), "Tiny"));
    Assertions.assertEquals("", gateway.sourceIdOf(source("has space", List.of()), "Spacey"));
    Assertions.assertEquals(2L, gateway.totalRefusals());
  }

  @Test
  void aThrowingMetricsCallIsAFaultAndReturnsNothing() {
    List<String> log = new ArrayList<>();
    MetricSourceGateway gateway = gateway(log);

    Assertions.assertNull(gateway.declarationOf(source("guardianpets", null), "guardianpets", "GuardianPets"));
    Assertions.assertEquals(1L, gateway.totalFaults());
    Assertions.assertEquals(1, gateway.faultCount("guardianpets"));
  }

  @Test
  void nullMetricsIsAFault() {
    MetricSourceGateway gateway = gateway(new ArrayList<>());
    ReactMetricSource nully = new ReactMetricSource() {
      @Override
      public String sourceId() {
        return "guardianpets";
      }

      @Override
      public List<ReactMetric> metrics() {
        return null;
      }
    };

    Assertions.assertNull(gateway.declarationOf(nully, "guardianpets", "GuardianPets"));
    Assertions.assertEquals(1L, gateway.totalFaults());
  }

  @Test
  void repeatedFaultsQuarantineTheSourceAndItIsSkippedAfterwards() {
    List<String> log = new ArrayList<>();
    MetricSourceGateway gateway = gateway(log);
    ReactMetricSource hostile = source("guardianpets", null);

    for (int i = 0; i < 3; i++) {
      gateway.declarationOf(hostile, "guardianpets", "GuardianPets");
    }

    Assertions.assertTrue(gateway.isQuarantined("guardianpets"));
    Assertions.assertTrue(log.stream().anyMatch(line -> line.contains("quarantined")));

    long faultsBefore = gateway.totalFaults();
    Assertions.assertNull(gateway.declarationOf(hostile, "guardianpets", "GuardianPets"));
    Assertions.assertEquals(faultsBefore, gateway.totalFaults());
  }

  @Test
  void forgettingASourceClearsItsQuarantine() {
    MetricSourceGateway gateway = gateway(new ArrayList<>());
    ReactMetricSource hostile = source("guardianpets", null);

    for (int i = 0; i < 3; i++) {
      gateway.declarationOf(hostile, "guardianpets", "GuardianPets");
    }

    gateway.forget("guardianpets");

    Assertions.assertFalse(gateway.isQuarantined("guardianpets"));
    Assertions.assertEquals(0, gateway.faultCount("guardianpets"));
  }

  @Test
  void theGatewayNeverHandsBackTheListTheSourceReturned() {
    MetricSourceGateway gateway = gateway(new ArrayList<>());
    List<ReactMetric> theirs = new ArrayList<>(
        List.of(ReactMetric.gauge("guardianpets.pets.live", "Live", "")));

    List<ReactMetric> ours = gateway.declarationOf(source("guardianpets", theirs), "guardianpets", "GuardianPets");

    Assertions.assertNotSame(theirs, ours);
    Assertions.assertEquals(1, ours.size());
    Assertions.assertThrows(UnsupportedOperationException.class, () -> ours.add(null));

    theirs.clear();
    Assertions.assertEquals(1, ours.size());
  }

  @Test
  void aMetricListWhoseIteratorThrowsIsContainedAsAFault() {
    List<String> log = new ArrayList<>();
    MetricSourceGateway gateway = gateway(log);
    List<ReactMetric> hostile = new AbstractList<>() {
      @Override
      public ReactMetric get(int index) {
        throw new IllegalStateException("get() is hostile");
      }

      @Override
      public int size() {
        return 1;
      }

      @Override
      public Iterator<ReactMetric> iterator() {
        throw new IllegalStateException("iterator() is hostile");
      }
    };

    Assertions.assertNull(gateway.declarationOf(source("guardianpets", hostile), "guardianpets", "GuardianPets"));
    Assertions.assertEquals(1L, gateway.totalFaults());
    Assertions.assertTrue(log.getFirst().contains("IllegalStateException"));
  }

  @Test
  void aMetricListWhoseSizeThrowsIsNeverAsked() {
    List<String> log = new ArrayList<>();
    MetricSourceGateway gateway = gateway(log);
    ReactMetric metric = ReactMetric.gauge("guardianpets.pets.live", "Live", "");
    List<ReactMetric> hostile = new AbstractList<>() {
      @Override
      public ReactMetric get(int index) {
        return metric;
      }

      @Override
      public int size() {
        throw new IllegalStateException("size() is hostile");
      }

      @Override
      public Iterator<ReactMetric> iterator() {
        return List.of(metric).iterator();
      }
    };

    List<ReactMetric> ours = gateway.declarationOf(source("guardianpets", hostile), "guardianpets", "GuardianPets");

    Assertions.assertEquals(List.of(metric), ours);
    Assertions.assertEquals(0L, gateway.totalFaults());
    Assertions.assertTrue(log.isEmpty());
  }

  @Test
  void anEndlessMetricListIsDrainedNoFurtherThanTheCap() {
    List<String> log = new ArrayList<>();
    MetricSourceGateway gateway = gateway(log);
    List<ReactMetric> endless = new AbstractList<>() {
      @Override
      public ReactMetric get(int index) {
        return ReactMetric.gauge("guardianpets.m" + index, "M", "");
      }

      @Override
      public int size() {
        return Integer.MAX_VALUE;
      }
    };

    List<ReactMetric> ours = gateway.declarationOf(source("guardianpets", endless), "guardianpets", "GuardianPets");

    Assertions.assertEquals(MetricSourceGateway.MAX_METRICS_PER_SOURCE, ours.size());
    Assertions.assertTrue(log.stream().anyMatch(line -> line.contains("declared more than")));
  }

  @Test
  void nullInputsAreSafe() {
    MetricSourceGateway gateway = gateway(new ArrayList<>());
    Assertions.assertEquals("", gateway.sourceIdOf(null, "Nobody"));
    Assertions.assertNull(gateway.declarationOf(null, "guardianpets", "Nobody"));
    Assertions.assertNull(gateway.declarationOf(source("guardianpets", List.of()), "", "Nobody"));
  }

  @Test
  void aSlowSourceIsWarnedAboutOnce() {
    List<String> log = new ArrayList<>();
    MetricSourceGateway gateway = new MetricSourceGateway(5, 1L, log::add);
    ReactMetricSource slow = new ReactMetricSource() {
      @Override
      public String sourceId() {
        return "guardianpets";
      }

      @Override
      public List<ReactMetric> metrics() {
        long until = System.nanoTime() + 3_000_000L;

        while (System.nanoTime() < until) {
          Thread.onSpinWait();
        }

        return List.of();
      }
    };

    gateway.declarationOf(slow, "guardianpets", "GuardianPets");
    gateway.declarationOf(slow, "guardianpets", "GuardianPets");

    Assertions.assertEquals(1, log.stream().filter(line -> line.contains("do not block")).count());
  }
}
