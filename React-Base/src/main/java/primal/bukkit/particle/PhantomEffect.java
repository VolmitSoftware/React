package primal.bukkit.particle;
import org.bukkit.Location;

import primal.lang.collection.GList;

/**
 * An effect that can be played
 * @author cyberpwn
 *
 */
public abstract class PhantomEffect implements VisualEffect
{
	@Override
	public GList<VisualEffect> getEffects()
	{
		return new GList<VisualEffect>();
	}

	@Override
	public abstract void play(Location l);

	@Override
	public void addEffect(VisualEffect e)
	{

	}
}