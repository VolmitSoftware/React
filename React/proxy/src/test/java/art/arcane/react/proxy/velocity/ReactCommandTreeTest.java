package art.arcane.react.proxy.velocity;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.LiteralCommandNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactCommandTreeTest {
  private record Source(boolean permitted) {
  }

  private final List<String> runs = new ArrayList<>();
  private final CommandDispatcher<Source> dispatcher = new CommandDispatcher<>();

  ReactCommandTreeTest() {
    LiteralCommandNode<Source> react = dispatcher.register(ReactCommandTree.<Source>literal("react", Source::permitted,
        (Source source, String alias, String[] arguments) -> runs.add(alias + ":" + String.join(",", arguments)),
        (Source source, String alias, String[] arguments) -> List.of("monitor", "network")));
    dispatcher.register(LiteralArgumentBuilder.<Source>literal("re")
        .requires(react.getRequirement()).redirect(react).executes(react.getCommand()));
  }

  @Test
  void unpermittedSourceGetsUnknownCommandSoTheProxyForwardsIt() {
    CommandSyntaxException bare = assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("react", new Source(false)));
    CommandSyntaxException withArguments = assertThrows(CommandSyntaxException.class,
        () -> dispatcher.execute("react monitor", new Source(false)));
    assertEquals(CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand(), bare.getType());
    assertEquals(CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand(), withArguments.getType());
    assertTrue(runs.isEmpty());
  }

  @Test
  void permittedSourceRunsWithAliasAndArguments() throws CommandSyntaxException {
    dispatcher.execute("react", new Source(true));
    dispatcher.execute("react network  join", new Source(true));
    dispatcher.execute("re monitor", new Source(true));
    assertEquals(List.of("react:", "react:network,join", "re:monitor"), runs);
  }

  @Test
  void suggestionsReplaceOnlyTheLastWord() {
    Suggestions suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("react network j", new Source(true))).join();
    List<String> texts = new ArrayList<>();
    for (Suggestion suggestion : suggestions.getList()) {
      texts.add(suggestion.getText());
      assertEquals("react network ".length(), suggestion.getRange().getStart());
    }
    assertEquals(List.of("monitor", "network"), texts);
  }

  @Test
  void splitDropsBlankArguments() {
    assertArrayEquals(new String[0], ReactCommandTree.split("  "));
    assertArrayEquals(new String[]{"a", "b"}, ReactCommandTree.split(" a  b "));
    assertEquals("re", ReactCommandTree.alias("/re monitor"));
  }
}
