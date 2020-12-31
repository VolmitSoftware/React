package primal.bukkit.world;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * Ray Tracing! Start at a location and override the trace function to manually
 * raytrace.
 *
 * @author cyberpwn
 *
 */
public class RayTrace
{
	private Location start;
	private Vector direction;
	private Double maxDistance;
	private Double distanceCovered;
	private Double step;
	private Boolean stop;

	/**
	 * Create a RayTrace instance to be fired with trace() and overridden with
	 * onTrace(Location l)
	 *
	 * @param start
	 *            the starting location for this trace
	 * @param direction
	 *            the direction this trace should go
	 * @param maxDistance
	 *            the max distance this trace can cover
	 * @param step
	 *            the step. 1 for normal. 0.5 for twice as many hits, 2 for half
	 *            as many.
	 */
	public RayTrace(Location start, Vector direction, Double maxDistance, Double step)
	{
		this.start = start;
		this.direction = direction;
		this.maxDistance = maxDistance;
		this.distanceCovered = 0.0;
		this.step = step;
		this.stop = false;
	}

	/**
	 * Begins the raytrace operation and calls onTrace(Location l) each step
	 */
	public void trace()
	{
		Location current = start;

		while(distanceCovered < maxDistance && !stop)
		{
			Vector stepper = direction.clone().normalize().multiply(step);
			current.add(stepper);
			onTrace(current.clone());
			distanceCovered += step;

			if(distanceCovered >= maxDistance)
			{
				break;
			}
		}
	}

	/**
	 * Stops the raytrace. You can only call this while it is running inside of
	 * the onTrace(Location l) method
	 */
	public void stop()
	{
		stop = true;
	}

	/**
	 * Called each iteration in the raytrace
	 *
	 * @param location
	 *            the location hit
	 */
	public void onTrace(Location location)
	{

	}

	public Location getStart()
	{
		return start;
	}

	public void setStart(Location start)
	{
		this.start = start;
	}

	public Vector getDirection()
	{
		return direction;
	}

	public void setDirection(Vector direction)
	{
		this.direction = direction;
	}

	public Double getMaxDistance()
	{
		return maxDistance;
	}

	public void setMaxDistance(Double maxDistance)
	{
		this.maxDistance = maxDistance;
	}

	public Double getDistanceCovered()
	{
		return distanceCovered;
	}

	public void setDistanceCovered(Double distanceCovered)
	{
		this.distanceCovered = distanceCovered;
	}

	public Double getStep()
	{
		return step;
	}

	public void setStep(Double step)
	{
		this.step = step;
	}
}