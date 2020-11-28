package primal.bukkit.world;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * Create a shape
 * 
 * @author cyberpwn
 */
public class Shape
{
	private Location location;
	private Vector offset;
	private Vector size;
	
	/**
	 * Create a shape
	 * 
	 * @param location
	 *            the location
	 * @param offset
	 *            the location offset
	 * @param size
	 *            the size
	 */
	public Shape(Location location, Vector offset, Vector size)
	{
		this.location = location;
		this.offset = offset;
		this.size = size;
	}
	
	/**
	 * Create a shape
	 * 
	 * @param location
	 *            the location
	 * @param size
	 *            the size
	 */
	public Shape(Location location, Vector size)
	{
		this(location, new Vector(0, 0, 0), size);
	}
	
	/**
	 * Create a shape
	 * 
	 * @param location
	 *            the location
	 */
	public Shape(Location location)
	{
		this(location, new Vector(1, 1, 1));
	}
	
	/**
	 * Is the given location within the shape?
	 * 
	 * @param l
	 *            the given location
	 * @return true if it is
	 */
	public boolean isWithinShape(Location l)
	{
		Location check = getCenter();
		
		if(l.getX() > check.getX() - size.getX() && l.getX() < check.getX() + size.getX())
		{
			if(l.getY() > check.getY() - size.getY() && l.getY() < check.getY() + size.getY())
			{
				if(l.getZ() > check.getZ() - size.getZ() && l.getZ() < check.getZ() + size.getZ())
				{
					return true;
				}
			}
		}
		
		return false;
	}
	
	/**
	 * Get a random location inside of the shape
	 * 
	 * @return the random location
	 */
	public Location randomLocation()
	{
		double mx = (Math.random() * (size.getX() * 2)) - size.getX();
		double my = (Math.random() * (size.getY() * 2)) - size.getY();
		double mz = (Math.random() * (size.getZ() * 2)) - size.getZ();
		
		return getCenter().add(new Vector(mx, my, mz));
	}
	
	/**
	 * Get a random point on the surface of this shape
	 * 
	 * @return the random location
	 */
	public Location randomSurface()
	{
		return getCenter().add(Math.random() >= 0.5 ? size.getX() : -size.getX(), Math.random() >= 0.5 ? size.getY() : -size.getY(), Math.random() >= 0.5 ? size.getZ() : -size.getZ());
	}
	
	/**
	 * Get the center of this shape
	 * 
	 * @return the center
	 */
	public Location getCenter()
	{
		return location.clone().add(offset);
	}
	
	public Location getLocation()
	{
		return location;
	}
	
	public Vector getOffset()
	{
		return offset;
	}
	
	public Vector getSize()
	{
		return size;
	}
}
