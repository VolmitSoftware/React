package art.arcane.react.content.action;

import art.arcane.react.api.action.ActionTicket;
import jdk.jfr.Recording;
import jdk.jfr.Configuration;
import jdk.jfr.RecordingState;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

class ActionCaptureProfileTest {
  @TempDir
  Path directory;
  private static volatile byte[] allocation;

  @Test
  void writesReadableLocalProfileWithBoundedSettingsAndNoEnvironmentEvents() throws Exception {
    TestAction action = new TestAction(directory);
    ActionTicket<ActionCaptureProfile.Params> ticket = action.create(action.getDefaultParams().setSeconds(1));
    ticket.start();
    try (Recording concurrent = new Recording(Configuration.getConfiguration("profile"))) {
      concurrent.setMaxSize(ActionCaptureProfile.MAX_RECORDING_BYTES);
      concurrent.start();
      action.workOn(ticket);
      awaitStarted(action);
      Recording recording = action.recording.get();
      Assertions.assertEquals(ActionCaptureProfile.MAX_RECORDING_BYTES, recording.getMaxSize());
      Assertions.assertEquals(Duration.ofSeconds(300), recording.getMaxAge());
      Assertions.assertEquals("true", recording.getSettings().get("jdk.ExecutionSample#enabled"));
      Assertions.assertEquals("true", recording.getSettings().get("jdk.ObjectAllocationSample#enabled"));
      Assertions.assertFalse(recording.getDumpOnExit());
      while (recording.getState() == RecordingState.RUNNING) {
        allocation = new byte[32 * 1024];
        for (int index = 0; index < allocation.length; index++) {
          allocation[index] = (byte) (index * index);
        }
      }
      action.worker.join(10_000);
      Assertions.assertFalse(action.worker.isAlive());
      action.workOn(ticket);
      Assertions.assertTrue(ticket.isDone());
      Assertions.assertFalse(ticket.isFailed());
      Assertions.assertEquals(1, ticket.getCount());
      Path result = Path.of(ticket.getParams().getProfilePath());
      Assertions.assertEquals(directory, result.getParent());
      Set<String> events = new HashSet<>();
      try (RecordingFile file = new RecordingFile(result)) {
        while (file.hasMoreEvents()) {
          RecordedEvent event = file.readEvent();
          events.add(event.getEventType().getName());
        }
      }
      Assertions.assertTrue(events.contains("jdk.ExecutionSample"), events.toString());
      Assertions.assertTrue(events.contains("jdk.ObjectAllocationSample"), events.toString());
      Assertions.assertFalse(events.contains("jdk.InitialSystemProperty"));
      Assertions.assertFalse(events.contains("jdk.InitialEnvironmentVariable"));
      Assertions.assertFalse(events.contains("jdk.JVMInformation"));
      Assertions.assertEquals(RecordingState.RUNNING, concurrent.getState());
      Path otherProfile = directory.resolve("other-recorder.jfr");
      concurrent.dump(otherProfile);
      boolean otherHasEnvironment = false;
      try (RecordingFile file = new RecordingFile(otherProfile)) {
        while (file.hasMoreEvents()) {
          if (file.readEvent().getEventType().getName().equals("jdk.InitialSystemProperty")) {
            otherHasEnvironment = true;
          }
        }
      }
      Assertions.assertTrue(otherHasEnvironment, "Concurrent recorder must retain its own settings and events");
      Files.delete(otherProfile);
      Assertions.assertEquals(RecordingState.CLOSED, recording.getState());
      Assertions.assertEquals(1, files().size());
    } finally {
      ticket.fail(new IllegalStateException("Test cleanup"));
      if (action.worker != null) action.worker.join(10_000);
    }
  }

  @Test
  void cancellationClosesRecorderRejectsOverlapAcrossInstancesAndCannotCompleteLate() throws Exception {
    TestAction action = new TestAction(directory);
    ActionTicket<ActionCaptureProfile.Params> first = action.create(action.getDefaultParams().setSeconds(1000));
    first.start();
    try {
      action.workOn(first);
      awaitStarted(action);
      Assertions.assertEquals(300, first.getParams().getSeconds());
      TestAction other = new TestAction(directory);
      ActionTicket<ActionCaptureProfile.Params> overlap = other.create();
      overlap.start();
      other.workOn(overlap);
      Assertions.assertTrue(overlap.isFailed());
      Assertions.assertNull(other.worker);
      first.fail(new IllegalStateException("Stopped"));
      action.worker.join(10_000);
      Assertions.assertFalse(action.worker.isAlive());
      Assertions.assertEquals(RecordingState.CLOSED, action.recording.get().getState());
      action.workOn(first);
      Assertions.assertTrue(first.isFailed());
      Assertions.assertNull(first.getParams().getProfilePath());
      Assertions.assertEquals(0, first.getCount());
      Assertions.assertTrue(files().isEmpty());
    } finally {
      first.fail(new IllegalStateException("Test cleanup"));
      if (action.worker != null) action.worker.join(10_000);
    }
  }

  @Test
  void cancellationBeforeDispatchNeverCreatesRecorderAndReleasesClaim() {
    TestAction action = new TestAction(directory);
    action.defer = true;
    ActionTicket<ActionCaptureProfile.Params> ticket = action.create(action.getDefaultParams().setSeconds(-5));
    ticket.start();
    action.workOn(ticket);
    Assertions.assertEquals(1, ticket.getParams().getSeconds());
    Runnable oldWorker = action.pending;
    ticket.fail(new IllegalStateException("Stopped before dispatch"));
    Assertions.assertNull(action.recording.get());
    ActionTicket<ActionCaptureProfile.Params> next = action.create();
    next.start();
    action.workOn(next);
    Assertions.assertFalse(next.isFailed());
    oldWorker.run();
    Assertions.assertNull(action.recording.get());
    next.fail(new IllegalStateException("Cleanup"));
    action.pending.run();
  }

  @Test
  void unwritableOutputFailsWithoutPublishingAPathAndReleasesRecorder() throws Exception {
    Path occupied = Files.writeString(directory.resolve("occupied"), "file");
    TestAction action = new TestAction(occupied);
    ActionTicket<ActionCaptureProfile.Params> ticket = action.create(action.getDefaultParams().setSeconds(1));
    ticket.start();
    try {
      action.workOn(ticket);
      action.worker.join(20_000);
      Assertions.assertFalse(action.worker.isAlive());
      Assertions.assertThrows(CompletionException.class, () -> action.workOn(ticket));
      Assertions.assertNull(ticket.getParams().getProfilePath());
      Assertions.assertEquals(RecordingState.CLOSED, action.recording.get().getState());
      Assertions.assertEquals(List.of(occupied), files());
    } finally {
      ticket.fail(new IllegalStateException("Test cleanup"));
      if (action.worker != null) action.worker.join(10_000);
    }
  }

  @Test
  void retentionRemovesOnlyOlderOwnedCaptures() throws IOException {
    for (int index = 0; index < 5; index++) {
      Files.writeString(directory.resolve("react-profile-170000000000" + index + "-" + UUID.randomUUID() + ".jfr"), "capture");
    }
    Path unrelated = Files.writeString(directory.resolve("operator.jfr"), "keep");
    ActionCaptureProfile.prune(directory);
    List<Path> kept = files();
    Assertions.assertEquals(4, kept.size());
    Assertions.assertTrue(Files.exists(unrelated));
    Assertions.assertFalse(kept.stream().anyMatch(path -> path.getFileName().toString().startsWith("react-profile-1700000000000-")));
    Assertions.assertFalse(kept.stream().anyMatch(path -> path.getFileName().toString().startsWith("react-profile-1700000000001-")));
  }

  private void awaitStarted(TestAction action) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
    while (action.recording.get() == null || action.recording.get().getState() == RecordingState.NEW) {
      Assertions.assertTrue(System.nanoTime() < deadline, "Recording did not start");
      Thread.sleep(10);
    }
    Assertions.assertEquals(RecordingState.RUNNING, action.recording.get().getState());
  }

  private List<Path> files() throws IOException {
    try (Stream<Path> entries = Files.list(directory)) {
      return entries.toList();
    }
  }

  private static final class TestAction extends ActionCaptureProfile {
    private final Path directory;
    private final AtomicReference<Recording> recording = new AtomicReference<>();
    private Thread worker;
    private boolean defer;
    private Runnable pending;

    private TestAction(Path directory) {
      this.directory = directory;
    }

    @Override
    protected Path profileDirectory() {
      return directory;
    }

    @Override
    protected void execute(Runnable task) {
      if (defer) {
        pending = task;
        return;
      }
      worker = new Thread(task, "profile-capture-test");
      worker.setDaemon(true);
      worker.start();
    }

    @Override
    protected Recording newRecording() throws IOException, ParseException {
      Recording created = super.newRecording();
      recording.set(created);
      return created;
    }
  }
}
