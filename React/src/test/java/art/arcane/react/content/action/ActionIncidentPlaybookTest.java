package art.arcane.react.content.action;

import art.arcane.react.React;
import art.arcane.react.api.action.Action;
import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.api.action.ReactAction;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.SamplerEntities;
import art.arcane.react.content.sampler.SamplerGcTimePercent;
import art.arcane.react.content.sampler.SamplerHopperUpdates;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerMemoryGarbage;
import art.arcane.react.content.sampler.SamplerMemoryUsed;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.model.SampledServer;
import art.arcane.react.model.SampledWorld;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

class ActionIncidentPlaybookTest {
  @Test
  void drivesOneRelevantChildAndWaitsForItsRealCompletionWithoutQueueing() {
    try (Fixture fixture = new Fixture()) {
      fixture.hotspot("minecraft:overworld", 35D, 0D);
      StubAction hopper = fixture.action(ActionHopperNetworkNormalize.ID, 2);
      ActionTicket<ActionIncidentPlaybook.Params> parent = fixture.parent();
      fixture.playbook.workOn(parent);
      ActionTicket<?> child = parent.getParams().getActiveChild();
      Assertions.assertNotNull(child);
      Assertions.assertTrue(child.isRunning());
      Assertions.assertFalse(parent.isDone());
      Assertions.assertEquals(0, parent.getCount());
      Assertions.assertEquals(0, hopper.workCalls);
      fixture.playbook.workOn(parent);
      Assertions.assertFalse(child.isDone());
      fixture.playbook.workOn(parent);
      Assertions.assertTrue(child.isDone());
      Assertions.assertFalse(parent.isDone());
      Assertions.assertEquals(1, parent.getCount());
      fixture.playbook.workOn(parent);
      Assertions.assertEquals(2, hopper.workCalls);
      fixture.tick(30D);
      parent.getParams().setNextEvaluationNanos(0L);
      fixture.playbook.workOn(parent);
      Assertions.assertTrue(parent.isDone());
      Assertions.assertFalse(parent.isFailed());
      fixture.react.verify(() -> React.action(ActionPrewarmCriticalChunks.ID), Mockito.never());
      fixture.react.verify(() -> React.action(ActionCollectGarbage.ID), Mockito.never());
    }
  }

  @Test
  void reevaluatesPressureAndEvidenceBeforeEscalatingWithTargetedWorlds() {
    try (Fixture fixture = new Fixture()) {
      fixture.hotspot("minecraft:overworld", 35D, 0D);
      fixture.hotspot("minecraft:the_nether", 0D, 100D);
      fixture.action(ActionHopperNetworkNormalize.ID, 1);
      fixture.action(ActionTrimEntitiesByAgePriority.ID, 1);
      ActionTicket<ActionIncidentPlaybook.Params> parent = fixture.parent();
      fixture.playbook.workOn(parent);
      ActionHopperNetworkNormalize.Params hopper = (ActionHopperNetworkNormalize.Params) parent.getParams().getActiveChild().getParams();
      Assertions.assertEquals("minecraft:overworld", hopper.getWorld());
      Assertions.assertFalse(hopper.isUnloadIdleHotChunks());
      fixture.playbook.workOn(parent);
      parent.getParams().setNextEvaluationNanos(0L);
      fixture.playbook.workOn(parent);
      ActionTrimEntitiesByAgePriority.Params trim = (ActionTrimEntitiesByAgePriority.Params) parent.getParams().getActiveChild().getParams();
      Assertions.assertEquals("minecraft:the_nether", trim.getWorld());
      Assertions.assertEquals(80, trim.getMinimumEntitiesPerChunk());
      Assertions.assertEquals(1, parent.getCount());
      fixture.playbook.workOn(parent);
      fixture.tick(30D);
      parent.getParams().setNextEvaluationNanos(0L);
      fixture.playbook.workOn(parent);
      Assertions.assertTrue(parent.isDone());
      Assertions.assertEquals(2, parent.getCount());
    }
  }

  @Test
  void unavailablePressureAndHealthyServerNeverRunMitigationEvenWithTierOverride() {
    try (Fixture fixture = new Fixture()) {
      fixture.hotspot("minecraft:overworld", 500D, 200D);
      fixture.action(ActionHopperNetworkNormalize.ID, 1);
      fixture.tick(Double.NaN);
      ActionTicket<ActionIncidentPlaybook.Params> parent = fixture.parent();
      parent.getParams().setTierOverride(2);
      fixture.playbook.workOn(parent);
      Assertions.assertTrue(parent.isDone());
      Assertions.assertEquals(0, parent.getCount());
      fixture.react.verify(() -> React.action(Mockito.anyString()), Mockito.never());
    }
  }

  @Test
  void worldFilterAndMissingEvidenceExcludeUnrelatedMitigations() {
    try (Fixture fixture = new Fixture()) {
      fixture.hotspot("minecraft:the_nether", 500D, 200D);
      fixture.action(ActionHopperNetworkNormalize.ID, 1);
      ActionTicket<ActionIncidentPlaybook.Params> parent = fixture.parent();
      parent.getParams().setWorld("minecraft:overworld");
      fixture.playbook.workOn(parent);
      Assertions.assertTrue(parent.isDone());
      Assertions.assertEquals(0, parent.getCount());
      fixture.react.verify(() -> React.action(Mockito.anyString()), Mockito.never());
    }
  }

  @Test
  void childFailuresTimeoutsAndParentCancellationNeverReportSuccessfulMitigation() {
    try (Fixture fixture = new Fixture()) {
      fixture.hotspot("minecraft:overworld", 35D, 0D);
      StubAction action = fixture.action(ActionHopperNetworkNormalize.ID, Integer.MAX_VALUE);
      ActionTicket<ActionIncidentPlaybook.Params> parent = fixture.parent();
      fixture.playbook.workOn(parent);
      ActionTicket<?> child = parent.getParams().getActiveChild();
      parent.getParams().setChildStartedNanos(System.nanoTime() - TimeUnit.SECONDS.toNanos(61));
      fixture.playbook.workOn(parent);
      Assertions.assertTrue(child.isFailed());
      Assertions.assertTrue(parent.isFailed());
      Assertions.assertEquals(0, parent.getCount());
      Assertions.assertEquals(0, action.workCalls);

      ActionTicket<ActionIncidentPlaybook.Params> canceled = fixture.parent();
      fixture.playbook.workOn(canceled);
      ActionTicket<?> canceledChild = canceled.getParams().getActiveChild();
      canceled.fail(new IllegalStateException("controller stopped"));
      Assertions.assertTrue(canceledChild.isFailed());
      fixture.playbook.workOn(canceled);
      Assertions.assertEquals(0, action.workCalls);

      ActionTicket<ActionIncidentPlaybook.Params> failed = fixture.parent();
      fixture.playbook.workOn(failed);
      IllegalStateException cause = new IllegalStateException("chunk failure");
      failed.getParams().getActiveChild().fail(cause);
      fixture.playbook.workOn(failed);
      Assertions.assertTrue(failed.isFailed());
      Assertions.assertSame(cause, failed.getFailure().getCause());
    }
  }

  @Test
  void explicitGarbageCollectionRequiresHeapAndGcEvidence() {
    try (Fixture fixture = new Fixture()) {
      fixture.action(ActionCollectGarbage.ID, 1);
      double heap = Runtime.getRuntime().maxMemory();
      fixture.metric(SamplerMemoryUsed.ID, heap * 0.95D);
      fixture.metric(SamplerMemoryGarbage.ID, heap * 0.2D);
      fixture.metric(SamplerGcTimePercent.ID, 3D);
      ActionTicket<ActionIncidentPlaybook.Params> parent = fixture.parent();
      parent.getParams().setIncludeGarbageCollection(true);
      fixture.playbook.workOn(parent);
      Assertions.assertTrue(parent.isDone());
      fixture.metric(SamplerGcTimePercent.ID, 1D);
      ActionTicket<ActionIncidentPlaybook.Params> eligible = fixture.parent();
      eligible.getParams().setIncludeGarbageCollection(true);
      fixture.playbook.workOn(eligible);
      Assertions.assertEquals(ActionCollectGarbage.ID, eligible.getParams().getActiveChild().getAction().getId());
    }
  }

  private static final class Fixture implements AutoCloseable {
    private final MockedStatic<React> react = Mockito.mockStatic(React.class);
    private final ActionIncidentPlaybook playbook = new ActionIncidentPlaybook();
    private final SampledServer sampled = new SampledServer();
    private final Map<String, Sampler> metrics = new HashMap<>();
    private final Map<String, Action<?>> actions = new HashMap<>();

    private Fixture() {
      ObserverController observer = Mockito.mock(ObserverController.class);
      Mockito.when(observer.getSampled()).thenReturn(sampled);
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      react.when(() -> React.sampler(Mockito.anyString())).thenAnswer(invocation -> metrics.get(invocation.getArgument(0)));
      react.when(() -> React.action(Mockito.anyString())).thenAnswer(invocation -> actions.get(invocation.getArgument(0)));
      tick(60D);
      metric(SamplerIncidentScore.ID, 0D);
    }

    private void hotspot(String key, double hoppers, double entities) {
      SampledWorld world = new SampledWorld(UUID.randomUUID(), key);
      world.getChunk(0, 0).get(SamplerHopperUpdates.ID).set(hoppers);
      world.getChunk(0, 0).gauge(SamplerEntities.ID).set(entities);
      sampled.getWorlds().put(world.getWorldId(), world);
    }

    private StubAction action(String id, int finishAfter) {
      StubAction action = new StubAction(id, finishAfter);
      actions.put(id, action);
      return action;
    }

    private ActionTicket<ActionIncidentPlaybook.Params> parent() {
      ActionTicket<ActionIncidentPlaybook.Params> parent = playbook.create();
      parent.start();
      return parent;
    }

    private void tick(double value) {
      metric(SamplerTickTime.ID, value);
    }

    private void metric(String id, double value) {
      Sampler sampler = Mockito.mock(Sampler.class);
      Mockito.when(sampler.sample()).thenReturn(value);
      Mockito.when(sampler.isSampleAvailable()).thenReturn(true);
      metrics.put(id, sampler);
    }

    @Override
    public void close() {
      react.close();
    }
  }

  private static final class StubAction extends ReactAction<ActionParams> {
    private final int finishAfter;
    private int workCalls;

    private StubAction(String id, int finishAfter) {
      super(id);
      this.finishAfter = finishAfter;
    }

    @Override
    public void workOn(ActionTicket<ActionParams> ticket) {
      workCalls++;
      if (workCalls >= finishAfter) {
        ticket.setCount(7);
        ticket.complete();
      }
    }

    @Override
    public ActionParams getDefaultParams() {
      return ActionIncidentPlaybook.Params.builder().build();
    }

    @Override
    public void onInit() {
    }
  }
}
