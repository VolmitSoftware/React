package com.volmit.react.api;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

public class CachedEntity
{
	private UUID uid;
	private int type;
	private int ticksLived;
	private String world;
	private double x;
	private double y;
	private double z;
	private double vx;
	private double vy;
	private double vz;
	private double dx;
	private double dy;
	private double dz;

	public CachedEntity(Entity e)
	{
		uid = e.getUniqueId();
		type = e.getType().ordinal();
		ticksLived = e.getTicksLived();
		world = e.getLocation().getWorld().getName();
		x = e.getLocation().getX();
		y = e.getLocation().getY();
		z = e.getLocation().getZ();
		vx = e.getVelocity().getX();
		vy = e.getVelocity().getY();
		vz = e.getVelocity().getZ();
		dx = e.getLocation().getDirection().getX();
		dy = e.getLocation().getDirection().getY();
		dz = e.getLocation().getDirection().getZ();
	}

	public void apply(Entity e)
	{
		e.setTicksLived(ticksLived + 2);
		Location l = new Location(Bukkit.getWorld(world), x, y, z);
		l.setDirection(new Vector(dx, dy, dz));
		Vector v = new Vector(vx, vy, vz);
		e.teleport(l);
		e.setVelocity(v);
	}

	public int getType()
	{
		return type;
	}

	public void setType(int type)
	{
		this.type = type;
	}

	public int getTicksLived()
	{
		return ticksLived;
	}

	public void setTicksLived(int ticksLived)
	{
		this.ticksLived = ticksLived;
	}

	public String getWorld()
	{
		return world;
	}

	public void setWorld(String world)
	{
		this.world = world;
	}

	public double getX()
	{
		return x;
	}

	public void setX(double x)
	{
		this.x = x;
	}

	public double getY()
	{
		return y;
	}

	public void setY(double y)
	{
		this.y = y;
	}

	public double getZ()
	{
		return z;
	}

	public void setZ(double z)
	{
		this.z = z;
	}

	public double getVx()
	{
		return vx;
	}

	public void setVx(double vx)
	{
		this.vx = vx;
	}

	public double getVy()
	{
		return vy;
	}

	public void setVy(double vy)
	{
		this.vy = vy;
	}

	public double getVz()
	{
		return vz;
	}

	public void setVz(double vz)
	{
		this.vz = vz;
	}

	public double getDx()
	{
		return dx;
	}

	public void setDx(double dx)
	{
		this.dx = dx;
	}

	public double getDy()
	{
		return dy;
	}

	public void setDy(double dy)
	{
		this.dy = dy;
	}

	public double getDz()
	{
		return dz;
	}

	public void setDz(double dz)
	{
		this.dz = dz;
	}

	public UUID getUid()
	{
		return uid;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		long temp;
		temp = Double.doubleToLongBits(dx);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(dy);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(dz);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		result = prime * result + ticksLived;
		result = prime * result + type;
		result = prime * result + ((uid == null) ? 0 : uid.hashCode());
		temp = Double.doubleToLongBits(vx);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(vy);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(vz);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		result = prime * result + ((world == null) ? 0 : world.hashCode());
		temp = Double.doubleToLongBits(x);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(y);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(z);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		CachedEntity other = (CachedEntity) obj;
		if(Double.doubleToLongBits(dx) != Double.doubleToLongBits(other.dx))
			return false;
		if(Double.doubleToLongBits(dy) != Double.doubleToLongBits(other.dy))
			return false;
		if(Double.doubleToLongBits(dz) != Double.doubleToLongBits(other.dz))
			return false;
		if(ticksLived != other.ticksLived)
			return false;
		if(type != other.type)
			return false;
		if(uid == null)
		{
			if(other.uid != null)
				return false;
		}
		else if(!uid.equals(other.uid))
			return false;
		if(Double.doubleToLongBits(vx) != Double.doubleToLongBits(other.vx))
			return false;
		if(Double.doubleToLongBits(vy) != Double.doubleToLongBits(other.vy))
			return false;
		if(Double.doubleToLongBits(vz) != Double.doubleToLongBits(other.vz))
			return false;
		if(world == null)
		{
			if(other.world != null)
				return false;
		}
		else if(!world.equals(other.world))
			return false;
		if(Double.doubleToLongBits(x) != Double.doubleToLongBits(other.x))
			return false;
		if(Double.doubleToLongBits(y) != Double.doubleToLongBits(other.y))
			return false;
		if(Double.doubleToLongBits(z) != Double.doubleToLongBits(other.z))
			return false;
		return true;
	}
}
