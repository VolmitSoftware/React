package art.arcane.react.mod.mixin;

import art.arcane.react.mod.cost.CostBucket;
import art.arcane.react.mod.cost.WorldgenCost;
import art.arcane.react.mod.cost.WorldgenStep;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class WorldgenNoiseHooks {
    @WrapMethod(method = "doFill(Lnet/minecraft/world/level/levelgen/blending/Blender;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;II)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            require = 0)
    private ChunkAccess react$timeNoise(Blender blender, StructureManager structures, RandomState random, ChunkAccess chunk, int minCellY, int cellCountY, Operation<ChunkAccess> original) {
        if (!WorldgenCost.active()) {
            return original.call(blender, structures, random, chunk, minCellY, cellCountY);
        }
        long start = System.nanoTime();
        try {
            return original.call(blender, structures, random, chunk, minCellY, cellCountY);
        } finally {
            WorldgenCost.step(WorldgenStep.NOISE, start);
        }
    }

    @WrapMethod(method = "buildSurface(Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
            require = 0)
    private void react$timeSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk, Operation<Void> original) {
        if (!WorldgenCost.active()) {
            original.call(region, structures, random, chunk);
            return;
        }
        long start = System.nanoTime();
        try {
            original.call(region, structures, random, chunk);
        } finally {
            WorldgenCost.step(WorldgenStep.SURFACE, start);
        }
    }

    @WrapMethod(method = "applyCarvers(Lnet/minecraft/server/level/WorldGenRegion;JLnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
            require = 0)
    private void react$timeCarverStep(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes, StructureManager structures, ChunkAccess chunk, Operation<Void> original) {
        if (!WorldgenCost.active()) {
            original.call(region, seed, random, biomes, structures, chunk);
            return;
        }
        long start = System.nanoTime();
        try {
            original.call(region, seed, random, biomes, structures, chunk);
        } finally {
            WorldgenCost.step(WorldgenStep.CARVERS, start);
        }
    }

    @WrapOperation(method = "applyCarvers", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/carver/ConfiguredWorldCarver;carve(Lnet/minecraft/world/level/levelgen/carver/CarvingContext;Lnet/minecraft/world/level/chunk/ChunkAccess;Ljava/util/function/Function;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/Aquifer;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/CarvingMask;)Z"))
    private boolean react$timeCarver(ConfiguredWorldCarver<?> carver, CarvingContext context, ChunkAccess chunk, Function<BlockPos, Holder<Biome>> biomes,
                                    RandomSource random, Aquifer aquifer, ChunkPos pos, CarvingMask mask, Operation<Boolean> original) {
        if (!WorldgenCost.active()) {
            return original.call(carver, context, chunk, biomes, random, aquifer, pos, mask);
        }
        CostBucket bucket = WorldgenCost.bucket(carver);
        long start = WorldgenCost.enter();
        try {
            return original.call(carver, context, chunk, biomes, random, aquifer, pos, mask);
        } finally {
            WorldgenCost.exit(start, bucket);
        }
    }
}
