package art.arcane.react.mod.mixin;

import art.arcane.react.mod.cost.WorldgenCost;
import art.arcane.react.mod.cost.WorldgenStep;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChunkAccess.class)
abstract class WorldgenBiomeHooks {
    @WrapMethod(method = "fillBiomesFromNoise(Lnet/minecraft/world/level/biome/BiomeResolver;)V",
            require = 0)
    private void react$timeBiomes(BiomeResolver resolver, Operation<Void> original) {
        if (!WorldgenCost.active()) {
            original.call(resolver);
            return;
        }
        long start = System.nanoTime();
        try {
            original.call(resolver);
        } finally {
            WorldgenCost.step(WorldgenStep.BIOMES, start);
        }
    }
}
