package art.arcane.react.web;

import art.arcane.react.api.web.BukkitConsoleCommandDispatcher;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class BukkitConsoleCommandDispatcherTest {

    @Test
    void dispatchRunsThroughGlobalSchedulerBeforeUsingConsoleSender() {
        ConsoleCommandSender consoleSender = mock(ConsoleCommandSender.class);
        try (MockedStatic<J> scheduler = mockStatic(J.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            scheduler.when(() -> J.sResult(any())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Supplier<Boolean> supplier = invocation.getArgument(0, Supplier.class);
                return supplier.get();
            });
            bukkit.when(Bukkit::getConsoleSender).thenReturn(consoleSender);
            bukkit.when(() -> Bukkit.dispatchCommand(consoleSender, "say scheduled")).thenReturn(true);

            boolean dispatched = new BukkitConsoleCommandDispatcher().dispatch("say scheduled");

            assertTrue(dispatched);
            scheduler.verify(() -> J.sResult(any()));
            bukkit.verify(Bukkit::getConsoleSender);
            bukkit.verify(() -> Bukkit.dispatchCommand(consoleSender, "say scheduled"));
        }
    }
}
