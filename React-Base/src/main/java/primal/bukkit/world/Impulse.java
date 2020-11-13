package primal.bukkit.world;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import primal.lang.collection.GList;

public class Impulse
{
	private double radius;
	private double forceMax;
	private double forceMin;
	private GList<Entity> ignore;
	private double damageMin;
	private double damageMax;

	public Impulse(double radius)
	{
		ignore = new GList<Entity>();
		this.radius = radius;
		this.forceMax = 1;
		this.forceMin = 0;
		this.damageMax = 1;
		this.damageMin = 0;
	}

	public Impulse radius(double radius)
	{
		this.radius = radius;
		return this;
	}

	public Impulse force(double force)
	{
		this.forceMax = force;
		return this;
	}

	public Impulse force(double forceMax, double forceMin)
	{
		this.forceMax = forceMax;
		this.forceMin = forceMin;
		return this;
	}

	public Impulse damage(double damage)
	{
		this.damageMax = damage;
		return this;
	}

	public Impulse damage(double damageMax, double damageMin)
	{
		this.damageMax = damageMax;
		this.damageMin = damageMin;
		return this;
	}

	public void punch(Location at)
	{
		Area a = new Area(at, radius);

		for(Entity i : a.getNearbyEntities())
		{
			if(ignore.contains(i))
			{
				continue;
			}

			Vector force = VectorMath.direction(at, i.getLocation());
			double damage = 0;
			double distance = i.getLocation().distance(at);

			if(forceMin < forceMax)
			{
				force.clone().multiply(((1D - (distance / radius)) * (forceMax - forceMin)) + forceMin);
			}

			if(damageMin < damageMax)
			{
				damage = ((1D - (distance / radius)) * (damageMax - damageMin)) + damageMin;
			}

			try
			{
				if(i instanceof LivingEntity && damage > 0)
				{
					((LivingEntity) i).damage(damage);
				}

				i.setVelocity(i.getVelocity().add(force));
			}

			catch(Exception e)
			{

			}
		}
	}

	public Impulse ignore(Entity player)
	{
		ignore.add(player);
		return this;
	}
}
