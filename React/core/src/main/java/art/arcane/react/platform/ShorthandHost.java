package art.arcane.react.platform;

import art.arcane.react.content.tweak.TweakShorthands;

public interface ShorthandHost {
  /**
   * Registers the shorthand commands the settings switch on and refreshes players' command lists. Cancelling the
   * subscription removes them again and restores any command they replaced. Runs on the command thread.
   *
   * @throws RuntimeException when registration failed; anything registered so far is removed first
   */
  Subscription register(TweakShorthands settings);

  /**
   * Runs the operation on the thread that owns the server's commands, now when already on it; false when it could not
   * be scheduled.
   */
  boolean runOnCommandThread(Runnable operation);
}
