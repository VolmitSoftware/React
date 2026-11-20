package art.arcane.react.mod.mixin;

import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PathNavigation.class)
public interface PathNavigationAccess {
    @Accessor("maxVisitedNodesMultiplier")
    float react$maxVisitedNodesMultiplier();
}
