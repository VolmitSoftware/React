package primal.bukkit.world;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import primal.compute.math.CDou;
import primal.lang.collection.GList;
import primal.lang.collection.GListAdapter;

/**
 * Vector utilities
 *
 * @author cyberpwn
 */
public class VectorMath
{
	/**
	 * Get all SIMPLE block faces from a more specific block face (SOUTH_EAST) =
	 * (south, east)
	 *
	 * @param f
	 *            the block face
	 * @return multiple faces, or one if the face is already simple
	 */
	public static GList<BlockFace> split(BlockFace f)
	{
		GList<BlockFace> faces = new GList<BlockFace>();

		switch(f)
		{
			case DOWN:
				faces.add(BlockFace.DOWN);
				break;
			case EAST:
				faces.add(BlockFace.EAST);
				break;
			case EAST_NORTH_EAST:
				faces.add(BlockFace.EAST);
				faces.add(BlockFace.EAST);
				faces.add(BlockFace.NORTH);
				break;
			case EAST_SOUTH_EAST:
				faces.add(BlockFace.EAST);
				faces.add(BlockFace.EAST);
				faces.add(BlockFace.SOUTH);
				break;
			case NORTH:
				faces.add(BlockFace.NORTH);
				break;
			case NORTH_EAST:
				faces.add(BlockFace.NORTH);
				faces.add(BlockFace.EAST);
				break;
			case NORTH_NORTH_EAST:
				faces.add(BlockFace.NORTH);
				faces.add(BlockFace.NORTH);
				faces.add(BlockFace.EAST);
				break;
			case NORTH_NORTH_WEST:
				faces.add(BlockFace.NORTH);
				faces.add(BlockFace.NORTH);
				faces.add(BlockFace.WEST);
				break;
			case NORTH_WEST:
				faces.add(BlockFace.NORTH);
				faces.add(BlockFace.WEST);
				break;
			case SELF:
				faces.add(BlockFace.SELF);
				break;
			case SOUTH:
				faces.add(BlockFace.SOUTH);
				break;
			case SOUTH_EAST:
				faces.add(BlockFace.SOUTH);
				faces.add(BlockFace.EAST);
				break;
			case SOUTH_SOUTH_EAST:
				faces.add(BlockFace.SOUTH);
				faces.add(BlockFace.SOUTH);
				faces.add(BlockFace.EAST);
				break;
			case SOUTH_SOUTH_WEST:
				faces.add(BlockFace.SOUTH);
				faces.add(BlockFace.SOUTH);
				faces.add(BlockFace.WEST);
				break;
			case SOUTH_WEST:
				faces.add(BlockFace.SOUTH);
				faces.add(BlockFace.WEST);
				break;
			case UP:
				faces.add(BlockFace.UP);
				break;
			case WEST:
				faces.add(BlockFace.WEST);
				break;
			case WEST_NORTH_WEST:
				faces.add(BlockFace.WEST);
				faces.add(BlockFace.WEST);
				faces.add(BlockFace.NORTH);
				break;
			case WEST_SOUTH_WEST:
				faces.add(BlockFace.WEST);
				faces.add(BlockFace.WEST);
				faces.add(BlockFace.SOUTH);
				break;
			default:
				break;
		}

		return faces;
	}

	/**
	 * Get a normalized vector going from a location to another
	 *
	 * @param from
	 *            from here
	 * @param to
	 *            to here
	 * @return the normalized vector direction
	 */
	public static Vector direction(Location from, Location to)
	{
		return to.subtract(from).toVector().normalize();
	}

	/**
	 * Get the vector direction from the yaw and pitch
	 *
	 * @param yaw
	 *            the yaw
	 * @param pitch
	 *            the pitch
	 * @return the vector
	 */
	public static Vector toVector(float yaw, float pitch)
	{
		return new Vector(Math.cos(pitch) * Math.cos(yaw), Math.sin(pitch), Math.cos(pitch) * Math.sin(-yaw));
	}

	/**
	 * Reverse a direction
	 *
	 * @param v
	 *            the direction
	 * @return the reversed direction
	 */
	public static Vector reverse(Vector v)
	{
		v.setX(-v.getX());
		v.setY(-v.getY());
		v.setZ(-v.getZ());
		return v;
	}

	/**
	 * Get a speed value from a vector (velocity)
	 *
	 * @param v
	 *            the vector
	 * @return the speed
	 */
	public static double getSpeed(Vector v)
	{
		Vector vi = new Vector(0, 0, 0);
		Vector vt = new Vector(0, 0, 0).add(v);

		return vi.distance(vt);
	}

	/**
	 * Shift all vectors based on the given vector
	 *
	 * @param vector
	 *            the vector direction to shift the vectors
	 * @param vectors
	 *            the vectors to be shifted
	 * @return the shifted vectors
	 */
	public static GList<Vector> shift(Vector vector, GList<Vector> vectors)
	{
		return new GList<Vector>(new GListAdapter<Vector, Vector>()
		{
			@Override
			public Vector onAdapt(Vector from)
			{
				return from.add(vector);
			}
		}.adapt(vectors));
	}

	/**
	 * Attempt to get the blockFace for the vector (will be tri-normalized)
	 *
	 * @param v
	 *            the vector
	 * @return the block face or null
	 */
	public static BlockFace getBlockFace(Vector v)
	{
		Vector p = triNormalize(v);

		for(BlockFace i : BlockFace.values())
		{
			if(p.getX() == i.getModX() && p.getY() == i.getModY() && p.getZ() == i.getModZ())
			{
				return i;
			}
		}

		for(BlockFace i : BlockFace.values())
		{
			if(p.getX() == i.getModX() && p.getZ() == i.getModZ())
			{
				return i;
			}
		}

		for(BlockFace i : BlockFace.values())
		{
			if(p.getY() == i.getModY() && p.getZ() == i.getModZ())
			{
				return i;
			}
		}

		for(BlockFace i : BlockFace.values())
		{
			if(p.getX() == i.getModX() || p.getY() == i.getModY())
			{
				return i;
			}
		}

		for(BlockFace i : BlockFace.values())
		{
			if(p.getX() == i.getModX() || p.getY() == i.getModY() || p.getZ() == i.getModZ())
			{
				return i;
			}
		}

		return null;
	}

	/**
	 * Angle the vector in a self relative direction
	 *
	 * @param v
	 *            the initial direction
	 * @param amt
	 *            the amount to shift in the direction
	 * @return the shifted direction
	 */
	public static Vector angleLeft(Vector v, float amt)
	{
		Location l = new Location(Bukkit.getWorlds().get(0), 0, 0, 0);
		l.setDirection(v);
		float y = l.getYaw();
		float p = l.getPitch();
		CDou cy = new CDou(360);
		CDou cp = new CDou(180);
		cy.set(y);
		cp.set(p);
		cy.sub(amt);
		l.setYaw((float) cy.get());
		l.setPitch((float) cp.get());

		return l.getDirection();
	}

	/**
	 * Angle the vector in a self relative direction
	 *
	 * @param v
	 *            the initial direction
	 * @param amt
	 *            the amount to shift in the direction
	 * @return the shifted direction
	 */
	public static Vector angleRight(Vector v, float amt)
	{
		Location l = new Location(Bukkit.getWorlds().get(0), 0, 0, 0);
		l.setDirection(v);
		float y = l.getYaw();
		float p = l.getPitch();
		CDou cy = new CDou(360);
		CDou cp = new CDou(180);
		cy.set(y);
		cp.set(p);
		cy.add(amt);
		l.setYaw((float) cy.get());
		l.setPitch((float) cp.get());

		return l.getDirection();
	}

	/**
	 * Angle the vector in a self relative direction
	 *
	 * @param v
	 *            the initial direction
	 * @param amt
	 *            the amount to shift in the direction
	 * @return the shifted direction
	 */
	public static Vector angleUp(Vector v, float amt)
	{
		Location l = new Location(Bukkit.getWorlds().get(0), 0, 0, 0);
		l.setDirection(v);
		float y = l.getYaw();
		float p = l.getPitch();
		CDou cy = new CDou(360);
		cy.set(y);
		l.setYaw((float) cy.get());
		l.setPitch((float) Math.max(-90, p - amt));

		return l.getDirection();
	}

	/**
	 * Angle the vector in a self relative direction
	 *
	 * @param v
	 *            the initial direction
	 * @param amt
	 *            the amount to shift in the direction
	 * @return the shifted direction
	 */
	public static Vector angleDown(Vector v, float amt)
	{
		Location l = new Location(Bukkit.getWorlds().get(0), 0, 0, 0);
		l.setDirection(v);
		float y = l.getYaw();
		float p = l.getPitch();
		CDou cy = new CDou(360);
		cy.set(y);
		l.setYaw((float) cy.get());
		l.setPitch((float) Math.min(90, p + amt));

		return l.getDirection();
	}

	/**
	 * (clone) Force normalize the vector into three points, 1, 0, or -1. If the
	 * value
	 * is > 0.333 (1) if the value is less than -0.333 (-1) else 0
	 *
	 * @param direction
	 *            the direction
	 * @return the vector
	 */
	public static Vector triNormalize(Vector direction)
	{
		Vector v = direction.clone();
		v.normalize();

		if(v.getX() > 0.333)
		{
			v.setX(1);
		}

		else if(v.getX() < -0.333)
		{
			v.setX(-1);
		}

		else
		{
			v.setX(0);
		}

		if(v.getY() > 0.333)
		{
			v.setY(1);
		}

		else if(v.getY() < -0.333)
		{
			v.setY(-1);
		}

		else
		{
			v.setY(0);
		}

		if(v.getZ() > 0.333)
		{
			v.setZ(1);
		}

		else if(v.getZ() < -0.333)
		{
			v.setZ(-1);
		}

		else
		{
			v.setZ(0);
		}

		return v;
	}
}