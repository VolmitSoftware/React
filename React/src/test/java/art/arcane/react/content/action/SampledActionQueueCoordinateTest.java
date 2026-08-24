package art.arcane.react.content.action;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.SamplerHopperUpdates;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.model.SampledServer;
import art.arcane.react.model.SampledWorld;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

class SampledActionQueueCoordinateTest {
  @Test
  void sampledQueueBuildersUseCoordinatesWithoutResolvingLiveWorldState() throws Exception {
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    Mockito.when(world.getKey()).thenReturn(NamespacedKey.minecraft("action_queue_identity"));

    SampledServer sampledServer = new SampledServer();
    SampledWorld sampledWorld = sampledServer.getWorld(world);
    SampledChunk sampledChunk = sampledWorld.getChunk(7, -9);
    sampledChunk.get("entities").set(120D);
    sampledChunk.get(SamplerHopperUpdates.ID).set(80D);
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(observer.getSampled()).thenReturn(sampledServer);
    Sampler hopperSampler = Mockito.mock(Sampler.class);
    Mockito.clearInvocations(world);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      react.when(() -> React.sampler(SamplerHopperUpdates.ID)).thenReturn(hopperSampler);

      ActionPrewarmCriticalChunks.Params prewarmParams = ActionPrewarmCriticalChunks.Params.builder()
          .includePlayerChunks(false)
          .build();
      List<?> prewarm = invokeQueue(new ActionPrewarmCriticalChunks(), "buildQueueSync", prewarmParams);

      ActionHopperNetworkNormalize.Params hopperParams = ActionHopperNetworkNormalize.Params.builder()
          .minimumHopperUpdatesPerChunk(1D)
          .build();
      List<?> hopper = invokeQueue(new ActionHopperNetworkNormalize(), "buildQueueSync", hopperParams);

      ActionTrimEntitiesByAgePriority.Params trimParams = ActionTrimEntitiesByAgePriority.Params.builder().build();
      List<?> trim = invokeQueue(new ActionTrimEntitiesByAgePriority(), "buildQueueSync", trimParams);

      ActionQuarantineHotChunks.Params quarantineParams = ActionQuarantineHotChunks.Params.builder()
          .minimumChunkScore(1D)
          .build();
      invokePrepare(new ActionQuarantineHotChunks(), quarantineParams);

      Assertions.assertFalse(prewarm.isEmpty());
      Assertions.assertFalse(hopper.isEmpty());
      Assertions.assertFalse(trim.isEmpty());
      Assertions.assertFalse(quarantineParams.getQueue().isEmpty());
      Mockito.verifyNoInteractions(world);
    }
  }

  private static List<?> invokeQueue(Object action, String methodName, Object params) throws Exception {
    Method method = action.getClass().getDeclaredMethod(methodName, params.getClass());
    method.setAccessible(true);
    return (List<?>) method.invoke(action, params);
  }

  private static void invokePrepare(ActionQuarantineHotChunks action, ActionQuarantineHotChunks.Params params)
      throws Exception {
    Method method = ActionQuarantineHotChunks.class.getDeclaredMethod("prepare", params.getClass());
    method.setAccessible(true);
    method.invoke(action, params);
  }
}
