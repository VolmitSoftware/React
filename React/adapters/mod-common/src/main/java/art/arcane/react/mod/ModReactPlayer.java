package art.arcane.react.mod;

import art.arcane.react.platform.ReactPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** An online player. Permission nodes map to the gamemaster op level until a permissions mod is wired in. */
public record ModReactPlayer(ServerPlayer player) implements ReactPlayer {
    public ModReactPlayer {
        Objects.requireNonNull(player, "player");
    }

    @Override
    public UUID id() {
        return player.getUUID();
    }

    @Override
    public String name() {
        return player.getScoreboardName();
    }

    @Override
    public Locale locale() {
        String language = player.clientInformation().language();
        return language == null || language.isBlank() ? Locale.ENGLISH : Locale.forLanguageTag(language.replace('_', '-'));
    }

    @Override
    public boolean hasPermission(String node) {
        return ModPermissions.has(player.permissions(), node);
    }

    @Override
    public void sendMessage(String message) {
        player.sendSystemMessage(Component.literal(message));
    }

    @Override
    public Object handle() {
        return player;
    }
}
