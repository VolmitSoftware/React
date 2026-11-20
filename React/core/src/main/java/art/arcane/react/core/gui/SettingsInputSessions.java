package art.arcane.react.core.gui;

import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.GuiMessages;
import art.arcane.react.platform.SettingsAction;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageKey;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Chat prompts for settings values the menu cannot cycle, such as numbers and text. The platform passes each chat
 * message of a player here first and hides the message when it was consumed.
 */
public final class SettingsInputSessions {
  private final Supplier<ReactSettingsMenu> menu;
  private final IntSupplier timeoutSeconds;
  private final LongSupplier clock;
  private final Map<UUID, Pending> sessions = new ConcurrentHashMap<>();

  public SettingsInputSessions(Supplier<ReactSettingsMenu> menu, IntSupplier timeoutSeconds) {
    this(menu, timeoutSeconds, System::currentTimeMillis);
  }

  SettingsInputSessions(Supplier<ReactSettingsMenu> menu, IntSupplier timeoutSeconds, LongSupplier clock) {
    this.menu = menu;
    this.timeoutSeconds = timeoutSeconds;
    this.clock = clock;
  }

  public void begin(UUID viewerId, SettingsAction.Input input) {
    if (viewerId == null || input == null) {
      return;
    }
    long timeoutMs = Math.max(5, timeoutSeconds.getAsInt()) * 1000L;
    String label = input.label() == null ? input.valuePath() : input.label();
    sessions.put(viewerId, new Pending(input, label, clock.getAsLong() + timeoutMs));
    send(viewerId, GuiMessages.INPUT_ENTER_VALUE, MessageArgument.untrusted("label", label));
    send(viewerId, GuiMessages.INPUT_PATH, MessageArgument.untrusted("path", input.valuePath()));
    send(viewerId, GuiMessages.INPUT_EXPECTED_TYPE, MessageArgument.untrusted("type", ReactSettingsMenu.typeName(input.type())));
    send(viewerId, GuiMessages.INPUT_CANCEL_HINT);
  }

  public boolean pending(UUID viewerId) {
    return viewerId != null && sessions.containsKey(viewerId);
  }

  /**
   * Handles one chat message from any thread. True when the message answered a prompt and must not reach chat.
   */
  public boolean onChat(UUID viewerId, String rawMessage) {
    Pending pending = viewerId == null ? null : sessions.get(viewerId);
    if (pending == null) {
      return false;
    }
    SettingsAction.Input input = pending.input();
    if (clock.getAsLong() >= pending.expiresAt()) {
      sessions.remove(viewerId);
      onOwnerThread(viewerId, () -> {
        send(viewerId, GuiMessages.INPUT_TIMED_OUT);
        menu.get().open(viewerId, input.returnPath(), input.returnPage());
      });
      return true;
    }

    String message = rawMessage == null ? "" : rawMessage;
    if (message.equalsIgnoreCase("cancel")) {
      sessions.remove(viewerId);
      onOwnerThread(viewerId, () -> {
        send(viewerId, GuiMessages.INPUT_CANCELLED);
        menu.get().open(viewerId, input.returnPath(), input.returnPage());
      });
      return true;
    }

    ReactSettingsMenu.ParseResult parsed = ReactSettingsMenu.parseInputValue(input.type(), message);
    if (!parsed.success()) {
      onOwnerThread(viewerId, () -> {
        send(viewerId, GuiMessages.INPUT_INVALID, MessageArgument.untrusted("reason", parsed.error()));
        send(viewerId, GuiMessages.INPUT_RETRY);
      });
      return true;
    }

    sessions.remove(viewerId);
    Object value = parsed.value();
    onOwnerThread(viewerId, () -> menu.get().confirmAndApply(viewerId, input.returnPath(), input.returnPage(), input.valuePath(), value));
    return true;
  }

  public void forget(UUID viewerId) {
    if (viewerId != null) {
      sessions.remove(viewerId);
    }
  }

  public void clear() {
    sessions.clear();
  }

  private static void onOwnerThread(UUID viewerId, Runnable task) {
    Object handle = ReactSettingsMenu.handle(viewerId);
    if (handle != null) {
      J.runEntity(handle, task);
    }
  }

  private static void send(UUID viewerId, MessageKey key, MessageArgument... arguments) {
    Object handle = ReactSettingsMenu.handle(viewerId);
    if (handle != null) {
      ReactLanguage.send(handle, key, arguments);
    }
  }

  private record Pending(SettingsAction.Input input, String label, long expiresAt) {
  }
}
