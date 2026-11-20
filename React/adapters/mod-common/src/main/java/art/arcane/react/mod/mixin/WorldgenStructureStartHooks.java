package art.arcane.react.mod.mixin;

import art.arcane.react.mod.cost.CostBucket;
import art.arcane.react.mod.cost.WorldgenCost;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(StructureStart.class)
abstract class WorldgenStructureStartHooks {
    @WrapMethod(method = "placeInChunk(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/world/level/ChunkPos;)V",
            require = 0)
    private void react$timePieces(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                                  BoundingBox box, ChunkPos origin, Operation<Void> original) {
        if (!WorldgenCost.active()) {
            original.call(level, structures, generator, random, box, origin);
            return;
        }
        CostBucket bucket = WorldgenCost.bucket(((StructureStart) (Object) this).getStructure());
        long start = WorldgenCost.enter();
        try {
            original.call(level, structures, generator, random, box, origin);
        } finally {
            WorldgenCost.exit(start, bucket);
        }
    }
}
