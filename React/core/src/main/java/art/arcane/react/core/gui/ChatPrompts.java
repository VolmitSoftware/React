package art.arcane.react.core.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Free-text chat prompts that do not block a thread. The player's next chat message answers the prompt and never
 * reaches chat. A prompt nobody answers within {@link #TIMEOUT_TICKS} times out. The platform passes each chat
 * message of a player to {@link #onChat} and calls {@link #forget} when the player leaves.
 */
public final class ChatPrompts {
  public static final int TIMEOUT_TICKS = 20 * 120;

  private final ViewerTasks tasks;
  private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

  public ChatPrompts() {
    this(ViewerTasks.OWNER_THREAD);
  }

  ChatPrompts(ViewerTasks tasks) {
    this.tasks = tasks;
  }

  /**
   * Waits for the viewer's next chat message, replacing any prompt the viewer still has open. Both callbacks run on
   * the viewer's owner thread.
   */
  public void begin(UUID viewerId, Consumer<String> answered, Runnable timedOut) {
    if (viewerId == null || answered == null) {
      return;
    }
    Prompt prompt = new Prompt(answered, timedOut);
    prompts.put(viewerId, prompt);
    tasks.run(viewerId, () -> {
      if (prompts.remove(viewerId, prompt) && prompt.timedOut() != null) {
        prompt.timedOut().run();
      }
    }, TIMEOUT_TICKS);
  }

  public boolean pending(UUID viewerId) {
    return viewerId != null && prompts.containsKey(viewerId);
  }

  /**
   * Handles one chat message from any thread. True when the message answered a prompt and must not reach chat.
   */
  public boolean onChat(UUID viewerId, String message) {
    Prompt prompt = viewerId == null ? null : prompts.remove(viewerId);
    if (prompt == null) {
      return false;
    }
    String text = message == null ? "" : message;
    tasks.run(viewerId, () -> prompt.answered().accept(text), 0);
    return true;
  }

  public void forget(UUID viewerId) {
    if (viewerId != null) {
      prompts.remove(viewerId);
    }
  }

  public void clear() {
    prompts.clear();
  }

  private record Prompt(Consumer<String> answered, Runnable timedOut) {
  }
}
