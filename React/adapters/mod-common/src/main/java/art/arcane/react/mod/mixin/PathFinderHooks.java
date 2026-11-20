package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModPathfinding;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Path searches, where Paper fires its EntityPathfindEvent, and the nodes each search takes off its open set, which
 * is what the pathfinder budget shrinks.
 */
@Mixin(PathFinder.class)
public abstract class PathFinderHooks {
    private static final String SEARCH = "findPath(Lnet/minecraft/world/level/PathNavigationRegion;Lnet/minecraft/world/entity/Mob;Ljava/util/Set;FIF)Lnet/minecraft/world/level/pathfinder/Path;";
    private static final String NODE_SEARCH = "findPath(Lnet/minecraft/world/level/pathfinder/Node;Ljava/util/Map;FIF)Lnet/minecraft/world/level/pathfinder/Path;";

    @Unique
    private int react$visited;

    @Inject(method = SEARCH, at = @At("HEAD"), require = 0)
    private void react$searching(CallbackInfoReturnable<Path> callback) {
        react$visited = 0;
        ModPathfinding.searched();
    }

    @ModifyExpressionValue(method = NODE_SEARCH, require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/pathfinder/BinaryHeap;pop()Lnet/minecraft/world/level/pathfinder/Node;"))
    private Node react$visiting(Node node) {
        react$visited++;
        return node;
    }

    @Inject(method = SEARCH, at = @At("RETURN"), require = 0)
    private void react$searched(CallbackInfoReturnable<Path> callback) {
        ModPathfinding.visited(react$visited);
        react$visited = 0;
    }
}
