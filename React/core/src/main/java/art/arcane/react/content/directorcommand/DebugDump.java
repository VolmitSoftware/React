package art.arcane.react.content.directorcommand;

import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.platform.ReactCommandSender;
import art.arcane.volmlib.util.diagnostics.BukkitDebugMessages;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.plugin.ComponentText;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@code /react debug dump} for platforms without VolmLib's Bukkit dump: captures on the server thread, writes and
 * uploads off it, and answers with the same mini menu and messages as Bukkit.
 */
public final class DebugDump {
  public static final String PERMISSION = "react.debugdump";
  private static final String COMMAND = "/react debug dump";
  private static final String PARENT_COMMAND = "/react";
  private static final int MAXIMUM_FILE_ATTEMPTS = 10_000;
  private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
  private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd-HH-mm-ss").withZone(ZoneOffset.UTC);
  private static final DirectorMiniMenu.Theme THEME = DirectorMiniMenu.Theme.reactBlue();

  /**
   * The platform side of a dump. {@link #capture} runs on the server thread; {@link #details} too.
   */
  public interface Host {
    DebugReport.Snapshot capture(ReactCommandSender sender);

    String details();

    boolean onServerThread(Runnable task);

    boolean async(Runnable task);

    URI upload(String report, String title, String source) throws IOException, InterruptedException;

    void info(String message);

    void warn(String message, Throwable failure);
  }

  private record Result(String path, String url, ComponentText notice, ComponentText error) {
    private static Result failure(ComponentText error) {
      return new Result("", "", ComponentText.empty(), error);
    }

    private boolean successful() {
      return error.plain().isEmpty();
    }
  }

  private final String name;
  private final String version;
  private final Host host;
  private final AtomicBoolean preparing = new AtomicBoolean();

  public DebugDump(String name, String version, Host host) {
    this.name = Objects.requireNonNull(name, "name");
    this.version = Objects.requireNonNull(version, "version");
    this.host = Objects.requireNonNull(host, "host");
  }

  public void request(ReactCommandSender sender, boolean upload) {
    Objects.requireNonNull(sender, "sender");
    if (!sender.hasPermission(PERMISSION)) {
      reply(sender, List.of(themed(text(sender, BukkitDebugMessages.MISSING_PERMISSION,
          MessageArgument.untrusted("permission", PERMISSION)), THEME.description())));
      return;
    }
    if (!preparing.compareAndSet(false, true)) {
      reply(sender, List.of(themed(text(sender, BukkitDebugMessages.ALREADY_PREPARING,
          MessageArgument.untrusted("plugin", name)), THEME.description())));
      return;
    }
    reply(sender, List.of(themed(text(sender, BukkitDebugMessages.PREPARING,
        MessageArgument.untrusted("plugin", name)), THEME.description())));
    if (!host.onServerThread(() -> capture(sender, upload))) {
      finish(sender, Result.failure(text(sender, BukkitDebugMessages.SCHEDULE_FAILED)));
    }
  }

  private void capture(ReactCommandSender sender, boolean upload) {
    DebugReport.Snapshot snapshot;
    String details;
    try {
      snapshot = host.capture(sender);
      details = details();
    } catch (RuntimeException | LinkageError failure) {
      host.warn("Unable to capture " + name + " diagnostic state", failure);
      finish(sender, Result.failure(text(sender, BukkitDebugMessages.CAPTURE_FAILED)));
      return;
    }
    if (!host.async(() -> writeAndUpload(sender, snapshot, details, upload))) {
      finish(sender, Result.failure(text(sender, BukkitDebugMessages.WRITER_SCHEDULE_FAILED)));
    }
  }

  private String details() {
    try {
      return Objects.requireNonNullElse(host.details(), "");
    } catch (RuntimeException failure) {
      host.warn("Unable to capture " + name + " diagnostic details", failure);
      return "Diagnostic capture failed: " + failure.getClass().getSimpleName();
    }
  }

  private void writeAndUpload(ReactCommandSender sender, DebugReport.Snapshot snapshot, String details, boolean upload) {
    String path;
    String report;
    try {
      report = DebugReport.create(snapshot, details);
      path = write(snapshot, report).toAbsolutePath().normalize().toString();
    } catch (IOException | RuntimeException failure) {
      host.warn("Unable to write " + name + " debug dump", failure);
      finish(sender, Result.failure(text(sender, BukkitDebugMessages.WRITE_FAILED)));
      return;
    }
    host.info("Saved debug dump: " + path);
    if (!upload) {
      finish(sender, new Result(path, "", ComponentText.empty(), ComponentText.empty()));
      return;
    }
    Result result;
    try {
      URI url = host.upload(report, "VolmitSoftware - " + name + " - v" + version, "VolmitSoftware/" + name + "/" + version);
      host.info("Debug dump uploaded: " + url);
      result = new Result(path, url.toString(), ComponentText.empty(), ComponentText.empty());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      host.warn(name + " debug dump upload was interrupted", interrupted);
      result = new Result(path, "", text(sender, BukkitDebugMessages.UPLOAD_INTERRUPTED), ComponentText.empty());
    } catch (IOException | RuntimeException failure) {
      host.warn("Unable to upload " + name + " debug dump to mclo.gs", failure);
      result = new Result(path, "", text(sender, BukkitDebugMessages.UPLOAD_FAILED), ComponentText.empty());
    }
    finish(sender, result);
  }

  private Path write(DebugReport.Snapshot snapshot, String report) throws IOException {
    Path directory = snapshot.dataDirectory().resolve("debug").normalize();
    if (Files.isSymbolicLink(directory)) {
      throw new IOException("Debug output cannot be a symbolic link: " + directory);
    }
    Files.createDirectories(directory);
    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("Debug output is not a regular directory: " + directory);
    }
    String baseName = fileToken(name) + "-v" + fileToken(version) + "-debugdump-" + FILE_TIME.format(snapshot.generatedAt());
    byte[] content = report.getBytes(StandardCharsets.UTF_8);
    for (int attempt = 1; attempt <= MAXIMUM_FILE_ATTEMPTS; attempt++) {
      Path target = directory.resolve(baseName + (attempt == 1 ? "" : "-" + attempt) + ".txt");
      try (FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
        ByteBuffer buffer = ByteBuffer.wrap(content);
        while (buffer.hasRemaining()) {
          channel.write(buffer);
        }
        channel.force(true);
        return target;
      } catch (FileAlreadyExistsException collision) {
        continue;
      }
    }
    throw new IOException("Unable to allocate a unique debug report in " + directory);
  }

  static String fileToken(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
  }

  private void finish(ReactCommandSender sender, Result result) {
    preparing.set(false);
    if (!result.successful()) {
      reply(sender, List.of(themed(result.error(), THEME.required())));
      return;
    }
    List<ComponentText> entries = new ArrayList<>();
    entries.add(themed(text(sender, BukkitDebugMessages.SAVED, MessageArgument.untrusted("plugin", name),
        MessageArgument.untrusted("path", result.path())), THEME.description()));
    entries.add(action(text(sender, BukkitDebugMessages.COPY_PATH), text(sender, BukkitDebugMessages.COPY_PATH_DESCRIPTION),
        result.path()).clickCopyToClipboard(result.path()));
    if (!result.notice().plain().isEmpty()) {
      entries.add(themed(result.notice(), THEME.required()));
    }
    if (!result.url().isEmpty()) {
      URI url = URI.create(result.url());
      entries.add(themed(text(sender, BukkitDebugMessages.UPLOADED_AS, MessageArgument.untrusted("plugin", name),
          MessageArgument.untrusted("version", version)), THEME.description()));
      entries.add(action(text(sender, BukkitDebugMessages.OPEN_LABEL, MessageArgument.untrusted("url", url)),
          text(sender, BukkitDebugMessages.OPEN_DESCRIPTION), url.toString()).clickOpenUrl(url));
    }
    reply(sender, entries);
  }

  private void reply(ReactCommandSender sender, List<ComponentText> content) {
    List<String> entries = new ArrayList<>(content.size());
    for (ComponentText line : content) {
      entries.add(entry(line));
    }
    UUID playerId = sender.playerId().orElse(null);
    Runnable delivery = () -> LanguageAudience.run(playerId, () -> deliver(sender, entries));
    if (!host.onServerThread(delivery)) {
      host.warn("Unable to schedule debug dump command feedback", new IllegalStateException(COMMAND));
    }
  }

  static void deliver(ReactCommandSender sender, List<String> entries) {
    DirectorMiniMenu.ContentMenu menu = new DirectorMiniMenu.ContentMenu(COMMAND, COMMAND, PARENT_COMMAND, entries, "", 1,
        Math.max(1, entries.size()));
    if (!sender.isPlayer()) {
      for (String line : DirectorMiniMenu.renderContentConsole(menu)) {
        if (line != null && !line.trim().isEmpty()) {
          sender.sendMessage(line);
        }
      }
      return;
    }
    sender.send(ComponentText.literal("\n".repeat(DirectorMiniMenu.CHAT_LINE_COUNT - 1)));
    for (String line : DirectorMiniMenu.renderContent(menu, THEME, ReactLanguage.directorResolver())) {
      if (line != null && !line.trim().isEmpty()) {
        sender.send(ComponentText.component(MINI_MESSAGE.deserialize(line)));
      }
    }
  }

  private static ComponentText themed(ComponentText text, String color) {
    return text.colorIfAbsent(color);
  }

  private static ComponentText action(ComponentText title, ComponentText description, String value) {
    ComponentText label = ComponentText.component(MINI_MESSAGE.deserialize(
        "<gradient:" + THEME.primaryLeft() + ":" + THEME.primaryRight() + ">" + title.miniMessage() + "</gradient>"));
    return label.hover(ComponentText.component(MINI_MESSAGE.deserialize(
        "<" + THEME.primaryRight() + ">" + title.miniMessage() + "</" + THEME.primaryRight() + "><reset>\n"
            + "<" + THEME.description() + ">✎ <font:minecraft:uniform>" + description.miniMessage()
            + "</font></" + THEME.description() + "><reset>\n"
            + "<" + THEME.optional() + ">✒ <font:minecraft:uniform>" + ComponentText.literal(value).miniMessage()
            + "</font></" + THEME.optional() + ">")));
  }

  private static String entry(ComponentText content) {
    return ComponentText.markup("<" + THEME.muted() + ">⇀</" + THEME.muted() + "> ").append(content).miniMessage();
  }

  private static ComponentText text(ReactCommandSender sender, TextKey key, MessageArgument... arguments) {
    MessageArgs.Builder values = MessageArgs.builder();
    for (MessageArgument argument : arguments) {
      values.add(argument);
    }
    MessageArgs built = values.build();
    return LanguageAudience.call(sender.playerId().orElse(null),
        () -> ComponentText.literal(ReactLanguage.directorResolver().resolve(key, built)));
  }
}
