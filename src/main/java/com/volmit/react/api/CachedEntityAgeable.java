package com.volmit.react.api;

import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;

public class CachedEntityAgeable extends CachedEntityLiving
{
	private int age;
	private boolean ageLock;
	private boolean breed;

	public CachedEntityAgeable(Ageable e)
	{
		super(e);

		age = e.getAge();
		breed = e.canBreed();
		ageLock = e.getAgeLock();
	}

	@Override
	public void apply(Entity ee)
	{
		super.apply(ee);

		Ageable e = (Ageable) ee;
		e.setAge(age);
		e.setBreed(breed);
		e.setAgeLock(ageLock);
	}

	public int getAge()
	{
		return age;
	}

	public void setAge(int age)
	{
		this.age = age;
	}

	public boolean isAgeLock()
	{
		return ageLock;
	}

	public void setAgeLock(boolean ageLock)
	{
		this.ageLock = ageLock;
	}

	public boolean isBreed()
	{
		return breed;
	}

	public void setBreed(boolean breed)
	{
		this.breed = breed;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + age;
		result = prime * result + (ageLock ? 1231 : 1237);
		result = prime * result + (breed ? 1231 : 1237);
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true;
		if(!super.equals(obj))
			return false;
		if(getClass() != obj.getClass())
			return false;
		CachedEntityAgeable other = (CachedEntityAgeable) obj;
		if(age != other.age)
			return false;
		if(ageLock != other.ageLock)
			return false;
		if(breed != other.breed)
			return false;
		return true;
	}

}
