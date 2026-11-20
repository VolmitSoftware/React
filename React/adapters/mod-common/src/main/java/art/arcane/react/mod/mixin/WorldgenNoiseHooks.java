package art.arcane.react.mod.mixin;

import art.arcane.react.mod.cost.CostBucket;
import art.arcane.react.mod.cost.WorldgenCost;
import art.arcane.react.mod.cost.WorldgenStep;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.CarverOutput;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class WorldgenNoiseHooks {
    @WrapMethod(method = "doFill(Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
            require = 0)
    private void react$timeNoise(NoiseChunk noise, ChunkAccess chunk, Operation<Void> original) {
        if (!WorldgenCost.active()) {
            original.call(noise, chunk);
            return;
        }
        long start = System.nanoTime();
        try {
            original.call(noise, chunk);
        } finally {
            WorldgenCost.step(WorldgenStep.NOISE, start);
        }
    }

    @WrapMethod(method = "buildSurface(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/biome/BiomeManager;Ljava/util/Set;Lnet/minecraft/world/level/levelgen/material/rule/MaterialRule;)V",
            require = 0)
    private void react$timeSurface(ChunkAccess chunk, NoiseChunk noise, RandomState random, BiomeManager biomes,
                               Set<Holder<Biome>> possibleBiomes, MaterialRule rule, Operation<Void> original) {
        if (!WorldgenCost.active()) {
            original.call(chunk, noise, random, biomes, possibleBiomes, rule);
            return;
        }
        long start = System.nanoTime();
        try {
            original.call(chunk, noise, random, biomes, possibleBiomes, rule);
        } finally {
            WorldgenCost.step(WorldgenStep.SURFACE, start);
        }
    }

    @WrapMethod(method = "generateCarvers(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/blending/Blender;Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/level/levelgen/material/rule/MaterialRule;)V",
            require = 0)
    private void react$timeCarverStep(ChunkAccess chunk, Blender blender, NoiseChunk noise, RandomState random, BiomeManager biomes,
                                   WorldGenRegion region, MaterialRule rule, Operation<Void> original) {
        if (!WorldgenCost.active()) {
            original.call(chunk, blender, noise, random, biomes, region, rule);
            return;
        }
        long start = System.nanoTime();
        try {
            original.call(chunk, blender, noise, random, biomes, region, rule);
        } finally {
            WorldgenCost.step(WorldgenStep.CARVERS, start);
        }
    }

    @WrapOperation(method = "generateCarvers", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/carver/WorldCarver;carve(Lnet/minecraft/world/level/levelgen/WorldGenerationContext;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/CarverOutput;)Z"))
    private boolean react$timeCarver(WorldCarver carver, WorldGenerationContext context, RandomSource random, ChunkPos chunkPos, ChunkPos sourcePos,
                                    CarverOutput output, Operation<Boolean> original) {
        if (!WorldgenCost.active()) {
            return original.call(carver, context, random, chunkPos, sourcePos, output);
        }
        CostBucket bucket = WorldgenCost.bucket(carver);
        long start = WorldgenCost.enter();
        try {
            return original.call(carver, context, random, chunkPos, sourcePos, output);
        } finally {
            WorldgenCost.exit(start, bucket);
        }
    }
}
