package art.arcane.react.api.web;

import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;

public final class BukkitConsoleCommandDispatcher implements ConsoleCommandDispatcher {

    @Override
    public boolean dispatch(String command) {
        Boolean dispatched = J.sResult(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
        return Boolean.TRUE.equals(dispatched);
    }
}
