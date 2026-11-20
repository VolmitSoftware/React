package art.arcane.react.content.action;

import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.api.action.ReactAction;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.content.sampler.SamplerEntities;
import art.arcane.react.content.sampler.SamplerHopperUpdates;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.platform.ChunkHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import lombok.Data;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

class ActionIncidentPlaybookChoiceTest {
  @AfterEach
  void unbind() {
    ReactPlatforms.unbind();
  }

  @Test
  void hopperStageConfiguresChildParamsByNameForTheHottestWorld() {
    List<SampledChunk> sampled = new ArrayList<>();
    sampled.add(chunk("minecraft:overworld", SamplerHopperUpdates.ID, 40D));
    sampled.add(chunk("minecraft:the_nether", SamplerEntities.ID, 120D));
    bind(sampled);
    StubAction hopper = new StubAction(ActionIncidentPlaybook.HOPPER_NORMALIZE_ID, HopperParams::new);
    StubAction trim = new StubAction(ActionIncidentPlaybook.TRIM_ENTITIES_ID, TrimParams::new);
    Map<String, Sampler> samplers = new HashMap<>();
    samplers.put(SamplerTickTime.ID, sampler(60D));
    samplers.put(SamplerIncidentScore.ID, sampler(0D));
    ActionIncidentPlaybook playbook = new ActionIncidentPlaybook();

    try (MockedStatic<SamplerHosts> hosts = Mockito.mockStatic(SamplerHosts.class);
         MockedStatic<ReactComponents> components = Mockito.mockStatic(ReactComponents.class)) {
      hosts.when(() -> SamplerHosts.sampler(Mockito.anyString())).thenAnswer(call -> samplers.get(call.getArgument(0)));
      components.when(() -> ReactComponents.action(hopper.getId())).thenReturn(hopper);
      components.when(() -> ReactComponents.action(trim.getId())).thenReturn(trim);

      ActionTicket<ActionIncidentPlaybook.Params> parent = playbook.create();
      parent.start();
      playbook.workOn(parent);

      HopperParams hopperParams = (HopperParams) parent.getParams().getActiveChild().getParams();
      Assertions.assertEquals(1, parent.getParams().getTier());
      Assertions.assertEquals("minecraft:overworld", hopperParams.getWorld());
      Assertions.assertEquals(20, hopperParams.getMaxChunks());
      Assertions.assertEquals(25D, hopperParams.getMinimumHopperUpdatesPerChunk());
      Assertions.assertEquals(48, hopperParams.getMaxMergedItemEntitiesPerChunk());
      Assertions.assertFalse(hopperParams.isUnloadIdleHotChunks());

      parent.getParams().getActiveChild().complete();
      playbook.workOn(parent);
      parent.getParams().setNextEvaluationNanos(0L);
      playbook.workOn(parent);

      TrimParams trimParams = (TrimParams) parent.getParams().getActiveChild().getParams();
      Assertions.assertEquals("minecraft:the_nether", trimParams.getWorld());
      Assertions.assertEquals(600, trimParams.getMaxTrim());
      Assertions.assertEquals(12, trimParams.getMaxTrimPerChunk());
      Assertions.assertEquals(80, trimParams.getMinimumEntitiesPerChunk());
      Assertions.assertEquals(20 * 60 * 5, trimParams.getMinEntityAgeTicks());
    }
  }

  private static SampledChunk chunk(String worldKey, String sampler, double value) {
    SampledChunk chunk = new SampledChunk(UUID.nameUUIDFromBytes(worldKey.getBytes()), worldKey, 0, 0);
    chunk.get(sampler).set(value);
    return chunk;
  }

  private static Sampler sampler(double value) {
    Sampler sampler = Mockito.mock(Sampler.class);
    Mockito.when(sampler.sample()).thenReturn(value);
    Mockito.when(sampler.isSampleAvailable()).thenReturn(true);
    return sampler;
  }

  private static void bind(List<SampledChunk> sampled) {
    ChunkHost chunks = Mockito.mock(ChunkHost.class);
    Mockito.when(chunks.sampledChunks()).thenReturn(sampled);
    ReactPlatform platform = Mockito.mock(ReactPlatform.class);
    Mockito.when(platform.chunkHost()).thenReturn(Optional.of(chunks));
    ReactPlatforms.bind(platform);
  }

  @Data
  static final class HopperParams implements ActionParams {
    private String world;
    private int maxChunks;
    private double minimumHopperUpdatesPerChunk;
    private int maxMergedItemEntitiesPerChunk;
    private boolean unloadIdleHotChunks = true;
  }

  @Data
  static final class TrimParams implements ActionParams {
    private String world;
    private int maxTrim;
    private int maxTrimPerChunk;
    private int minimumEntitiesPerChunk;
    private int minEntityAgeTicks;
  }

  private static final class StubAction extends ReactAction<ActionParams> {
    private final Supplier<ActionParams> defaults;

    private StubAction(String id, Supplier<ActionParams> defaults) {
      super(id);
      this.defaults = defaults;
    }

    @Override
    public void workOn(ActionTicket<ActionParams> ticket) {
    }

    @Override
    public ActionParams getDefaultParams() {
      return defaults.get();
    }

    @Override
    public void onInit() {
    }
  }
}
