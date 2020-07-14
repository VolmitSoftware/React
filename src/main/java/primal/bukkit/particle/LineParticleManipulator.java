package primal.bukkit.particle;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import primal.bukkit.world.VectorMath;

/**
 * A Particle effect which is a line from a to b. Override play(location) to
 * customize
 *
 * @author cyberpwn
 *
 */
public abstract class LineParticleManipulator extends ParticleManipulator
{
	/**
	 * Runs play(Location l) across a line between a and b with ppa particles
	 * per block
	 *
	 * @param a
	 *            the first location
	 * @param b
	 *            the second location
	 * @param ppb
	 *            plays per block. Setting this to 1 will play one particle per
	 *            block. Setting this to 2 will play 2 particles per block (more
	 *            density on the line)
	 */
	public void play(Location a, Location b, Double ppb)
	{
		Double dist = a.distance(b);
		Double jump = dist / ppb;
		Double jumps = dist * ppb;
		Location cursor = a.clone();
		Vector direction = VectorMath.direction(a, b);

		for(int i = 0; i < jumps; i++)
		{
			play(cursor);
			cursor = cursor.add(direction.clone().multiply(jump)).clone();
		}
	}
}