package art.arcane.react.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

public final class ShorthandMessages {
  public static final TextKey GAME_MODE_DESCRIPTION = TextKey.of("tweak.shorthands.command.description.game_mode", "Switches the caster to {mode} mode.");
  public static final TextKey GIVE_DESCRIPTION = TextKey.of("tweak.shorthands.command.description.give", "Gives the caster a Minecraft item.");
  public static final TextKey MORE_DESCRIPTION = TextKey.of("tweak.shorthands.command.description.more", "Gives one maximum-size copy of the exact held item.");
  public static final TextKey RELOAD_DESCRIPTION = TextKey.of("tweak.shorthands.command.description.reload", "Invokes the server's bare reload command.");
  public static final TextKey PLAYER_ONLY = TextKey.of("tweak.shorthands.command.feedback.player_only", "&cThis command can only be used by a player.&r");
  public static final TextKey USAGE = TextKey.of("tweak.shorthands.command.feedback.usage", "&cUsage: {usage}&r");
  public static final TextKey GAME_MODE_SET = TextKey.of("tweak.shorthands.command.feedback.game_mode_set", "&aGame mode set to {mode}.&r");
  public static final TextKey ITEM_UNAVAILABLE = TextKey.of("tweak.shorthands.command.feedback.item_unavailable", "&cUnknown or unavailable item: {item}&r");
  public static final TextKey AMOUNT_OUT_OF_RANGE = TextKey.of("tweak.shorthands.command.feedback.amount_out_of_range", "&cAmount must be between 1 and {maximum}.&r");
  public static final TextKey ITEM_GIVEN = TextKey.of("tweak.shorthands.command.feedback.item_given", "&aGave {amount} {item}.&r");
  public static final TextKey MORE_EMPTY_HAND = TextKey.of("tweak.shorthands.command.feedback.more_empty_hand", "&cHold an item before using /more.&r");
  public static final TextKey MORE_GIVEN = TextKey.of("tweak.shorthands.command.feedback.more_given", "&aGave one exact stack of the held item.&r");
  public static final TextKey RELOAD_UNAVAILABLE = TextKey.of("tweak.shorthands.command.feedback.reload_unavailable", "&cThe server's /reload command is unavailable.&r");
  public static final TextKey RECURSIVE_STOPPED = TextKey.of("tweak.shorthands.command.feedback.recursive_stopped", "Stopped recursive shorthand /{label}.");
  public static final TextKey CONFIGURED_UNAVAILABLE = TextKey.of("tweak.shorthands.command.feedback.configured_unavailable", "The configured command for /{label} is unavailable.");
  public static final TextKey CONFIGURED_FAILED = TextKey.of("tweak.shorthands.command.feedback.configured_failed", "The configured command for /{label} failed.");

  private ShorthandMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(GAME_MODE_DESCRIPTION);
    builder.add(GIVE_DESCRIPTION);
    builder.add(MORE_DESCRIPTION);
    builder.add(RELOAD_DESCRIPTION);
    builder.add(PLAYER_ONLY);
    builder.add(USAGE);
    builder.add(GAME_MODE_SET);
    builder.add(ITEM_UNAVAILABLE);
    builder.add(AMOUNT_OUT_OF_RANGE);
    builder.add(ITEM_GIVEN);
    builder.add(MORE_EMPTY_HAND);
    builder.add(MORE_GIVEN);
    builder.add(RELOAD_UNAVAILABLE);
    builder.add(RECURSIVE_STOPPED);
    builder.add(CONFIGURED_UNAVAILABLE);
    builder.add(CONFIGURED_FAILED);
  }
}
