package primal.bukkit.world;

import java.util.HashSet;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import primal.lang.collection.GList;

/**
 * Used to Create an instance of a spherical area based on a central location
 * Great for efficiently checking if an entity is within a spherical area.
 *
 * @author cyberpwn
 */
public class Area
{
	private Location location;
	private Double radius;

	public static boolean within(Location center, Location target, double rad)
	{
		return new Area(center, rad).isWithin(target);
	}

	/**
	 * Used to instantiate a new "area" in which you can check if entities are
	 * within this area.
	 *
	 * @param location
	 *            The center location of the area
	 * @param radius
	 *            The radius used as a double.
	 */
	public Area(Location location, Double radius)
	{
		this.location = location;
		this.radius = radius;
	}

	public Cuboid toCuboid()
	{
		return new Cuboid(location.clone().add(radius, radius, radius), location.clone().subtract(radius, radius, radius));
	}

	/**
	 * Used to instantiate a new "area" in which you can check if entities are
	 * within this area.
	 *
	 * @param location
	 *            The center location of the area
	 * @param radius
	 *            The radius used as an int.
	 */
	public Area(Location location, Integer radius)
	{
		this.location = location;
		this.radius = (double) radius;
	}

	/**
	 * Calculate the <STRONG>ESTIMATED distance</STRONG> from the center of this
	 * area, to the given location <STRONG>WARNING: This uses newton's method,
	 * be careful on how accurate you need this. As it is meant for FAST
	 * calculations with minimal load.</STRONG>
	 *
	 * @param location
	 *            The given location to calculate a distance from the center.
	 * @return Returns the distance of location from the center.
	 */
	public Double distance(Location location)
	{
		double c = this.location.distanceSquared(location);
		double t = c;

		for(int i = 0; i < 3; i++)
		{
			t = (c / t + t) / 2.0;
		}

		return t;
	}

	/**
	 * Calculate the <STRONG>EXACT distance</STRONG> from the center of this
	 * area, to the given location <STRONG>WARNING: This uses the sqrt function,
	 * be careful on how heavy you call this.</STRONG>
	 *
	 * @param location
	 *            The given location to calculate a distance from the center.
	 * @return Returns the distance of location from the center.
	 */
	public Double slowDistance(Location location)
	{
		return this.location.distance(location);
	}

	/**
	 * Check to see weather a location is within the area
	 *
	 * @param location
	 *            The location to measure from the center.
	 * @return Returns True if within; False if not.
	 */
	public boolean isWithin(Location location)
	{
		return this.location.distance(location) <= ((double) (radius * radius));
	}

	/**
	 * But does it have any entities?
	 *
	 * @return
	 */
	public boolean hasEntities()
	{
		return getNearbyEntities().length > 0;
	}

	/**
	 * Get all nearby entities matching the given entity type
	 *
	 * @param type
	 *            the entity type
	 * @return the nearby entities matching the given type
	 */
	public Entity[] getNearbyEntities(EntityType type)
	{
		GList<Entity> e = new GList<Entity>(getNearbyEntities());

		for(Entity i : e.copy())
		{
			if(!i.getType().equals(type))
			{
				e.remove(i);
			}
		}

		return e.toArray(new Entity[e.size()]);
	}

	/**
	 * Get nearby entities which match the following class
	 *
	 * @param entityClass
	 *            the entity class
	 * @return the nearby entities assignable from the given class
	 */
	public Entity[] getNearbyEntities(Class<? extends Entity> entityClass)
	{
		GList<Entity> e = new GList<Entity>(getNearbyEntities());

		for(Entity i : e.copy())
		{
			if(!i.getClass().isAssignableFrom(entityClass))
			{
				e.remove(i);
			}
		}

		return e.toArray(new Entity[e.size()]);
	}

	/**
	 * Get ALL entities within the area. <STRONG>NOTE: This is EVERY entity, not
	 * just LivingEntities. Drops, Particles, Mobs, Players, Everything</STRONG>
	 *
	 * @return Returns an Entity[] array of all entities within the given area.
	 */
	public Entity[] getNearbyEntities()
	{
		try
		{
			int chunkRadius = (int) (radius < 16 ? 1 : (radius - (radius % 16)) / 16);
			HashSet<Entity> radiusEntities = new HashSet<Entity>();

			for(int chX = 0 - chunkRadius; chX <= chunkRadius; chX++)
			{
				for(int chZ = 0 - chunkRadius; chZ <= chunkRadius; chZ++)
				{
					int x = (int) location.getX(), y = (int) location.getY(), z = (int) location.getZ();

					for(Entity e : new Location(location.getWorld(), x + (chX * 16), y, z + (chZ * 16)).getChunk().getEntities())
					{
						if(e.getLocation().distanceSquared(location) <= radius * radius && e.getLocation().getBlock() != location.getBlock())
						{
							radiusEntities.add(e);
						}
					}
				}
			}

			return radiusEntities.toArray(new Entity[radiusEntities.size()]);
		}

		catch(Exception e)
		{
			return new GList<Entity>().toArray(new Entity[0]);
		}
	}

	/**
	 * Get all players within the area.
	 *
	 * @return Returns an Player[] array of all players within the given area.
	 */
	public Player[] getNearbyPlayers()
	{
		GList<Player> px = new GList<Player>();

		for(Entity i : getNearbyEntities())
		{
			if(i.getType().equals(EntityType.PLAYER))
			{
				px.add((Player) i);
			}
		}

		return px.toArray(new Player[px.size()]);
	}

	/**
	 * Get the defined center location
	 *
	 * @return Returns the center location of the area
	 */
	public Location getLocation()
	{
		return location;
	}

	/**
	 * Set the defined center location
	 *
	 * @param location
	 *            The new location to be set
	 */
	public void setLocation(Location location)
	{
		this.location = location;
	}

	/**
	 * Gets the area's radius
	 *
	 * @return Returns the area's radius
	 */
	public Double getRadius()
	{
		return radius;
	}

	/**
	 * Set the area's radius
	 *
	 * @param radius
	 *            The new radius to be set
	 */
	public void setRadius(Double radius)
	{
		this.radius = radius;
	}

	/**
	 * Pick a random location in this radius
	 *
	 * @return
	 */
	public Location random()
	{
		Random r = new Random();
		double x = radius * ((r.nextDouble() - 0.5) * 2);
		double y = radius * ((r.nextDouble() - 0.5) * 2);
		double z = radius * ((r.nextDouble() - 0.5) * 2);

		return location.clone().add(x, y, z);
	}
}