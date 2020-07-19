package com.volmit.react.controller;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Colorable;

import com.volmit.react.Config;
import com.volmit.react.Gate;
import com.volmit.react.React;
import com.volmit.react.ReactPlugin;
import com.volmit.react.Surge;
import com.volmit.react.api.Capability;
import com.volmit.react.api.StackData;
import com.volmit.react.api.StackedEntity;
import com.volmit.react.util.A;
import com.volmit.react.util.Area;
import com.volmit.react.util.Controller;
import com.volmit.react.util.D;
import com.volmit.react.util.F;
import com.volmit.react.util.MSound;
import com.volmit.react.util.Profiler;
import com.volmit.react.util.Protocol;
import com.volmit.react.util.S;
import com.volmit.react.util.TICK;

import org.json.JSONObject;
import primal.bukkit.sound.GSound;
import primal.lang.collection.GList;
import primal.lang.collection.GMap;

public class EntityStackController extends Controller
{
	private StackData sd;
	private GMap<Integer, StackedEntity> stacks = new GMap<>();
	private GList<LivingEntity> check = new GList<LivingEntity>();
	private boolean lay = false;

	@Override
	public void dump(JSONObject object)
	{
		object.put("active-stacks", stacks.size());
	}

	@Override
	public void start()
	{
		sd = new StackData();

		if(Config.ENTITY_STACK_CACHE && Config.ENTITYSTACK_ENABLED)
		{
			File ff = new File(new File(ReactPlugin.getI().getDataFolder(), "cache"), "stack-cache.ulf");

			if(ff.exists())
			{
				new A()
				{
					@Override
					public void run()
					{
						Profiler p = new Profiler();
						p.begin();
						D.v("Loading Entity Stacks from Cache...");

						try
						{
							sd.load(ff);
							p.end();
							D.v("Loaded " + F.f(sd.getEmap().size()) + " existing stacks in " + F.time(p.getMilliseconds(), 2));
						}

						catch(IOException e)
						{
							e.printStackTrace();
						}
					}
				};
			}
		}

		Surge.register(this);
	}

	public void saveCache()
	{
		File ff = new File(new File(ReactPlugin.getI().getDataFolder(), "cache"), "stack-cache.ulf");

		if(ff.exists())
		{
			new A()
			{
				@Override
				public void run()
				{
					Profiler p = new Profiler();
					p.begin();
					D.v("Saving Entity Stacks to Cache...");

					try
					{
						sd.save(ff);
						p.end();
						D.v("Saved " + F.f(sd.getEmap().size()) + " existing stacks in " + F.time(p.getMilliseconds(), 2));
					}

					catch(IOException e)
					{
						e.printStackTrace();
					}
				}
			};
		}
	}

	public void saveCacheFinal()
	{
		File ff = new File(new File(ReactPlugin.getI().getDataFolder(), "cache"), "stack-cache.ulf");
		Profiler p = new Profiler();
		p.begin();
		D.v("Saving Entity Stacks to Cache...");

		try
		{
			int from = sd.getEmap().size();
			sd.clear();
			int to = sd.getEmap().size();
			D.v("Defragmented Stack Cache: " + F.f(from) + " -> " + F.f(to) + " (" + F.pc((double) to / (double) from, 0) + " effective.");

			sd.save(ff);
			p.end();
			D.v("Saved " + F.f(sd.getEmap().size()) + " existing stacks in " + F.time(p.getMilliseconds(), 2));
		}

		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public void stop()
	{
		Surge.unregister(this);

		saveCacheFinal();
	}

	@Override
	public void tick()
	{
		if(!Config.ENTITYSTACK_ENABLED)
		{
			stacks.clear();
			return;
		}

		if(TICK.tick % 2400 == 0 && Config.ENTITY_STACK_CACHE)
		{
			saveCache();
		}

		if(TICK.tick % 10 == 0)
		{
			for(Map.Entry<Integer, StackedEntity> i : stacks.copy().entrySet())
			{
				if(!i.getValue().isValid())
				{
					i.getValue().remove();
					stacks.remove(i.getKey());
					continue;
				}

				i.getValue().update();
				sd.put(i.getValue().getEntity());
			}
		}

		if(Config.ENTITY_STACK_CACHE && TICK.tick % 20 == 0)
		{
			for(World i : Bukkit.getWorlds())
			{
				new S("fast-cache")
				{
					@Override
					public void run()
					{
						for(Chunk j : i.getLoadedChunks())
						{
							for(Entity k : j.getEntities())
							{
								if(k instanceof LivingEntity)
								{
									handle((LivingEntity) k);
								}
							}
						}
					}
				};
			}
		}

		if(Config.ENTITYSTACK_ONINTERVAL)
		{
			if(Config.ENTITYSTACK_FAST_INTERVAL && check.isEmpty())
			{
				for(World i : Bukkit.getWorlds())
				{
					for(Chunk j : i.getLoadedChunks())
					{
						for(Entity k : j.getEntities())
						{
							if(k instanceof LivingEntity)
							{
								check.add((LivingEntity) k);
							}
						}
					}
				}
			}

			else if(Config.ENTITYSTACK_FAST_INTERVAL)
			{
				for(LivingEntity i : check)
				{
					checkNear(i);
				}

				check.clear();
			}

			else if(TICK.tick % 20 == 0)
			{
				for(World i : Bukkit.getWorlds())
				{
					new S("stack-interval")
					{
						@Override
						public void run()
						{
							List<LivingEntity> le = i.getLivingEntities();
							for(int j = 0; j < 20; j++)
							{
								checkNear(le.get((int) (Math.random() * (i.getLivingEntities().size() - 1))));
							}
						}

					};
				}
			}
		}
	}

	private void handle(LivingEntity k)
	{
		if(!isStacked(k) && sd.get(k) > 0)
		{
			stacks.put(k.getEntityId(), new StackedEntity(k, sd.get(k)));
		}
	}

	@SuppressWarnings("deprecation")
	public void stack(GList<LivingEntity> e)
	{
		if(!Config.ENTITYSTACK_ENABLED)
		{
			return;
		}

		if(Config.ENTITY_STACK_MAX_COUNT < Config.ENTITYSTACK_MINIMUM_GROUP)
		{
			Config.ENTITY_STACK_MAX_COUNT = Config.ENTITYSTACK_MINIMUM_GROUP + 2;
		}

		if(!Capability.ENTITY_ATTRIBUTES.isCapable())
		{
			if(!e.isEmpty())
			{
				while(!e.isEmpty() && e.pickRandom().getMaxHealth() * (e.size()) > Config.ENTITYSTACK_MAXIMUM_HEALTH)
				{
					e.pop();
				}

				while(e.size() > Config.ENTITY_STACK_MAX_COUNT)
				{
					e.pop();
				}
			}
		}

		else
		{
			if(!e.isEmpty())
			{
				while(!e.isEmpty() && e.pickRandom().getAttribute(Attribute.GENERIC_MAX_HEALTH).getDefaultValue() * (e.size()) > Config.ENTITYSTACK_MAXIMUM_HEALTH)
				{
					e.pop();
				}

				while(e.size() > Config.ENTITY_STACK_MAX_COUNT)
				{
					e.pop();
				}
			}
		}

		if(e.size() < Config.ENTITYSTACK_MINIMUM_GROUP)
		{
			return;
		}

		LivingEntity le = e.pickRandom();

		for(LivingEntity i : e.copy())
		{
			if(!i.equals(le))
			{
				React.instance.featureController.merge(i, le);
				i.remove();
			}
		}

		StackedEntity se = new StackedEntity(le, e.size());
		stacks.put(le.getEntityId(), se);
	}

	public boolean isStacked(LivingEntity e)
	{
		if(!Config.ENTITYSTACK_ENABLED)
		{
			return false;
		}

		if(e.getType().equals(EntityType.ARMOR_STAND))
		{
			return false;
		}

		return getStack(e) != null;
	}

	public StackedEntity getStack(LivingEntity e)
	{
		if(!Config.ENTITYSTACK_ENABLED)
		{
			return null;
		}

		if(e.getType().equals(EntityType.ARMOR_STAND))
		{
			return null;
		}

		if(stacks.containsKey(e.getEntityId()))
		{
			return stacks.get(e.getEntityId());
		}

		for(StackedEntity i : stacks.values())
		{
			if(i.isEntity(e))
			{
				return i;
			}
		}

		return null;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(ItemSpawnEvent e)
	{
		if(e.getEntity().getItemStack().getType().equals(Material.EGG))
		{
			if(lay)
			{
				lay = false;
				return;
			}

			Area a = new Area(e.getLocation(), 0.5);

			for(Entity i : a.getNearbyEntities())
			{
				if(i.getType().equals(EntityType.CHICKEN))
				{
					if(isStacked((LivingEntity) i))
					{
						int extra = getStack((LivingEntity) i).getCount() - 1;

						if(extra > 0)
						{
							ItemStack is = new ItemStack(Material.EGG);
							is.setAmount(extra);
							lay = true;
							e.getEntity().getWorld().dropItem(e.getLocation(), is);
						}
					}
					break;
				}
			}
		}
	}

	@SuppressWarnings("deprecation")
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(PlayerShearEntityEvent e)
	{
		if(!Config.ENTITY_STACKER_SHEEP)
		{
			return;
		}

		if(e.isCancelled())
		{
			return;
		}

		if(e.getEntity() instanceof LivingEntity && isStacked((LivingEntity) e.getEntity()))
		{
			try
			{
				if(e.getEntity() instanceof Sheep)
				{
					Sheep s = (Sheep) e.getEntity();
					int count = (int) (getStack(s).getCount() * (Math.random() * 2D));

					if(s.isAdult() && !s.isSheared())
					{
						e.setCancelled(true);
						ItemStack is = null;
						if(Protocol.R1_13.to(Protocol.LATEST).contains(Protocol.getProtocolVersion()))
						{
							DyeColor color = s.getColor();
							try
							{
								is = ItemStack.class.getConstructor(Material.class, int.class).newInstance(Material.class.getMethod("valueOf", String.class).invoke(null, color.name() + "_WOOL"), count);
							}

							catch(InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e1)
							{

							}
						}

						else
						{
							Material m = Material.WOOL;
							byte d = s.getColor().getWoolData();
							is = new ItemStack(m, 1, (short) 0, d);
							is.setAmount(count);
						}
						s.getLocation().getWorld().dropItemNaturally(s.getLocation().clone().add(0, 1, 0), is);

						s.setSheared(true);

						if(!Gate.safe)
						{
							new GSound(MSound.SHEEP_SHEAR.bs(), 1f, (float) (0.5f + (1.25f * Math.random()))).play(s.getLocation());
						}

					}
				}

				e.setCancelled(true);
			}

			catch(Throwable ex)
			{

			}
		}
	}

	@EventHandler
	public void on(EntityDamageEvent e)
	{
		if(!Config.ENTITYSTACK_ENABLED)
		{
			return;
		}

		if(!(e.getEntity() instanceof LivingEntity))
		{
			return;
		}

		if(Config.ENTITY_STACK_NP_DAMAGE_NORMALIZATION && isStacked((LivingEntity) e.getEntity()))
		{
			switch(e.getCause())
			{
				case DRAGON_BREATH:
				case ENTITY_ATTACK:
				case ENTITY_EXPLOSION:
				case ENTITY_SWEEP_ATTACK:
				case MAGIC:
				case MELTING:
				case POISON:
				case PROJECTILE:
				case THORNS:
				case WITHER:
					return;
				default:
					break;
			}

			e.setDamage(e.getDamage() * (getStack((LivingEntity) e.getEntity()).getCount() + 2));
		}

		if(e.getEntity() instanceof LivingEntity)
		{
			if(isStacked((LivingEntity) e.getEntity()))
			{
				StackedEntity se = getStack((LivingEntity) e.getEntity());
				se.update();

				if(se.getCount() <= 1)
				{
					se.remove();
					stacks.remove(se.getEntity().getEntityId());
				}
			}
		}
	}

	@EventHandler
	public void on(EntityDamageByEntityEvent e)
	{
		if(!Config.ENTITYSTACK_ENABLED)
		{
			return;
		}

		if(e.getEntity() instanceof LivingEntity)
		{
			if(isStacked((LivingEntity) e.getEntity()))
			{
				StackedEntity se = getStack((LivingEntity) e.getEntity());
				se.setDamager(e.getDamager());
				se.update();

				if(se.getCount() <= 1)
				{
					se.remove();
					stacks.remove(se.getEntity().getEntityId());
				}
			}
		}
	}

	public void merge(StackedEntity a, StackedEntity b)
	{
		if(a.equals(b))
		{
			return;
		}

		if(a.getCount() + b.getCount() > Config.ENTITY_STACK_MAX_COUNT)
		{
			return;
		}

		StackedEntity se = new StackedEntity(a.getEntity(), a.getCount() + b.getCount());
		se.setHealth(a.getEntity().getHealth() + b.getEntity().getHealth());
		React.instance.featureController.merge(b.getEntity(), a.getEntity());
		se.update();
		b.getEntity().remove();
		stacks.remove(a.getEntity().getEntityId());
		stacks.remove(b.getEntity().getEntityId());
		b.destroy();
		se.update();
		stacks.put(se.getEntity().getEntityId(), se);
	}

	public void checkNear(LivingEntity e)
	{
		if(Config.USE_WORLD_CONFIGS)
		{
			if(!Config.getWorldConfig(e.getWorld()).allowStacking)
			{
				return;
			}
		}

		if(!Config.ENTITYSTACK_ENABLED)
		{
			return;
		}

		if(!e.isValid())
		{
			return;
		}
		if(Gate.isMythic(e) && !Config.STACK_MYTHIC_MOBS)
		{
			return;
		}
		Area a = new Area(e.getLocation().clone(), Config.ENTITYSTACK_GROUP_SEARCH_RADIUS);
		GList<LivingEntity> le = new GList<LivingEntity>();
		GList<StackedEntity> fullStacks = new GList<StackedEntity>();

		if(!isStacked(e))
		{
			le.add(e);
		}
		else
		{
			fullStacks.add(getStack(e));
		}

		for(Entity i : a.getNearbyEntities(e.getType()))
		{
			if(i.equals(e))
			{
				continue;
			}

			if(!Config.ALLOW_STACKING.contains(e.getType().toString()))
			{
				continue;
			}

			if(i.getType().equals(EntityType.ARMOR_STAND))
			{
				continue;
			}

			if(i.getType().equals(EntityType.DROPPED_ITEM))
			{
				continue;
			}

			if(Gate.isMythic(i) && !Config.STACK_MYTHIC_MOBS)
			{
				continue;
			}

			if(Config.ENTITY_SHEEP_COLOR_SPECIFIC && e.getType().equals(EntityType.SHEEP))
			{
				Colorable s = (Colorable) e;
				Colorable g = (Colorable) i;

				if(!s.getColor().equals(g.getColor()))
				{
					continue;
				}
			}

			if(!isStacked((LivingEntity) i))
			{
				le.add((LivingEntity) i);
			}
			else
			{
				fullStacks.add(getStack((LivingEntity) i));
			}
		}

		while(fullStacks.size() >= 2)
		{
			merge(fullStacks.pop(), fullStacks.pop());
		}

		if(le.size() >= Config.ENTITYSTACK_MINIMUM_GROUP)
		{
			stack(le);
		}
	}

	@EventHandler
	public void on(CreatureSpawnEvent e)
	{
		if(!Config.ENTITYSTACK_ENABLED)
		{
			return;
		}

		if(!Config.ENTITYSTACK_ONSPAWN)
		{
			return;
		}

		SpawnReason r = e.getSpawnReason();

		if(!Config.ENTITYSTACK_REASONS.contains(r.name().toLowerCase()))
		{
			return;
		}

		if(e.getEntity() != null)
		{
			if(!e.getEntity().isValid())
			{
				return;
			}

			if(!Config.ALLOW_STACKING.contains(e.getEntity().getType().toString()))
			{
				return;
			}

			if(e.getEntity().getType().equals(EntityType.ARMOR_STAND))
			{
				return;
			}

			if(e.getEntity().getType().equals(EntityType.DROPPED_ITEM))
			{
				return;
			}

			checkNear((LivingEntity) e.getEntity());
		}
	}

	@Override
	public int getInterval()
	{
		return 5;
	}

	@Override
	public boolean isUrgent()
	{
		return false;
	}
}
