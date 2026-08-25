package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.incident.IncidentEvidence;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SamplerIncidentScoreContributorsTest {
  @Test
  void capturesEightInputsAndTheirActualScorePoints() {
    Map<String, Sampler> samplers = samplers(true);
    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
      TestIncidentScore score = new TestIncidentScore(samplers);

      double value = score.onSample();
      List<IncidentEvidence> evidence = score.contributions();

      assertEquals(100D, value, 1.0E-9D);
      assertEquals(8, evidence.size());
      assertEquals(1D, evidence.stream().mapToDouble(IncidentEvidence::weight).sum(), 1.0E-9D);
      assertEquals(100D, evidence.stream().mapToDouble(IncidentEvidence::scorePoints).sum(), 1.0E-9D);
      assertTrue(evidence.stream().allMatch(IncidentEvidence::available));
    }
  }

  @Test
  void unavailableInputsAreExplicitAndAvailableWeightsAreRenormalized() {
    Map<String, Sampler> samplers = samplers(false);
    Sampler tick = samplers.get(SamplerTickMsP95.ID);
    when(tick.isSampleAvailable()).thenReturn(true);
    when(tick.sample()).thenReturn(150D);
    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
      TestIncidentScore score = new TestIncidentScore(samplers);

      double value = score.onSample();
      List<IncidentEvidence> evidence = score.contributions();

      assertEquals(100D, value, 1.0E-9D);
      assertTrue(score.isSampleAvailable());
      assertEquals(1, evidence.stream().filter(IncidentEvidence::available).count());
      assertEquals(100D, evidence.getFirst().scorePoints(), 1.0E-9D);
      assertFalse(evidence.get(1).available());
      assertEquals("Unavailable", evidence.get(1).display());
    }
  }

  @Test
  void webReadsTheCachedSnapshotWithoutResamplingInputs() {
    Map<String, Sampler> samplers = samplers(true);
    Sampler tick = samplers.get(SamplerTickMsP95.ID);
    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
      TestIncidentScore score = new TestIncidentScore(samplers);

      score.onSample();
      score.contributions();
      score.snapshot();

      verify(tick).sample();
    }
  }

  private Map<String, Sampler> samplers(boolean available) {
    Map<String, Double> maximums = Map.of(
        SamplerTickMsP95.ID, 150D,
        SamplerTickSpikeRate.ID, 120D,
        SamplerGcTimePercent.ID, 25D,
        SamplerSchedulerBacklog.ID, 300D,
        SamplerBacklogGrowthRate.ID, 80D,
        SamplerPlayerPingP95.ID, 350D,
        SamplerTopChunkCost.ID, 25D,
        SamplerRedstoneBurstRate.ID, 80D
    );
    Map<String, Sampler> samplers = new HashMap<>();
    for (Map.Entry<String, Double> entry : maximums.entrySet()) {
      Sampler sampler = mock(Sampler.class);
      when(sampler.isSampleAvailable()).thenReturn(available);
      when(sampler.sample()).thenReturn(entry.getValue());
      when(sampler.getName()).thenReturn(entry.getKey());
      when(sampler.formattedValue(entry.getValue())).thenReturn(Double.toString(entry.getValue()));
      when(sampler.formattedSuffix(entry.getValue())).thenReturn("");
      samplers.put(entry.getKey(), sampler);
    }
    return samplers;
  }

  private static final class TestIncidentScore extends SamplerIncidentScore {
    private final Map<String, Sampler> samplers;

    private TestIncidentScore(Map<String, Sampler> samplers) {
      this.samplers = samplers;
    }

    @Override
    public Sampler getSampler(String id) {
      return samplers.get(id);
    }
  }
}
