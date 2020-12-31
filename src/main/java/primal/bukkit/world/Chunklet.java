package primal.bukkit.world;

import java.util.Iterator;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import primal.lang.collection.GList;

/**
 * 4x4 chunklet area based on the chunk grid
 *
 * @author cyberpwn
 */
public class Chunklet
{
	protected int x;
	protected int z;
	protected World world;

	/**
	 * Create a new chunklet
	 *
	 * @param x
	 *            the x coord
	 * @param z
	 *            the z coord
	 * @param world
	 *            the world
	 */
	public Chunklet(int x, int z, World world)
	{
		this.x = x;
		this.z = z;
		this.world = world;
	}

	/**
	 * Get all the chunklets in the chunk that this chunklet resides
	 *
	 * @return the chunklets
	 */
	public GList<Chunklet> inThisChunk()
	{
		return W.getChunklets(getChunk());
	}

	/**
	 * Create a new chunklet from the current location
	 *
	 * @param location
	 *            the given location
	 */
	public Chunklet(Location location)
	{
		this.x = location.getBlockX() >> 2;
		this.z = location.getBlockZ() >> 2;
		this.world = location.getWorld();
	}

	/**
	 * Get all players inside this chunklet
	 *
	 * @return the players in this chunklet
	 */
	public GList<Player> getPlayers()
	{
		GList<Player> p = new GList<Player>();

		for(Entity i : getChunk().getEntities())
		{
			if(i.getType().equals(EntityType.PLAYER))
			{
				if(contains(i.getLocation()))
				{
					p.add((Player) i);
				}
			}
		}

		return p;
	}

	/**
	 * Get entities in this chunklet
	 *
	 * @return the entities
	 */
	public GList<Entity> getEntities()
	{
		GList<Entity> p = new GList<Entity>();

		for(Entity i : getChunk().getEntities())
		{
			if(contains(i.getLocation()))
			{
				p.add(i);
			}
		}

		return p;
	}

	/**
	 * Get the chunk this chunklet resides in
	 *
	 * @return the chunk
	 */
	public Chunk getChunk()
	{
		return getMin().getChunk();
	}

	/**
	 * Does this chunklet contain?
	 *
	 * @param l
	 *            the object
	 * @return true if it does
	 */
	public boolean contains(Location l)
	{
		return new Cuboid(getMin(), getMax()).contains(l);
	}

	/**
	 * Does this chunklet contain?
	 *
	 * @param p
	 *            the object
	 * @return true if it does
	 */
	public boolean contains(Player p)
	{
		return contains(p.getLocation());
	}

	/**
	 * Does this chunklet contain?
	 *
	 * @param chunk
	 *            the object
	 * @return true if it does
	 */
	public boolean contains(Chunk chunk)
	{
		return getMin().getChunk().equals(chunk);
	}

	/**
	 * Get the min corner of this chunklet
	 *
	 * @return the min corner
	 */
	public Location getMin()
	{
		return new Location(world, x << 2, 0, z << 2);
	}

	/**
	 * Get the max corner of this chunklet
	 *
	 * @return the max coord
	 */
	public Location getMax()
	{
		return new Location(world, (x << 2) + 3, 255, (z << 2) + 3);
	}

	/**
	 * Get an iterator of blocks from this chunklet
	 *
	 * @return the blocks of the chunklet
	 */
	public Iterator<Block> iterator()
	{
		return new Cuboid(getMin(), getMax()).iterator();
	}

	/**
	 * Get the relative chunklet based on direction
	 *
	 * @param d
	 *            the direction
	 * @return the relative chunklet
	 */
	public Chunklet getRelative(Direction d)
	{
		return new Chunklet(x + d.x(), z + d.z(), world);
	}

	/**
	 * Get all chunklet neighbors
	 *
	 * @return the chunklet neighbors
	 */
	public GList<Chunklet> getNeighbors()
	{
		GList<Chunklet> n = new GList<Chunklet>();

		for(Direction d : Direction.news())
		{
			n.add(getRelative(d));
		}

		return n;
	}

	/**
	 * Get a circle of chunklets
	 *
	 * @param radius
	 *            the chunklet radius
	 * @return the chunklets
	 */
	public GList<Chunklet> getCircle(int radius)
	{
		GList<Chunklet> n = new GList<Chunklet>();

		int x = radius;
		int z = 0;
		int xChange = 1 - (radius << 1);
		int zChange = 0;
		int radiusError = 0;

		while(x >= z)
		{
			for(int i = this.x - x; i <= this.x + x; i++)
			{
				n.add(new Chunklet(i, this.z + z, world));
				n.add(new Chunklet(i, this.z - z, world));
			}

			for(int i = this.x - z; i <= this.x + z; i++)
			{
				n.add(new Chunklet(i, this.z + x, world));
				n.add(new Chunklet(i, this.z - x, world));
			}

			z++;
			radiusError += zChange;
			zChange += 2;

			if(((radiusError << 1) + xChange) > 0)
			{
				x--;
				radiusError += xChange;
				xChange += 2;
			}
		}

		return n;
	}

	@Override
	public boolean equals(Object object)
	{
		if(object == null)
		{
			return false;
		}

		if(object instanceof Chunklet)
		{
			Chunklet c = (Chunklet) object;
			if(c.x == x && c.z == z && c.world.equals(world))
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Get a strip line of an edge of this chunklet
	 *
	 * @param level
	 *            the level
	 * @param d
	 *            the direction (face)
	 * @return the list of locations for this strip
	 */
	public GList<Location> getBorder(int level, Direction d)
	{
		GList<Location> ls = new GList<Location>();
		Iterator<Block> it = new Cuboid(getMin(), getMax()).getFace(d.f()).flatten(level).iterator();

		while(it.hasNext())
		{
			ls.add(it.next().getLocation());
		}

		return ls;
	}

	/**
	 * Get the x chunklet coord
	 *
	 * @return the x
	 */
	public int getX()
	{
		return x;
	}

	/**
	 * Set the chunklet x coord
	 *
	 * @param x
	 *            the x coord
	 */
	public void setX(int x)
	{
		this.x = x;
	}

	/**
	 * Get the z coord
	 *
	 * @return the chunklet z coord
	 */
	public int getZ()
	{
		return z;
	}

	/**
	 * Set the z coord
	 *
	 * @param z
	 *            the chunklet z coord
	 */
	public void setZ(int z)
	{
		this.z = z;
	}

	/**
	 * Get the world this chunklet resides in
	 *
	 * @return the world
	 */
	public World getWorld()
	{
		return world;
	}

	/**
	 * Set the world of this chunklet
	 *
	 * @param world
	 *            the world
	 */
	public void setWorld(World world)
	{
		this.world = world;
	}
}