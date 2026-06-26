package art.arcane.react.nms.v26_2_R1;

import net.bytebuddy.asm.Advice;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class BrewingTickAdvice {
    private BrewingTickAdvice() {}

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean enter(@Advice.Argument(0) Level level,
                                @Advice.Argument(1) BlockPos pos,
                                @Advice.Argument(2) BlockState state,
                                @Advice.Argument(3) BrewingStandBlockEntity entity) {
        return BrewingTickRuntime.enter(level, pos, state, entity);
    }
}
