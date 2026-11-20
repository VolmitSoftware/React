package art.arcane.react.mod.mixin;

import art.arcane.react.mod.cost.CostBucket;
import art.arcane.react.mod.cost.WorldgenCost;
import art.arcane.react.mod.cost.WorldgenStep;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkGenerator.class)
abstract class WorldgenChunkGeneratorHooks {
    @WrapMethod(method = "applyBiomeDecoration(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/StructureManager;)V",
            require = 0)
    private void react$timeFeatureStep(WorldGenLevel level, ChunkAccess chunk, StructureManager structures, Operation<Void> original) {
        if (!WorldgenCost.active()) {
            original.call(level, chunk, structures);
            return;
        }
        long start = System.nanoTime();
        try {
            original.call(level, chunk, structures);
        } finally {
            WorldgenCost.step(WorldgenStep.FEATURES, start);
        }
    }

    @WrapOperation(method = "applyBiomeDecoration", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/placement/FeaturePlacer;placeWithBiomeCheck(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean react$timeFeature(FeaturePlacer placer, PlacedFeature feature, RandomSource random, BlockPos origin, Operation<Boolean> original) {
        if (!WorldgenCost.active()) {
            return original.call(placer, feature, random, origin);
        }
        CostBucket bucket = WorldgenCost.bucket(feature);
        long start = WorldgenCost.enter();
        try {
            return original.call(placer, feature, random, origin);
        } finally {
            WorldgenCost.exit(start, bucket);
        }
    }

    @WrapMethod(method = "createStructures(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/resources/ResourceKey;)V",
            require = 0)
    private void react$timeStructureStep(RegistryAccess access, ChunkGeneratorStructureState state, StructureManager structures, ChunkAccess chunk,
                                    StructureTemplateManager templates, ResourceKey<Level> dimension, Operation<Void> original) {
        if (!WorldgenCost.active()) {
            original.call(access, state, structures, chunk, templates, dimension);
            return;
        }
        long start = System.nanoTime();
        try {
            original.call(access, state, structures, chunk, templates, dimension);
        } finally {
            WorldgenCost.step(WorldgenStep.STRUCTURE_STARTS, start);
        }
    }
}
