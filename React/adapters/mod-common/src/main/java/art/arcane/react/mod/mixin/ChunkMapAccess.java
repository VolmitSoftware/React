package art.arcane.react.mod.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.BooleanSupplier;

@Mixin(ChunkMap.class)
public interface ChunkMapAccess {
    @Accessor("visibleChunkMap")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> react$visibleChunkMap();

    @Invoker("processUnloads")
    void react$processUnloads(BooleanSupplier haveTime);
}
