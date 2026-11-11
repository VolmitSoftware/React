package art.arcane.react.core.bridge;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.instrument.Instrumentation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class AgentGateTest {
  private final Deque<Runnable> attachThread = new ArrayDeque<>();
  private final Deque<Runnable> serverThread = new ArrayDeque<>();
  private final RecordingReport report = new RecordingReport();
  private final AtomicReference<String> remembered = new AtomicReference<>();

  @Test
  void manyHooksWhileAttachFailsMakeOneAttemptAndNeverRetry() {
    FakeAttachment attachment = FakeAttachment.failing(new IllegalStateException("Could not self-attach"));
    AgentGate gate = gate(attachment);
    AtomicInteger installed = new AtomicInteger();

    for (int hook = 0; hook < 5; hook++) {
      gate.whenAttached(installed::incrementAndGet);
    }
    Assertions.assertEquals(AgentGate.State.PENDING, gate.state());
    Assertions.assertEquals(1, attachThread.size());
    drain(attachThread);

    Assertions.assertEquals(AgentGate.State.UNAVAILABLE, gate.state());
    for (int hook = 0; hook < 5; hook++) {
      gate.whenAttached(installed::incrementAndGet);
      gate.request();
    }
    drain(attachThread);
    drain(serverThread);

    Assertions.assertEquals(1, attachment.attempts.get());
    Assertions.assertEquals(0, installed.get());
    Assertions.assertEquals(1, report.unavailable.size());
    Assertions.assertTrue(report.unavailable.get(0).contains("IllegalStateException"), report.unavailable.get(0));
    Assertions.assertEquals(1, report.failures.size());
    Assertions.assertFalse(gate.attached());
    Assertions.assertNull(gate.instrumentation());
  }

  @Test
  void requestReturnsBeforeTheAttemptRuns() {
    FakeAttachment attachment = FakeAttachment.failing(new IllegalStateException("slow"));
    AgentGate gate = gate(attachment);

    gate.request();

    Assertions.assertEquals(0, attachment.attempts.get());
    Assertions.assertEquals(AgentGate.State.PENDING, gate.state());
  }

  @Test
  void failureIsRememberedForLaterGatesInTheSameJvm() {
    FakeAttachment first = FakeAttachment.failing(new IllegalStateException("Could not self-attach"));
    gate(first).request();
    drain(attachThread);
    Assertions.assertNotNull(remembered.get());

    FakeAttachment second = FakeAttachment.failing(new IllegalStateException("must not run"));
    AgentGate reloaded = gate(second);
    reloaded.request();

    Assertions.assertEquals(AgentGate.State.UNAVAILABLE, reloaded.state());
    Assertions.assertTrue(attachThread.isEmpty());
    Assertions.assertEquals(0, second.attempts.get());
    Assertions.assertEquals(2, report.unavailable.size());
    Assertions.assertEquals(1, report.failures.size());
  }

  @Test
  void hooksQueuedDuringTheAttemptInstallOnTheServerThreadOnceAttached() {
    Instrumentation instrumentation = Mockito.mock(Instrumentation.class);
    FakeAttachment attachment = FakeAttachment.attaching(instrumentation);
    AgentGate gate = gate(attachment);
    AtomicInteger installed = new AtomicInteger();

    gate.whenAttached(installed::incrementAndGet);
    gate.whenAttached(installed::incrementAndGet);
    drain(attachThread);

    Assertions.assertEquals(AgentGate.State.ATTACHED, gate.state());
    Assertions.assertEquals(0, installed.get());
    Assertions.assertEquals(2, serverThread.size());
    drain(serverThread);
    Assertions.assertEquals(2, installed.get());

    gate.whenAttached(installed::incrementAndGet);
    Assertions.assertEquals(3, installed.get());
    Assertions.assertEquals(1, attachment.attempts.get());
    Assertions.assertEquals(1, report.attached.get());
    Assertions.assertSame(instrumentation, gate.instrumentation());
    Assertions.assertNull(remembered.get());
  }

  @Test
  void anAgentThatIsAlreadyLoadedNeedsNoAttempt() {
    Instrumentation instrumentation = Mockito.mock(Instrumentation.class);
    FakeAttachment attachment = FakeAttachment.failing(new IllegalStateException("must not run"));
    attachment.existing = instrumentation;
    AgentGate gate = gate(attachment);
    AtomicInteger installed = new AtomicInteger();

    Assertions.assertTrue(gate.attached());
    Assertions.assertEquals(AgentGate.State.IDLE, gate.state());

    gate.whenAttached(installed::incrementAndGet);

    Assertions.assertEquals(1, installed.get());
    Assertions.assertEquals(AgentGate.State.ATTACHED, gate.state());
    Assertions.assertTrue(attachThread.isEmpty());
    Assertions.assertEquals(0, attachment.attempts.get());
  }

  @Test
  void aHookThatThrowsDoesNotStopTheOthers() {
    FakeAttachment attachment = FakeAttachment.attaching(Mockito.mock(Instrumentation.class));
    AgentGate gate = gate(attachment);
    AtomicInteger installed = new AtomicInteger();

    gate.whenAttached(() -> {
      throw new IllegalStateException("hook failed");
    });
    gate.whenAttached(installed::incrementAndGet);
    drain(attachThread);
    drain(serverThread);

    Assertions.assertEquals(1, installed.get());
    Assertions.assertEquals(1, report.hookFailures.size());
  }

  private AgentGate gate(FakeAttachment attachment) {
    return new AgentGate(attachment, attachThread::add, serverThread::add,
        new AgentGate.Memory() {
          @Override
          public String recall() {
            return remembered.get();
          }

          @Override
          public void remember(String reason) {
            remembered.set(reason);
          }
        }, report);
  }

  private static void drain(Deque<Runnable> queue) {
    Runnable next;
    while ((next = queue.poll()) != null) {
      next.run();
    }
  }

  private static final class FakeAttachment implements AgentGate.Attachment {
    private final AtomicInteger attempts = new AtomicInteger();
    private final Instrumentation result;
    private final RuntimeException failure;
    private Instrumentation existing;

    private FakeAttachment(Instrumentation result, RuntimeException failure) {
      this.result = result;
      this.failure = failure;
    }

    static FakeAttachment failing(RuntimeException failure) {
      return new FakeAttachment(null, failure);
    }

    static FakeAttachment attaching(Instrumentation result) {
      return new FakeAttachment(result, null);
    }

    @Override
    public Instrumentation existing() {
      return existing;
    }

    @Override
    public Instrumentation attach() {
      attempts.incrementAndGet();
      if (failure != null) {
        throw failure;
      }
      return result;
    }
  }

  private static final class RecordingReport implements AgentGate.Report {
    private final AtomicInteger attached = new AtomicInteger();
    private final List<String> unavailable = new ArrayList<>();
    private final List<Throwable> failures = new ArrayList<>();
    private final List<Throwable> hookFailures = new ArrayList<>();

    @Override
    public void attached() {
      attached.incrementAndGet();
    }

    @Override
    public void unavailable(String reason) {
      unavailable.add(reason);
    }

    @Override
    public void attachFailure(Throwable failure) {
      failures.add(failure);
    }

    @Override
    public void hookFailure(Throwable failure) {
      hookFailures.add(failure);
    }
  }
}
