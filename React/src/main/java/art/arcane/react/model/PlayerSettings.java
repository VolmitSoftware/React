/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.model;

import art.arcane.react.React;
import art.arcane.react.api.monitor.configuration.MonitorConfiguration;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import lombok.Data;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Data
public class PlayerSettings {
  private static final Gson JSON = new Gson();
  private static final int MAX_WRITE_ATTEMPTS = 3;
  private static final Map<UUID, PendingSave> PENDING_SAVES = new ConcurrentHashMap<>();
  private static final Map<UUID, PendingSave> IN_FLIGHT_SAVES = new ConcurrentHashMap<>();
  private static final Map<UUID, PendingSave> FAILED_SAVES = new ConcurrentHashMap<>();
  private static final AtomicBoolean SAVE_DRAIN_RUNNING = new AtomicBoolean(false);
  private static final Object SAVE_DRAIN_MONITOR = new Object();

  private MonitorConfiguration monitorConfiguration;
  private boolean actionBarMonitoring = false;
  private boolean visualizing = false;

  public static void saveSettings(UUID player, PlayerSettings s) {
    if (player == null || s == null) {
      return;
    }

    try {
      File target = React.instance.getDataFile("player-settings", player + ".json");
      String content = new JSONObject(JSON.toJson(s)).toString(4);
      FAILED_SAVES.remove(player);
      PENDING_SAVES.put(player, new PendingSave(target, content, 1));
      scheduleSaveDrain();
    } catch (Throwable throwable) {
      React.reportError(new IllegalStateException("Failed to queue player settings for " + player, throwable));
    }
  }

  public static PlayerSettings get(UUID player) {
    PlayerSettings fallback = new PlayerSettings();
    PendingSave queued = PENDING_SAVES.get(player);
    if (queued == null) {
      queued = IN_FLIGHT_SAVES.get(player);
    }
    if (queued == null) {
      queued = FAILED_SAVES.get(player);
    }
    if (queued != null) {
      return parse(queued.content, fallback, player);
    }

    File l = React.instance.getDataFile("player-settings", player.toString() + ".json");
    Path recovery = recoveryPath(l.toPath().toAbsolutePath());
    if (Files.isRegularFile(recovery)) {
      try {
        String content = Files.readString(recovery, StandardCharsets.UTF_8);
        PlayerSettings recovered = JSON.fromJson(content, PlayerSettings.class);
        if (recovered != null) {
          PENDING_SAVES.put(player, new PendingSave(l, content.stripTrailing(), 1));
          scheduleSaveDrain();
          return recovered;
        }
      } catch (IOException | JsonParseException e) {
        React.reportError(new IllegalStateException(
            "Failed to recover staged player settings for " + player + " from " + recovery,
            e
        ));
      }
    }

    if (!l.exists()) {
      saveSettings(player, fallback);
      return fallback;
    }

    try {
      return parse(IO.readAll(l), fallback, player);
    } catch (IOException | JsonParseException e) {
      React.reportError(new IllegalStateException("Failed to read player settings for " + player, e));
      return fallback;
    }
  }

  public static boolean flushPendingSaves(long timeoutMillis) {
    retryFailedSaves();
    scheduleSaveDrain();
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
    synchronized (SAVE_DRAIN_MONITOR) {
      while (!PENDING_SAVES.isEmpty() || !IN_FLIGHT_SAVES.isEmpty() || SAVE_DRAIN_RUNNING.get()) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0L) {
          return false;
        }

        try {
          TimeUnit.NANOSECONDS.timedWait(SAVE_DRAIN_MONITOR, remaining);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
    }
    return FAILED_SAVES.isEmpty();
  }

  public void toggleVisualizing() {
    visualizing = !visualizing;
  }

  public boolean isVisualizing() {
    return visualizing;
  }

  public void init() {
    if (monitorConfiguration == null) {
      monitorConfiguration = JSON.fromJson(JSON.toJson(ReactConfiguration.get().getMonitoring().getMonitorConfiguration()), MonitorConfiguration.class);
    }
  }

  private static PlayerSettings parse(String content, PlayerSettings fallback, UUID player) {
    try {
      PlayerSettings configuration = JSON.fromJson(content, PlayerSettings.class);
      return configuration == null ? fallback : configuration;
    } catch (JsonParseException exception) {
      React.reportError(new IllegalStateException("Failed to parse player settings for " + player, exception));
      return fallback;
    }
  }

  private static void scheduleSaveDrain() {
    if (PENDING_SAVES.isEmpty() || !SAVE_DRAIN_RUNNING.compareAndSet(false, true)) {
      return;
    }

    try {
      J.a(PlayerSettings::drainPendingSaves);
    } catch (Throwable throwable) {
      SAVE_DRAIN_RUNNING.set(false);
      signalSaveDrain();
      React.reportError(new IllegalStateException("Failed to schedule the player settings write queue", throwable));
    }
  }

  private static void drainPendingSaves() {
    try {
      while (!PENDING_SAVES.isEmpty()) {
        for (Map.Entry<UUID, PendingSave> entry : new ArrayList<>(PENDING_SAVES.entrySet())) {
          UUID player = entry.getKey();
          PendingSave save = entry.getValue();
          IN_FLIGHT_SAVES.put(player, save);
          if (!PENDING_SAVES.remove(player, save)) {
            IN_FLIGHT_SAVES.remove(player, save);
            continue;
          }

          try {
            writeAtomically(save.target, save.content);
            FAILED_SAVES.remove(player);
          } catch (IOException exception) {
            if (save.attempt < MAX_WRITE_ATTEMPTS) {
              PENDING_SAVES.putIfAbsent(player, save.nextAttempt());
            } else {
              FAILED_SAVES.put(player, save);
              React.reportError(new IllegalStateException(
                  "Failed to persist player settings for " + player + " after " + save.attempt + " attempts",
                  exception
              ));
            }
          } finally {
            IN_FLIGHT_SAVES.remove(player, save);
            signalSaveDrain();
          }
        }
      }
    } finally {
      SAVE_DRAIN_RUNNING.set(false);
      signalSaveDrain();
      if (!PENDING_SAVES.isEmpty()) {
        scheduleSaveDrain();
      }
    }
  }

  static void writeAtomically(File target, String content) throws IOException {
    Path targetPath = target.toPath().toAbsolutePath();
    Path parent = targetPath.getParent();
    if (parent == null) {
      throw new IOException("Player settings path has no parent: " + targetPath);
    }

    Files.createDirectories(parent);
    Path temporary = Files.createTempFile(parent, target.getName(), ".tmp");
    Path recovery = recoveryPath(targetPath);
    try {
      ByteBuffer bytes = StandardCharsets.UTF_8.encode(content + System.lineSeparator());
      try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
        while (bytes.hasRemaining()) {
          channel.write(bytes);
        }
        channel.force(true);
      }
      moveReplacing(temporary, recovery);
      moveReplacing(recovery, targetPath);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  static Path recoveryPath(Path targetPath) {
    return targetPath.resolveSibling(targetPath.getFileName() + ".pending");
  }

  private static void moveReplacing(Path source, Path target) throws IOException {
    try {
      Files.move(
          source,
          target,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING
      );
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private record PendingSave(File target, String content, int attempt) {
    private PendingSave nextAttempt() {
      return new PendingSave(target, content, attempt + 1);
    }

    private PendingSave retry() {
      return new PendingSave(target, content, 1);
    }
  }

  private static void retryFailedSaves() {
    for (Map.Entry<UUID, PendingSave> entry : new ArrayList<>(FAILED_SAVES.entrySet())) {
      UUID player = entry.getKey();
      PendingSave failed = entry.getValue();
      if (FAILED_SAVES.remove(player, failed)) {
        PENDING_SAVES.putIfAbsent(player, failed.retry());
      }
    }
  }

  private static void signalSaveDrain() {
    synchronized (SAVE_DRAIN_MONITOR) {
      SAVE_DRAIN_MONITOR.notifyAll();
    }
  }
}
