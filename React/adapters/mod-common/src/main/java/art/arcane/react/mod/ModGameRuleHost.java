package art.arcane.react.mod;

import art.arcane.react.platform.GameRuleHost;
import art.arcane.react.platform.ReactWorld;
import net.minecraft.server.level.ServerLevel;

final class ModGameRuleHost implements GameRuleHost {
    @Override
    public Integer randomTickSpeed(ReactWorld world) {
        return world.handle() instanceof ServerLevel ? VanillaApi.randomTickSpeed((ServerLevel) world.handle()) : null;
    }

    @Override
    public boolean setRandomTickSpeed(ReactWorld world, int value) {
        if (!(world.handle() instanceof ServerLevel)) {
            return false;
        }
        ServerLevel level = (ServerLevel) world.handle();
        VanillaApi.setRandomTickSpeed(level, value);
        return VanillaApi.randomTickSpeed(level) == value;
    }
}
