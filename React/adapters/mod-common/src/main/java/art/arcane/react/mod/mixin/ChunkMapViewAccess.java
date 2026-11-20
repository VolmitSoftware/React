package art.arcane.react.mod.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapViewAccess {
    @Invoker("getPlayerViewDistance")
    int react$playerViewDistance(ServerPlayer player);

    @Invoker("updateChunkTracking")
    void react$updateChunkTracking(ServerPlayer player);
}
