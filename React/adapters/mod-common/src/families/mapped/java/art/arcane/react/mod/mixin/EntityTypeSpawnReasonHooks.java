package art.arcane.react.mod.mixin;

import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;

/** 1.21.1 creates entities without a reason; {@link MobSpawnReasonHooks} records it at finalize instead. */
@Mixin(EntityType.class)
public abstract class EntityTypeSpawnReasonHooks {
}
