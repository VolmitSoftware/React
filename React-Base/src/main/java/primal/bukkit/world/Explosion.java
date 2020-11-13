package primal.bukkit.world;

import org.bukkit.Location;

public class Explosion
{
	private boolean blocks;
	private boolean fire;
	private float yield;

	public Explosion()
	{
		this.blocks = true;
		this.fire = false;
		this.yield = 3f;
	}

	public void boom(Location l)
	{
		l.getWorld().createExplosion(l.getX(), l.getY(), l.getZ(), yield, fire, blocks);
	}

	public Explosion setBreakBlocks(boolean bb)
	{
		this.blocks = bb;
		return this;
	}

	public Explosion radius(float yield)
	{
		this.yield = yield;
		return this;
	}

	public Explosion noBlocks()
	{
		this.blocks = false;
		return this;
	}

	public Explosion withFire()
	{
		this.fire = true;
		return this;
	}
}
