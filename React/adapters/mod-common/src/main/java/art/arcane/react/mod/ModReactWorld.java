package art.arcane.react.mod;

import art.arcane.react.platform.ReactWorld;
import net.minecraft.server.level.ServerLevel;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** A loaded dimension. Vanilla levels carry no UUID, so the id is derived from the dimension key and stays stable. */
public record ModReactWorld(ServerLevel level) implements ReactWorld {
    public ModReactWorld {
        Objects.requireNonNull(level, "level");
    }

    public static UUID idOf(ServerLevel level) {
        return UUID.nameUUIDFromBytes(("react-dimension:" + nameOf(level)).getBytes(StandardCharsets.UTF_8));
    }

    public static String nameOf(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    @Override
    public UUID id() {
        return idOf(level);
    }

    @Override
    public String name() {
        return nameOf(level);
    }

    @Override
    public Object handle() {
        return level;
    }
}
