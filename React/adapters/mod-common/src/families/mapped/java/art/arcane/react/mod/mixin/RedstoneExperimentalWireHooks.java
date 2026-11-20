package art.arcane.react.mod.mixin;

import net.minecraft.world.level.block.RedStoneWireBlock;
import org.spongepowered.asm.mixin.Mixin;

/** This version has no experimental wire evaluator; its wire changes go through {@link RedstoneSignalWireHooks}. */
@Mixin(RedStoneWireBlock.class)
public abstract class RedstoneExperimentalWireHooks {
}
