package primal.bukkit.particle;
import org.bukkit.Location;

/**
 * A Particle effect which is an elptical shape created with a vector for volume
 * whd
 *
 * @author cyberpwn
 */
public abstract class SphereParticleManipulator extends ParticleManipulator
{
	/**
	 * Draw a sphere
	 *
	 * @param center
	 *            the center of the sphere
	 * @param radius
	 *            the radius of the sphere
	 * @param resolution
	 *            resolution is particle blocks. Setting the resolution to 1
	 *            would simply make each particle about one block apart, 2 being
	 *            2 particles per interval and so on. 0.5 being 2 blocks per
	 *            particle
	 */
	public void play(Location center, Double radius, Double resolution)
	{
		int i, j;
		int lats = (int) (resolution / (radius * 2)) + 1;
		int longs = (int) (resolution / (radius * 2)) + 1;

		for(i = 0; i <= lats; i++)
		{
			double lat0 = Math.PI * (-0.5 + (double) (i - 1) / lats);
			double z0 = Math.sin(lat0) * radius;
			double zr0 = Math.cos(lat0) * radius;

			double lat1 = Math.PI * (-0.5 + (double) i / lats);
			double z1 = Math.sin(lat1) * radius;
			double zr1 = Math.cos(lat1) * radius;

			for(j = 0; j <= longs; j++)
			{
				double lng = 2 * Math.PI * (double) (j - 1) / longs;
				double x = Math.cos(lng);
				double y = Math.sin(lng);

				play(center.clone().add(x * zr0, y * zr0, z0));
				play(center.clone().add(x * zr1, y * zr1, z1));
			}
		}
	}
}