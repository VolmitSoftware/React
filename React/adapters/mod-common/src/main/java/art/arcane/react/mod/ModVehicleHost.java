package art.arcane.react.mod;

import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.VehicleHost;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Minecarts and boats on mods. Vanilla carts carry no flying or derailed modifiers, so velocity alone decides. */
final class ModVehicleHost implements VehicleHost {
    private final MinecraftServer server;

    ModVehicleHost(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public GameEntity vehicle(Object handle) {
        return new ModGameEntity((Entity) handle);
    }

    @Override
    public boolean isMinecart(GameEntity entity) {
        Entity handle = handle(entity);
        return handle != null && VanillaApi.isMinecart(handle);
    }

    @Override
    public boolean isBoat(GameEntity entity) {
        Entity handle = handle(entity);
        return handle != null && VanillaApi.isBoat(handle);
    }

    @Override
    public boolean isMoving(GameEntity vehicle) {
        return velocitySquared(vehicle) > 0D;
    }

    @Override
    public double velocitySquared(GameEntity vehicle) {
        Entity handle = handle(vehicle);
        return handle == null ? 0D : handle.getDeltaMovement().lengthSqr();
    }

    @Override
    public boolean hasPassengers(GameEntity vehicle) {
        Entity handle = handle(vehicle);
        return handle != null && handle.isVehicle();
    }

    @Override
    public void stop(GameEntity vehicle) {
        Entity handle = handle(vehicle);
        if (handle != null) {
            handle.setDeltaMovement(Vec3.ZERO);
            VanillaCompat.syncVelocity(handle);
        }
    }

    @Override
    public List<GameEntity> anchors() {
        List<GameEntity> result = new ArrayList<>();
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            result.add(new ModGameEntity(player));
        }
        return result;
    }

    @Override
    public boolean isOnline(GameEntity player) {
        return handle(player) instanceof ServerPlayer handle && !handle.isRemoved()
                && server.getPlayerList().getPlayer(handle.getUUID()) == handle;
    }

    @Override
    public List<GameEntity> sampleNear(GameEntity player, String owner, double horizontalRadius, double verticalRadius, int limit,
                                       Predicate<GameEntity> claim) {
        if (limit <= 0 || !(handle(player) instanceof ServerPlayer handle)) {
            return List.of();
        }
        List<GameEntity> result = new ArrayList<>();
        for (Entity entity : handle.level().getEntities(handle, handle.getBoundingBox().inflate(horizontalRadius, verticalRadius,
                horizontalRadius), (Entity candidate) -> true)) {
            GameEntity wrapped = new ModGameEntity(entity);
            if (claim == null || claim.test(wrapped)) {
                result.add(wrapped);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    private static Entity handle(GameEntity entity) {
        return entity != null && entity.handle() instanceof Entity handle ? handle : null;
    }
}
