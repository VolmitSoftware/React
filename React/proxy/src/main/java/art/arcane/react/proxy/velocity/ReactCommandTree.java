package art.arcane.react.proxy.velocity;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * The proxy's {@code /react} tree. The permission sits on the literal, so Brigadier never matches it for a player without
 * access and Velocity passes the whole command, arguments included, on to the backend's own {@code /react}.
 */
final class ReactCommandTree {
  static final String ARGUMENTS = "arguments";

  interface Runner<S> {
    void run(S source, String alias, String[] arguments);
  }

  interface Completer<S> {
    List<String> complete(S source, String alias, String[] arguments);
  }

  private ReactCommandTree() {
  }

  static <S> LiteralArgumentBuilder<S> literal(String label, Predicate<S> permitted, Runner<S> runner, Completer<S> completer) {
    return LiteralArgumentBuilder.<S>literal(label)
        .requires(permitted)
        .executes((CommandContext<S> context) -> {
          runner.run(context.getSource(), alias(context.getInput()), new String[0]);
          return Command.SINGLE_SUCCESS;
        })
        .then(RequiredArgumentBuilder.<S, String>argument(ARGUMENTS, StringArgumentType.greedyString())
            .suggests((CommandContext<S> context, SuggestionsBuilder builder) -> suggest(context, builder, completer))
            .executes((CommandContext<S> context) -> {
              runner.run(context.getSource(), alias(context.getInput()), split(StringArgumentType.getString(context, ARGUMENTS)));
              return Command.SINGLE_SUCCESS;
            }));
  }

  static String alias(String input) {
    String trimmed = input.strip();
    if (trimmed.startsWith("/")) {
      trimmed = trimmed.substring(1);
    }
    int space = trimmed.indexOf(' ');
    return space < 0 ? trimmed : trimmed.substring(0, space);
  }

  static String[] split(String arguments) {
    String trimmed = arguments.strip();
    return trimmed.isEmpty() ? new String[0] : trimmed.split(" +");
  }

  private static <S> CompletableFuture<Suggestions> suggest(CommandContext<S> context, SuggestionsBuilder builder, Completer<S> completer) {
    String remaining = builder.getRemaining();
    String[] typed = remaining.split(" ", -1);
    SuggestionsBuilder last = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
    for (String suggestion : completer.complete(context.getSource(), alias(context.getInput()), typed)) {
      last.suggest(suggestion);
    }
    return last.buildFuture();
  }
}
