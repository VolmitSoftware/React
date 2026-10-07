package art.arcane.react.content.action;

import art.arcane.react.React;
import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.api.action.ReactAction;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.ActionMessages;
import art.arcane.react.util.common.scheduling.ReactExecutors;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import art.arcane.volmlib.util.localization.MessageArgument;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@ConfigDescription("Captures an on-demand local JDK Flight Recorder profile with execution, allocation, and GC events. Retains three captures; never uploads recordings.")
public class ActionCaptureProfile extends ReactAction<ActionCaptureProfile.Params> {
  public static final String ID = "capture-profile";
  static final long MAX_RECORDING_BYTES = 64L * 1024L * 1024L;
  private static final Set<String> EXCLUDED_EVENTS = Set.of("jdk.InitialSystemProperty", "jdk.InitialEnvironmentVariable",
      "jdk.InitialSecurityProperty", "jdk.JVMInformation", "jdk.SystemProcess");
  private static final AtomicReference<Capture> ACTIVE = new AtomicReference<>();

  public ActionCaptureProfile() {
    super(ID);
  }

  @Override
  public void workOn(ActionTicket<Params> ticket) {
    synchronized (ticket) {
      if (ticket.isDone()) {
        return;
      }
      if (!isEnabled()) {
        ticket.fail(new IllegalStateException("Profile capture action is disabled"));
        return;
      }
      Params params = ticket.getParams();
      if (params.capture == null) {
        Capture capture = new Capture(Math.clamp(params.getSeconds(), 1, 300));
        if (!ACTIVE.compareAndSet(null, capture)) {
          ticket.fail(new IllegalStateException("A React profile capture is already active"));
          return;
        }
        params.capture = capture;
        params.setSeconds(capture.seconds);
        ticket.setTotalWork(capture.seconds);
        ticket.onTerminal(ignored -> cancel(capture));
        try {
          execute(() -> record(capture));
        } catch (RuntimeException failure) {
          ACTIVE.compareAndSet(capture, null);
          throw failure;
        }
        return;
      }
      Capture capture = params.capture;
      if (!capture.result.isDone()) {
        if (capture.startedNanos != 0L) {
          ticket.setWork((int) Math.min(capture.seconds, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - capture.startedNanos)));
        }
        return;
      }
      Path path = capture.result.join();
      params.setProfilePath(path.toString());
      ticket.setCount(1);
      ticket.setWork(ticket.getTotalWork());
      ticket.complete();
    }
  }

  @Override
  public String getCompletedMessage(ActionTicket<Params> ticket) {
    return ReactLanguage.plain(ActionMessages.PROFILE_CAPTURED,
        MessageArgument.untrusted("count", ticket.getCount()),
        MessageArgument.untrusted("path", ticket.getParams().getProfilePath()));
  }

  @Override
  public Params getDefaultParams() {
    return Params.builder().build();
  }

  @Override
  public void onInit() {
  }

  protected Path profileDirectory() {
    return React.instance.getDataFolder().toPath().resolve("diagnostics").resolve("profiles");
  }

  protected void execute(Runnable task) {
    ReactExecutors.async().execute(task);
  }

  protected Recording newRecording() throws IOException, ParseException {
    Recording recording = new Recording(Configuration.getConfiguration("profile"));
    recording.setName("React profile");
    recording.setToDisk(true);
    recording.setMaxSize(MAX_RECORDING_BYTES);
    recording.setMaxAge(Duration.ofSeconds(300));
    recording.setDumpOnExit(false);
    for (String event : EXCLUDED_EVENTS) {
      recording.disable(event);
    }
    return recording;
  }

  private void record(Capture capture) {
    if (!capture.claimed.compareAndSet(false, true)) {
      return;
    }
    Path temporary = null;
    Path filtered = null;
    Path destination = null;
    try {
      if (capture.cancel.getCount() == 0L) {
        return;
      }
      try (Recording recording = newRecording()) {
        if (capture.cancel.getCount() == 0L) {
          return;
        }
        recording.start();
        capture.startedNanos = System.nanoTime();
        if (capture.cancel.await(capture.seconds, TimeUnit.SECONDS)) {
          return;
        }
        recording.stop();
        Path directory = profileDirectory();
        Files.createDirectories(directory);
        temporary = Files.createTempFile(directory, ".capture-", ".tmp");
        recording.dump(temporary);
        if (Files.size(temporary) > MAX_RECORDING_BYTES) {
          throw new IOException("Profile exceeds the 64 MiB file limit");
        }
        if (capture.cancel.getCount() == 0L) {
          return;
        }
      }
      Path directory = temporary.getParent();
      filtered = Files.createTempFile(directory, ".filtered-", ".tmp");
      try (RecordingFile source = new RecordingFile(temporary)) {
        source.write(filtered, event -> !EXCLUDED_EVENTS.contains(event.getEventType().getName()));
      }
      if (Files.size(filtered) > MAX_RECORDING_BYTES) {
        throw new IOException("Filtered profile exceeds the 64 MiB file limit");
      }
      Files.delete(temporary);
      temporary = null;
      if (capture.cancel.getCount() == 0L) {
        return;
      }
      destination = directory.resolve("react-profile-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".jfr");
      Files.move(filtered, destination, StandardCopyOption.ATOMIC_MOVE);
      filtered = null;
      if (capture.cancel.getCount() != 0L) {
        prune(destination.getParent());
        capture.result.complete(destination);
      }
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      capture.result.completeExceptionally(failure);
    } catch (IOException | ParseException | RuntimeException failure) {
      capture.result.completeExceptionally(failure);
      React.reportError("Local profile capture failed", failure);
    } finally {
      removeIncomplete(temporary);
      removeIncomplete(filtered);
      if (capture.result.isCompletedExceptionally() || capture.cancel.getCount() == 0L && !capture.result.isDone()) {
        removeIncomplete(destination);
      }
      ACTIVE.compareAndSet(capture, null);
    }
  }

  private void cancel(Capture capture) {
    capture.cancel.countDown();
    if (capture.claimed.compareAndSet(false, true)) {
      ACTIVE.compareAndSet(capture, null);
    }
  }

  static void prune(Path directory) throws IOException {
    List<Path> profiles;
    try (Stream<Path> entries = Files.list(directory)) {
      profiles = entries.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
          .filter(path -> path.getFileName().toString().matches("react-profile-[0-9]+-[0-9a-f-]{36}\\.jfr"))
          .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
          .toList();
    }
    for (int index = 3; index < profiles.size(); index++) {
      Files.delete(profiles.get(index));
    }
  }

  private void removeIncomplete(Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException failure) {
      React.reportError("Could not remove incomplete profile " + path, failure);
    }
  }

  private static final class Capture {
    private final int seconds;
    private final CountDownLatch cancel = new CountDownLatch(1);
    private final AtomicBoolean claimed = new AtomicBoolean();
    private final CompletableFuture<Path> result = new CompletableFuture<>();
    private volatile long startedNanos;

    private Capture(int seconds) {
      this.seconds = seconds;
    }
  }

  @Builder
  @Data
  @Accessors(chain = true)
  public static class Params implements ActionParams {
    @Builder.Default
    @ConfigDoc(value = "Profile duration in seconds, clamped from 1 to 300.", impact = "Longer captures cover more activity; the recording retains at most 64 MiB of rolling JFR data.")
    private int seconds = 30;
    private transient Capture capture;
    private transient String profilePath;
  }
}
