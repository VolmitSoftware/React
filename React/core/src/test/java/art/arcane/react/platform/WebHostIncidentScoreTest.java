package art.arcane.react.platform;

import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.core.controller.SampleController;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebHostIncidentScoreTest {
  @Test
  void noSampleControllerReportsTheScoreUnavailable() {
    assertSame(WebHost.IncidentScore.EMPTY, WebHost.IncidentScore.sample(null));
  }

  @Test
  void anUnregisteredSamplerReportsTheScoreUnavailable() {
    SampleController samples = mock(SampleController.class);

    assertSame(WebHost.IncidentScore.EMPTY, WebHost.IncidentScore.sample(samples));
  }

  @Test
  void theRegisteredSamplerSuppliesTheScore() {
    SampleController samples = mock(SampleController.class);
    SamplerIncidentScore sampler = mock(SamplerIncidentScore.class);
    when(samples.getSampler(SamplerIncidentScore.ID)).thenReturn(sampler);
    when(sampler.snapshot()).thenReturn(new SamplerIncidentScore.IncidentScoreSnapshot(1234L, 42.5D, true, List.of()));

    WebHost.IncidentScore score = WebHost.IncidentScore.sample(samples);

    assertTrue(score.available());
    assertEquals(42.5D, score.score(), 1.0E-9D);
    assertEquals(1234L, score.sampledAtMs());
  }
}
