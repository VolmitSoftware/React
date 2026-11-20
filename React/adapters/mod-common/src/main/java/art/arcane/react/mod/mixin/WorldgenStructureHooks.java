package art.arcane.react.mod.mixin;

import art.arcane.react.mod.cost.CostBucket;
import art.arcane.react.mod.cost.WorldgenCost;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Predicate;

@Mixin(Structure.class)
abstract class WorldgenStructureHooks {
    @WrapMethod(method = "generate(Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/biome/BiomeSource;Lnet/minecraft/world/level/biome/Climate$Sampler;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;JLnet/minecraft/world/level/ChunkPos;ILnet/minecraft/world/level/LevelHeightAccessor;Ljava/util/function/Predicate;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;",
            require = 0)
    private StructureStart react$timeStart(Holder<Structure> self, ResourceKey<Level> dimension, RegistryAccess access, ChunkGenerator generator,
                                          BiomeSource biomeSource, Climate.Sampler sampler, RandomState random, StructureTemplateManager templates, long seed, ChunkPos pos, int references,
                                          LevelHeightAccessor height, Predicate<Holder<Biome>> validBiome, Operation<StructureStart> original) {
        if (!WorldgenCost.active()) {
            return original.call(self, dimension, access, generator, biomeSource, sampler, random, templates, seed, pos, references, height, validBiome);
        }
        CostBucket bucket = WorldgenCost.bucket(this);
        long start = WorldgenCost.enter();
        try {
            return original.call(self, dimension, access, generator, biomeSource, sampler, random, templates, seed, pos, references, height, validBiome);
        } finally {
            WorldgenCost.exit(start, bucket);
        }
    }
}
