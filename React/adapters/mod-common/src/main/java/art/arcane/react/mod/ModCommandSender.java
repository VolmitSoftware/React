package art.arcane.react.mod;

import art.arcane.react.platform.ReactCommandSender;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.volmlib.util.plugin.ComponentText;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.Objects;
import java.util.Optional;

/** The console or a player running a React command. React permission nodes resolve through {@link ModPermissions}. */
public record ModCommandSender(CommandSourceStack source) implements ReactCommandSender {
    public ModCommandSender {
        Objects.requireNonNull(source, "source");
    }

    @Override
    public String name() {
        return source.getTextName();
    }

    @Override
    public boolean isPlayer() {
        return source.isPlayer();
    }

    @Override
    public Optional<ReactPlayer> player() {
        ServerPlayer player = source.getPlayer();
        return player == null ? Optional.empty() : Optional.of(new ModReactPlayer(player));
    }

    @Override
    public boolean hasPermission(String node) {
        return ModPermissions.has(source.permissions(), node);
    }

    @Override
    public boolean isOp() {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    @Override
    public void send(ComponentText message) {
        source.sendSystemMessage(ModText.component(message));
    }

    @Override
    public void sendMessage(String message) {
        send(ComponentText.section(message));
    }

    @Override
    public Object handle() {
        return source;
    }
}
