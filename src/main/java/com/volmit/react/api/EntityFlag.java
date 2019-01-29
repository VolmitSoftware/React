package com.volmit.react.api;

import org.bukkit.entity.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

import com.volmit.react.Gate;
import com.volmit.react.React;
import com.volmit.volume.lang.collections.GSet;

public enum EntityFlag
{
	BREEDING("breeding"),
	MYTHIC("mythic"),
	BUILD_IRONGOLEM("build-irongolem"),
	BUILD_SNOWMAN("build-snowman"),
	BUILD_WITHER("build-wither"),
	CHUNK_GEN("chunkgen"),
	CURED("cured"),
	CUSTOM("custom"),
	DEFAULT("default"),
	DISPENSE_EGG("dispense-egg"),
	EGG("egg"),
	ENDER_PEARL("ender-pearl"),
	INFECTION("infection"),
	JOCKEY("jockey"),
	LIGHTNING("lightning"),
	MOUNT("mount"),
	NATURAL("natural"),
	NETHER_PORTAL("nether-portal"),
	OCELOT_BABY("ocelot"),
	REINFORCEMENTS("reinforcements"),
	SHOULDER_ENTITY("shoulder-entity"),
	SILVERFISH_BLOCK("silverfish"),
	SLIME_SPLIT("slime-split"),
	SPAWNER("spawner"),
	SPAWNER_EGG("spawner-egg"),
	TRAP("trap"),
	VILLAGE_DEFENSE("village-defense"),
	VILLAGE_INVASION("village-invasion"),
	NAMED("named"),
	TAMED("tamed"),
	STACKED("stacked"),
	RIDDEN("ridden"),
	LIVING("living"),
	NON_LIVING("non-living"),
	PASSIVE("passive"),
	HOSTILE("hostile"),
	MATURE("mature"),
	YOUNG("young"),
	UNDERWATER("underwater"),
	GROUNDED("grounded"),
	AIRBORNE("airborne"),
	PROJECTILES("projectiles"),
	CAVES("caves"),
	NEARBY("nearby"),
	LIT("lit"),
	LEASHED("leashed");

	private String m;
	private boolean spawnRule;

	private EntityFlag(String m)
	{
		this.m = m;
		spawnRule = false;
	}

	private EntityFlag(String m, boolean spawnRule)
	{
		this.spawnRule = spawnRule;
		this.m = m;
	}

	public SpawnReason getReason()
	{
		return isSpawnRule() ? SpawnReason.valueOf(name()) : null;
	}

	public String getM()
	{
		return m;
	}

	public boolean isSpawnRule()
	{
		return spawnRule;
	}

	public static GSet<EntityFlag> getFlags(Entity e)
	{
		GSet<EntityFlag> flags = new GSet<EntityFlag>();

		for(EntityFlag i : values())
		{
			if(i.is(e))
			{
				flags.add(i);
			}
		}

		if(React.instance.entityCullController.getTrackReasons().containsKey(e.getUniqueId()))
		{
			flags.add(EntityFlag.valueOf(React.instance.entityCullController.getTrackReasons().get(e.getUniqueId()).name()));
		}

		return flags;
	}

	public boolean is(Entity e)
	{
		if(this.equals(EntityFlag.MYTHIC))
		{
			return Gate.isMythic(e);
		}

		if(this.equals(EntityFlag.UNDERWATER))
		{
			return e.getLocation().getBlock().isLiquid();
		}

		if(!e.getType().equals(EntityType.ARMOR_STAND))
		{
			if(this.equals(EntityFlag.CAVES))
			{
				return e.getLocation().getBlock().getLightFromSky() < 9;
			}

			if(this.equals(EntityFlag.LIT))
			{
				return e.getLocation().getBlock().getLightFromSky() >= 9;
			}
		}

		if(this.equals(EntityFlag.GROUNDED))
		{
			return e.isOnGround();
		}

		if(this.equals(EntityFlag.AIRBORNE))
		{
			return !e.isOnGround() && !e.getLocation().getBlock().isLiquid();
		}

		if(this.equals(EntityFlag.MATURE))
		{
			return e instanceof Ageable && ((Ageable) e).isAdult();
		}

		if(this.equals(EntityFlag.YOUNG))
		{
			return e instanceof Ageable && !((Ageable) e).isAdult();
		}

		if(this.equals(EntityFlag.LIVING))
		{
			return e instanceof LivingEntity;
		}

		if(this.equals(EntityFlag.PASSIVE))
		{
			return e instanceof Animals;
		}

		if(this.equals(EntityFlag.HOSTILE))
		{
			return e instanceof Monster;
		}

		if(this.equals(EntityFlag.HOSTILE))
		{
			return e instanceof Monster;
		}

		if(this.equals(EntityFlag.NON_LIVING))
		{
			return !(e instanceof LivingEntity);
		}

		if(this.equals(EntityFlag.PROJECTILES))
		{
			return e instanceof Projectile;
		}

		if(this.equals(EntityFlag.LEASHED))
		{
			return e instanceof LivingEntity && ((LivingEntity) e).isLeashed();
		}

		if(this.equals(EntityFlag.NAMED))
		{
			if(!Capability.ENTITY_NAMES.isCapable())
			{
				return false;
			}

			return e.isCustomNameVisible() || e.getCustomName() != null;
		}

		if(this.equals(EntityFlag.RIDDEN) || this.equals(EntityFlag.STACKED))
		{
			if(!Capability.PASSENGERS.isCapable())
			{
				return false;
			}

			return !e.getPassengers().isEmpty();
		}

		if(this.equals(EntityFlag.TAMED))
		{
			return e instanceof Tameable && ((Tameable) e).isTamed();
		}

		return false;
	}

	@Override
	public String toString()
	{
		return m;
	}
}
