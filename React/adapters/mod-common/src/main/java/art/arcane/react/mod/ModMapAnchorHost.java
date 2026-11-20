package art.arcane.react.mod;

import art.arcane.react.api.rendering.MapRuntime;
import art.arcane.react.platform.MapAnchorHost;
import art.arcane.react.platform.MapRenderTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;

/** Map anchors on a mod server, resolved the way the Bukkit host does: frame first, then holder, then spawn. */
final class ModMapAnchorHost implements MapAnchorHost {
    private final MinecraftServer server;
    private final IntFunction<MapRuntime.FrameAnchor> frameAnchors;

    ModMapAnchorHost(MinecraftServer server, IntFunction<MapRuntime.FrameAnchor> frameAnchors) {
        this.server = server;
        this.frameAnchors = frameAnchors;
    }

    @Override
    public Optional<MapAnchor> anchor(MapRenderTarget target) {
        ServerPlayer viewer = viewer(target);
        MapRuntime.FrameAnchor frame = frame(target);
        ServerLevel frameLevel = frame == null ? null : level(frame.worldId());
        ServerLevel targetLevel = target == null ? null : level(target.worldId());
        ServerLevel mapLevel = targetLevel != null
                ? targetLevel
                : frameLevel != null ? frameLevel : viewer == null ? null : VanillaApi.level(viewer);
        if (mapLevel == null) {
            return Optional.empty();
        }

        double x;
        double z;
        if (frameLevel != null) {
            x = frame.x();
            z = frame.z();
        } else if (viewer != null) {
            x = viewer.getX();
            z = viewer.getZ();
        } else {
            BlockPos spawn = VanillaApi.spawn(mapLevel);
            x = spawn.getX() + 0.5D;
            z = spawn.getZ() + 0.5D;
        }
        return Optional.of(new MapAnchor(ModReactWorld.idOf(mapLevel), x, z, frame != null, viewer == null ? null : viewer.getUUID()));
    }

    @Override
    public boolean frameAnchored(MapRenderTarget target) {
        return frame(target) != null;
    }

    private MapRuntime.FrameAnchor frame(MapRenderTarget target) {
        return target == null ? null : frameAnchors.apply(target.mapId());
    }

    private ServerPlayer viewer(MapRenderTarget target) {
        return target == null || target.viewerId() == null ? null : server.getPlayerList().getPlayer(target.viewerId());
    }

    private ServerLevel level(UUID worldId) {
        if (worldId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (ModReactWorld.idOf(level).equals(worldId)) {
                return level;
            }
        }
        return null;
    }
}
