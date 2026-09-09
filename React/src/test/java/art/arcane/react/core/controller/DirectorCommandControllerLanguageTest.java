package art.arcane.react.core.controller;

import art.arcane.react.localization.ReactLanguage;
import art.arcane.volmlib.util.localization.BukkitLanguageSwitcher;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

class DirectorCommandControllerLanguageTest {
  @ParameterizedTest
  @ValueSource(strings = {"language", "languages", "LANGUAGE", "LaNgUaGeS"})
  void languageCommandsForwardArgumentsWithoutRequiringRootPermission(String spelling) {
    DirectorCommandController controller = new DirectorCommandController();
    BukkitLanguageSwitcher switcher = Mockito.mock(BukkitLanguageSwitcher.class);
    Player sender = personalLanguagePlayer();
    Command command = reactCommand();
    List<String[]> forwarded = List.of(
        new String[0],
        new String[]{"self", "pl_PL"},
        new String[]{"server", "edit", "pl_PL"}
    );

    try (MockedStatic<ReactLanguage> language = Mockito.mockStatic(ReactLanguage.class)) {
      language.when(ReactLanguage::switcher).thenReturn(switcher);

      for (String[] arguments : forwarded) {
        Assertions.assertTrue(controller.onCommand(sender, command, "react", withSpelling(spelling, arguments)));
      }

      ArgumentCaptor<String[]> arguments = ArgumentCaptor.forClass(String[].class);
      Mockito.verify(switcher, Mockito.times(forwarded.size())).command(Mockito.same(sender), arguments.capture());
      for (int index = 0; index < forwarded.size(); index++) {
        Assertions.assertArrayEquals(forwarded.get(index), arguments.getAllValues().get(index));
      }
      Mockito.verify(sender, Mockito.never()).hasPermission("react.use");
      Mockito.verify(sender, Mockito.never()).hasPermission("react.*");
      Mockito.verify(sender, Mockito.never()).isOp();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"language", "languages", "LANGUAGE", "LaNgUaGeS"})
  void languageCompletionReturnsSwitcherSuggestionsWithForwardedArguments(String spelling) {
    DirectorCommandController controller = new DirectorCommandController();
    BukkitLanguageSwitcher switcher = Mockito.mock(BukkitLanguageSwitcher.class);
    Player sender = personalLanguagePlayer();
    Command command = reactCommand();
    List<String> suggestions = List.of("pl_PL", "pt_PT");
    List<String[]> forwarded = List.of(
        new String[]{""},
        new String[]{"self", "p"},
        new String[]{"server", "edit", "p"}
    );
    Mockito.when(switcher.complete(Mockito.same(sender), Mockito.any(String[].class))).thenReturn(suggestions);

    try (MockedStatic<ReactLanguage> language = Mockito.mockStatic(ReactLanguage.class)) {
      language.when(ReactLanguage::switcher).thenReturn(switcher);

      for (String[] arguments : forwarded) {
        Assertions.assertEquals(suggestions,
            controller.onTabComplete(sender, command, "react", withSpelling(spelling, arguments)));
      }

      ArgumentCaptor<String[]> arguments = ArgumentCaptor.forClass(String[].class);
      Mockito.verify(switcher, Mockito.times(forwarded.size())).complete(Mockito.same(sender), arguments.capture());
      for (int index = 0; index < forwarded.size(); index++) {
        Assertions.assertArrayEquals(forwarded.get(index), arguments.getAllValues().get(index));
      }
      Mockito.verify(sender, Mockito.never()).hasPermission("react.use");
    }
  }

  private static Player personalLanguagePlayer() {
    Player player = Mockito.mock(Player.class);
    Mockito.when(player.hasPermission("react.language.self")).thenReturn(true);
    Mockito.when(player.hasPermission("volmit.language.self")).thenReturn(true);
    return player;
  }

  private static Command reactCommand() {
    Command command = Mockito.mock(Command.class);
    Mockito.when(command.getName()).thenReturn("react");
    return command;
  }

  private static String[] withSpelling(String spelling, String[] arguments) {
    String[] result = new String[arguments.length + 1];
    result[0] = spelling;
    System.arraycopy(arguments, 0, result, 1, arguments.length);
    return result;
  }
}
