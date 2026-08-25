package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.core.incident.IncidentRecord;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@ConfigDescription("Stores structured incident evidence for the plugin and web viewer.")
public class IncidentController implements IController {
  private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

  @ConfigDoc(value = "Persists the structured incident history to plugins/React/incidents.json.", impact = "Disabling persistence keeps the current runtime history only.")
  private boolean persistenceEnabled = true;
  @ConfigDoc(value = "Maximum structured incident events retained.", impact = "Higher values preserve more history and use more memory and disk space.")
  private int retention = 256;

  private transient final Object lock;
  private transient final ArrayDeque<IncidentRecord> records;
  private transient final AtomicLong revision;
  private transient final AtomicBoolean persistQueued;
  private transient File storageFile;
  private transient ExecutorService writer;
  private transient volatile boolean stopping;

  public IncidentController() {
    lock = new Object();
    records = new ArrayDeque<>();
    revision = new AtomicLong();
    persistQueued = new AtomicBoolean();
  }

  IncidentController(File storageFile, int retention) {
    this();
    this.storageFile = storageFile;
    this.retention = retention;
  }

  @Override
  public String getId() {
    return "incident";
  }

  @Override
  public String getName() {
    return "Incident";
  }

  @Override
  public void start() {
    stopping = false;
    persistQueued.set(false);
    revision.set(0L);
    synchronized (lock) {
      records.clear();
    }
    if (storageFile == null && React.instance != null) {
      storageFile = React.instance.getDataFile("incidents.json");
    }
    load();
    writer = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "react-incident-writer");
      thread.setDaemon(true);
      return thread;
    });
  }

  @Override
  public void postStart() {
  }

  @Override
  public void stop() {
    stopping = true;
    schedulePersist();
    ExecutorService localWriter = writer;
    writer = null;
    if (localWriter == null) {
      return;
    }
    localWriter.shutdown();
    try {
      if (!localWriter.awaitTermination(10L, TimeUnit.SECONDS)) {
        React.warn("Timed out while flushing structured incident history");
      }
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      React.warn("Interrupted while flushing structured incident history", failure);
    }
  }

  public void record(IncidentRecord record) {
    if (record == null) {
      return;
    }
    synchronized (lock) {
      records.addLast(record);
      trimToRetention();
      revision.incrementAndGet();
    }
    schedulePersist();
  }

  public List<IncidentRecord> recent(int limit) {
    if (limit <= 0) {
      return List.of();
    }
    synchronized (lock) {
      int bounded = Math.min(Math.min(limit, effectiveRetention()), records.size());
      List<IncidentRecord> result = new ArrayList<>(bounded);
      Iterator<IncidentRecord> iterator = records.descendingIterator();
      while (iterator.hasNext() && result.size() < bounded) {
        result.add(iterator.next());
      }
      return List.copyOf(result);
    }
  }

  public int size() {
    synchronized (lock) {
      return records.size();
    }
  }

  private void load() {
    File file = storageFile;
    if (!persistenceEnabled || file == null || !file.isFile()) {
      return;
    }
    try {
      String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
      IncidentArchive archive = JSON.fromJson(content, IncidentArchive.class);
      if (archive == null || archive.records == null) {
        return;
      }
      synchronized (lock) {
        for (IncidentRecord record : archive.records) {
          if (record != null) {
            records.addLast(record);
          }
        }
        trimToRetention();
      }
    } catch (IOException | RuntimeException failure) {
      React.warn("Failed to load structured incident history from " + file.getAbsolutePath(), failure);
    }
  }

  private void schedulePersist() {
    ExecutorService localWriter = writer;
    if (!persistenceEnabled || storageFile == null || localWriter == null || persistQueued.get()) {
      return;
    }
    if (!persistQueued.compareAndSet(false, true)) {
      return;
    }
    try {
      localWriter.execute(this::drainPersistence);
    } catch (RejectedExecutionException failure) {
      persistQueued.set(false);
      if (!stopping) {
        React.warn("Structured incident writer rejected a persistence request", failure);
      }
    }
  }

  private void drainPersistence() {
    long writtenRevision = -1L;
    try {
      do {
        writtenRevision = revision.get();
        writeSnapshot(snapshot());
      } while (revision.get() != writtenRevision);
    } catch (IOException failure) {
      React.warn("Failed to persist structured incident history to " + storageFile.getAbsolutePath(), failure);
    } finally {
      persistQueued.set(false);
      if (!stopping && revision.get() != writtenRevision) {
        schedulePersist();
      }
    }
  }

  private List<IncidentRecord> snapshot() {
    synchronized (lock) {
      return List.copyOf(records);
    }
  }

  private void writeSnapshot(List<IncidentRecord> snapshot) throws IOException {
    Path target = storageFile.toPath().toAbsolutePath();
    Path parent = target.getParent();
    if (parent == null) {
      throw new IOException("Incident history path has no parent: " + target);
    }
    Files.createDirectories(parent);
    Path temporary = Files.createTempFile(parent, storageFile.getName(), ".tmp");
    try {
      Files.writeString(
          temporary,
          JSON.toJson(new IncidentArchive(snapshot)) + System.lineSeparator(),
          StandardCharsets.UTF_8
      );
      try {
        Files.move(
            temporary,
            target,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        );
      } catch (AtomicMoveNotSupportedException failure) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private void trimToRetention() {
    int maximum = effectiveRetention();
    while (records.size() > maximum) {
      records.removeFirst();
    }
  }

  private int effectiveRetention() {
    return Math.max(1, retention);
  }

  private static final class IncidentArchive {
    private List<IncidentRecord> records;

    private IncidentArchive(List<IncidentRecord> records) {
      this.records = records;
    }
  }
}
