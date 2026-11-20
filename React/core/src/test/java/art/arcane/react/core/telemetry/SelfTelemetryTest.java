package art.arcane.react.core.telemetry;

import art.arcane.react.api.metric.internal.PublishedMetricStore;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.HistoryController;
import art.arcane.react.core.controller.SampleController;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfTelemetryTest {
  @Test
  void registersEverySamplerAndReadsPlayerActivityAndMissingSources() {
    SampleController samples = Mockito.mock(SampleController.class);
    List<Sampler> registered = new ArrayList<>();
    Mockito.when(samples.registerSampler(Mockito.any())).thenAnswer(invocation -> {
      Sampler sampler = invocation.getArgument(0);
      sampler.start();
      registered.add(sampler);
      return true;
    });
    SelfTelemetry telemetry = new SelfTelemetry(new SelfTelemetry.Sources() {
      @Override
      public HistoryController history() {
        return null;
      }

      @Override
      public boolean webAvailable() {
        return true;
      }

      @Override
      public int activeWebSocketSessions() {
        return 3;
      }

      @Override
      public PublishedMetricStore publishedStore() {
        return null;
      }
    });
    telemetry.start();
    telemetry.registerReactSamplers(samples);
    telemetry.registerPlayerSamplers(samples);
    assertEquals(22, registered.size());
    long now = System.currentTimeMillis();
    telemetry.playerJoined(UUID.randomUUID(), now);
    telemetry.playerJoined(UUID.randomUUID(), now);
    assertEquals(2D, sampler(registered, "player-joins-rate").sample());
    assertEquals(2D, sampler(registered, "players-unique-24h").sample());
    assertEquals(3D, sampler(registered, "react-websocket-sessions").sample());
    assertTrue(sampler(registered, "react-websocket-sessions").isSampleAvailable());
    assertFalse(sampler(registered, "react-history-writer-queue").isSampleAvailable());
    assertFalse(sampler(registered, "react-published-metrics-accepted").isSampleAvailable());
    telemetry.stop(samples);
    Mockito.verify(samples, Mockito.times(22)).unregisterSampler(Mockito.anyString());
  }

  private static Sampler sampler(List<Sampler> samplers, String id) {
    for (Sampler sampler : samplers) {
      if (sampler.getId().equals(id)) {
        return sampler;
      }
    }
    throw new AssertionError(id);
  }
}
