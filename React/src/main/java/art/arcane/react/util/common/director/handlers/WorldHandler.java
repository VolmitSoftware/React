package art.arcane.react.util.director.handlers;

import art.arcane.volmlib.util.director.handlers.base.WorldHandlerBase;
import art.arcane.react.util.director.DirectorParameterHandler;
import org.bukkit.World;

public class WorldHandler extends WorldHandlerBase implements DirectorParameterHandler<World> {
    @Override
    protected String excludedPrefix() {
        return "iris/";
    }
}
