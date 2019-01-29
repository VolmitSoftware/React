package com.volmit.react;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.spigotmc.SpigotWorldConfig;
import org.spigotmc.TickLimiter;

import com.volmit.react.api.ActivationRangeType;
import com.volmit.react.api.Capability;
import com.volmit.react.api.ChunkIssue;
import com.volmit.react.api.DeadEntity;
import com.volmit.react.api.EntityFlag;
import com.volmit.react.api.Flavor;
import com.volmit.react.api.IActionSource;
import com.volmit.react.api.LagMap;
import com.volmit.react.api.LagMapChunk;
import com.volmit.react.api.NobodySender;
import com.volmit.react.api.Notification;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactPlayer;
import com.volmit.react.api.SelectorPosition;
import com.volmit.react.controller.EventController;
import com.volmit.react.util.A;
import com.volmit.react.util.C;
import com.volmit.react.util.Callback;
import com.volmit.react.util.Control;
import com.volmit.react.util.DataCluster;
import com.volmit.react.util.Ex;
import com.volmit.react.util.F;
import com.volmit.react.util.I;
import com.volmit.react.util.IController;
import com.volmit.react.util.JSONArray;
import com.volmit.react.util.JSONObject;
import com.volmit.react.util.M;
import com.volmit.react.util.MSound;
import com.volmit.react.util.NMSX;
import com.volmit.react.util.P;
import com.volmit.react.util.Protocol;
import com.volmit.react.util.S;
import com.volmit.react.util.TICK;
import com.volmit.react.util.TXT;
import com.volmit.react.util.Task;
import com.volmit.react.util.TaskLater;
import com.volmit.react.util.W;
import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;
import com.volmit.volume.lang.collections.GSet;

import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.mobs.MythicMob;

public class Gate
{
	public static boolean safe = false;
	public static boolean lowMemoryMode = false;
	public static GMap<Integer, DeadEntity> markedForDeath = new GMap<Integer, DeadEntity>();
	public static int snd = 3;
	private static GMap<String, Integer> defaultSettings = new GMap<String, Integer>();
	private static GSet<Chunk> refresh = new GSet<Chunk>();
	private static GSet<Location> destroy = new GSet<Location>();
	public static int cd = 0;
	public static C themeColor = C.AQUA;
	public static C darkColor = C.DARK_GRAY;
	public static C textColor = C.GRAY;
	private static GList<C> crgb = new GList<C>();
	private static GList<EntityType> ed;
	public static boolean stable = true;

	public static GList<CreatureSpawner> getSpawners(Chunk c)
	{
		GList<CreatureSpawner> sp = new GList<CreatureSpawner>();

		for(BlockState i : c.getTileEntities())
		{
			if(i instanceof CreatureSpawner)
			{
				sp.add((CreatureSpawner) i);
			}
		}

		return sp;
	}

	public static boolean isLowMemory()
	{
		return lowMemoryMode;
	}

	public static void toggleLowMemoryMode()
	{
		setLowMemoryMode(!isLowMemory());
	}

	public static void setLowMemoryMode(boolean lmm)
	{
		lowMemoryMode = lmm;
	}

	static
	{
		crgb.add(C.AQUA);
		crgb.add(C.BLUE);
		crgb.add(C.GOLD);
		crgb.add(C.GREEN);
		crgb.add(C.LIGHT_PURPLE);
		crgb.add(C.RED);
		crgb.add(C.YELLOW);
		crgb.add(C.DARK_AQUA);
		crgb.add(C.DARK_BLUE);
		crgb.add(C.DARK_GREEN);
		crgb.add(C.DARK_PURPLE);
		crgb.add(C.DARK_RED);
		crgb.add(C.WHITE);
	}

	public static boolean mythicMobs()
	{
		return Capability.MYTHIC_MOBS.isCapable();
	}

	public static GList<String> getMythicTypes()
	{
		GList<String> m = new GList<String>();

		if(!mythicMobs())
		{
			return m;
		}

		for(MythicMob i : MythicMobs.inst().getMobManager().getMobTypes())
		{
			m.add(i.getInternalName());
		}

		return m;
	}

	public static boolean isMythic(Entity e)
	{
		if(!mythicMobs())
		{
			return false;
		}

		return MythicMobs.inst().getAPIHelper().isMythicMob(e);
	}

	public static void removeMythicMob(Entity e)
	{
		if(!mythicMobs() || !isMythic(e))
		{
			return;
		}

		MythicMobs.inst().getAPIHelper().getMythicMobInstance(e).setDespawned();
		MythicMobs.inst().getAPIHelper().getMythicMobInstance(e).getEntity().remove();
	}

	public static String getMythicType(Entity e)
	{
		if(!mythicMobs() || !isMythic(e))
		{
			return null;
		}

		return MythicMobs.inst().getAPIHelper().getMythicMobInstance(e).getType().getInternalName();
	}

	public static boolean factions()
	{
		return Bukkit.getServer().getPluginManager().getPlugin("Factions") != null;
	}

	public static void syncColor()
	{
		if(Config.STYLE_THEME_COLOR.equalsIgnoreCase("RGB"))
		{
			themeColor = crgb.pickRandom();
		}

		else
		{
			try
			{
				themeColor = C.valueOf(Config.STYLE_THEME_COLOR.toUpperCase());
			}

			catch(Exception e)
			{

			}
		}

		try
		{
			darkColor = C.valueOf(Config.STYLE_DARK_COLOR.toUpperCase());
		}

		catch(Exception e)
		{

		}

		try
		{
			textColor = C.valueOf(Config.STYLE_TEXT_COLOR.toUpperCase());
		}

		catch(Exception e)
		{

		}
	}

	public static void fixLighting(SelectorPosition sel, Callback<Integer> cb, Callback<Double> prog)
	{
		if(Config.SAFE_MODE_FAWE)
		{
			return;
		}

		new A()
		{
			@Override
			public void run()
			{
				if(hasFawe())
				{
					try
					{
						Class<?> vectorClass = Class.forName("com.sk89q.worldedit.Vector"); //$NON-NLS-1$
						Class<?> cuboidClass = Class.forName("com.sk89q.worldedit.regions.CuboidRegion"); //$NON-NLS-1$
						Class<?> regionClass = Class.forName("com.sk89q.worldedit.regions.Region"); //$NON-NLS-1$
						Class<?> faweapClass = Class.forName("com.boydti.fawe.FaweAPI"); //$NON-NLS-1$
						Constructor<?> cuboidConstruct = cuboidClass.getConstructor(vectorClass, vectorClass);
						Constructor<?> vectorConstruct = vectorClass.getConstructor(int.class, int.class, int.class);
						Method faweFixMethod = faweapClass.getMethod("fixLighting", String.class, regionClass); //$NON-NLS-1$
						Integer[] total = {0};
						Integer[] sof = {0};
						Integer tot = sel.getPossibilities().size();
						int kv = 0;

						for(Object o : new GList<Object>(sel.getPossibilities()).shuffleCopy())
						{
							if(!sel.can(o))
							{
								continue;
							}

							if(M.r(0.1))
							{
								kv += 5;
							}

							new TaskLater("fq-chunkwait", kv)
							{
								@Override
								public void run()
								{
									try
									{
										Chunk c = (Chunk) o;

										if(Config.getWorldConfig(c.getWorld()).allowRelighting)
										{
											Object vector1 = vectorConstruct.newInstance(c.getX() << 4, 0, c.getZ() << 4);
											Object vector2 = vectorConstruct.newInstance(16 + (c.getX() << 4), 256, 16 + (c.getZ() << 4));
											Object cuboid = cuboidConstruct.newInstance(vector1, vector2);
											Object ret = faweFixMethod.invoke(null, c.getWorld().getName(), cuboid);
											total[0] += (int) ret;
											sof[0]++;
											prog.run(sof[0].doubleValue() / tot.doubleValue());
										}
									}

									catch(Throwable e)
									{

									}
								}
							};
						}

						new TaskLater("fq-chunkwait-finish", kv)
						{
							@Override
							public void run()
							{
								try
								{
									prog.run(1.0);
									cb.run(total[0]);
								}

								catch(Throwable e)
								{

								}
							}
						};
					}

					catch(

							Throwable e)
					{
						Ex.t(e);
						prog.run(1.0);
						cb.run(-1);
						return;
					}
				}
			}
		};
	}

	public static void markForDeath(Entity e, int ticks)
	{
		if(Config.USE_COLLISION && Config.USE_COLLISION_ON_CULL && e instanceof LivingEntity)
		{
			React.instance.collisionController.precull((LivingEntity) e);
		}

		if(!markedForDeath.containsKey(e.getEntityId()))
		{
			markedForDeath.put(e.getEntityId(), new DeadEntity(e, (int) (ticks + (Math.random() * (ticks / 4)))));
		}
	}

	public static void tickDeath()
	{
		for(Integer i : markedForDeath.k())
		{
			if(markedForDeath.get(i).c())
			{
				markedForDeath.remove(i);
			}
		}
	}

	public static Player whoLoaded(Chunk c)
	{
		try
		{
			if(c.isLoaded())
			{
				for(Entity i : c.getEntities())
				{
					if(i instanceof Player)
					{
						return (Player) i;
					}
				}

				for(int i = 1; i < Bukkit.getViewDistance() + 1; i++)
				{
					for(Chunk j : W.chunkRadius(c, i + 1))
					{
						for(Entity k : j.getEntities())
						{
							if(k instanceof Player)
							{
								return (Player) k;
							}
						}
					}
				}
			}
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}

		return null;
	}

	public static int getChunkCountForView()
	{
		return (int) Math.pow((Bukkit.getViewDistance() * 2) + 1, 2);
	}

	public static int getMaxChunksForView()
	{
		return (getChunkCountForView()) * (Bukkit.getOnlinePlayers().size() + 1);
	}

	public static boolean hasFawe()
	{
		if(Config.SAFE_MODE_FAWE)
		{
			return false;
		}

		return Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null; //$NON-NLS-1$
	}

	public static void tickEntityNextTickListTick(World world) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, SecurityException, NoSuchFieldException
	{
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}

		Class<?> cworldclass = NMSX.getCBClass("CraftWorld"); //$NON-NLS-1$
		Object theWorld = cworldclass.getMethod("getHandle").invoke(world); //$NON-NLS-1$
		Field f = deepFindField(theWorld, "entityLimiter"); //$NON-NLS-1$
		f.setAccessible(true);
		TickLimiter tl = (TickLimiter) f.get(theWorld);
		Field ff = tl.getClass().getDeclaredField("maxTime"); //$NON-NLS-1$
		ff.setAccessible(true);
		int maxTime = (int) ff.get(tl);

		if(maxTime > 1 && tl.shouldContinue())
		{
			tweakEntityTickMax(world, maxTime - 1);
		}

		if(TICK.tick % 40 == 0)
		{
			if(maxTime < 50 && !tl.shouldContinue())
			{
				tweakEntityTickMax(world, maxTime + 1);
			}
		}
	}

	public static void resetEntityMaxTick(World world) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, NoSuchFieldException
	{
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}

		if(defaultSettings.containsKey(world.getName() + "-entitymaxtick")) //$NON-NLS-1$
		{
			tweakEntityTickMax(world, defaultSettings.get(world.getName() + "-entitymaxtick")); //$NON-NLS-1$
		}
	}

	public static int getEntityTickMax(World world) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, NoSuchFieldException
	{
		return getSpigotConfig(world).entityMaxTickTime;
	}

	public static int getTileTickMax(World world) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, NoSuchFieldException
	{
		return getSpigotConfig(world).tileMaxTickTime;
	}

	public static void tweakEntityTickMax(World world, int tt) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, NoSuchFieldException
	{
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}

		SpigotWorldConfig wc = getSpigotConfig(world);

		if(!defaultSettings.containsKey(world.getName() + "-entitymaxtick")) //$NON-NLS-1$
		{
			defaultSettings.put(world.getName() + "-entitymaxtick", getEntityTickMax(world)); //$NON-NLS-1$
		}

		wc.entityMaxTickTime = tt;
		forceSet(wc, "max-tick-time.entity", tt); //$NON-NLS-1$
		Class<?> cworldclass = NMSX.getCBClass("CraftWorld"); //$NON-NLS-1$
		Object theWorld = cworldclass.getMethod("getHandle").invoke(world); //$NON-NLS-1$
		Field f = deepFindField(theWorld, "entityLimiter"); //$NON-NLS-1$

		if(f != null)
		{
			f.setAccessible(true);
			f.set(theWorld, new TickLimiter(tt));
		}
	}

	public static Field deepFindField(Object obj, String fieldName)
	{
		Class<?> cls = obj.getClass();

		for(Class<?> acls = cls; acls != null; acls = acls.getSuperclass())
		{
			try
			{
				Field field = acls.getDeclaredField(fieldName);

				return field;
			}

			catch(NoSuchFieldException ex)
			{

			}
		}

		return null;
	}

	public static void forceSet(SpigotWorldConfig v, String key, Object value) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchFieldException
	{
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}

		Field f = v.getClass().getDeclaredField("config"); //$NON-NLS-1$
		f.setAccessible(true);
		YamlConfiguration fc = (YamlConfiguration) f.get(v);
		fc.set("world-settings.default." + key, value); //$NON-NLS-1$
	}

	public static int getActivationRange(World world, ActivationRangeType type) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, NoSuchFieldException
	{
		if(Config.SAFE_MODE_NMS)
		{
			return -1;
		}

		switch(type)
		{
			case ANIMALS:
				return getSpigotConfig(world).animalActivationRange;
			case MISC:
				return getSpigotConfig(world).miscActivationRange;
			case MONSTERS:
				return getSpigotConfig(world).monsterActivationRange;
			default:
				break;
		}

		return -1;
	}

	public static void resetActivationRange(World world, ActivationRangeType type) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, NoSuchFieldException
	{
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}

		if(defaultSettings.containsKey(world.getName() + "-" + type.toString())) //$NON-NLS-1$
		{
			tweakActivationRange(world, type, defaultSettings.get(world.getName() + "-" + type.toString())); //$NON-NLS-1$
		}
	}

	public static void tweakActivationRange(World world, ActivationRangeType type, int distance) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, NoSuchFieldException
	{
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}

		SpigotWorldConfig conf = getSpigotConfig(world);

		if(!defaultSettings.containsKey(world.getName() + "-" + type.toString())) //$NON-NLS-1$
		{
			defaultSettings.put(world.getName() + "-" + type.toString(), getActivationRange(world, type)); //$NON-NLS-1$
		}

		switch(type)
		{
			case ANIMALS:
				conf.animalActivationRange = distance;
			case MISC:
				conf.miscActivationRange = distance;
			case MONSTERS:
				conf.monsterActivationRange = distance;
			default:
				break;
		}
	}

	public static SpigotWorldConfig getSpigotConfig(World world) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, NoSuchFieldException
	{
		if(Config.SAFE_MODE_NMS)
		{
			return null;
		}

		Class<?> cworldclass = NMSX.getCBClass("CraftWorld"); //$NON-NLS-1$
		Object theWorld = cworldclass.getMethod("getHandle").invoke(world); //$NON-NLS-1$
		SpigotWorldConfig wc = (SpigotWorldConfig) theWorld.getClass().getField("spigotConfig").get(theWorld); //$NON-NLS-1$

		return wc;
	}

	public static String msg(CommandSender p, String msg)
	{
		syncColor();

		String s = TXT.makeTag(themeColor, darkColor, textColor, Info.CORE_NAME) + msg;

		if(Config.STYLE_STRIP_COLOR)
		{
			s = C.stripColor(s);
		}

		if(p.equals(Bukkit.getConsoleSender()))
		{
			console(s);
			return s;
		}

		if(!Surge.isMainThread())
		{
			String ss = s;
			new S("message")
			{
				@Override
				public void run()
				{
					p.sendMessage(ss);
				}
			};
		}

		else
		{
			p.sendMessage(s);
		}

		return s;
	}

	public static void console(String s)
	{
		if(!Config.CONSOLE_COLOR)
		{
			s = C.stripColor(s);
		}

		if(Surge.isMainThread())
		{
			Bukkit.getConsoleSender().sendMessage(s);
		}

		else
		{
			String v = s;

			new S("log")
			{
				@Override
				public void run()
				{
					console(v);
				}
			};
		}
	}

	public static String msg(ReactPlayer p, Notification n)
	{
		syncColor();
		String s = TXT.makeTag(themeColor, darkColor, textColor, Info.CORE_NAME + " - " + C.WHITE + F.capitalizeWords(n.getType().toString().toLowerCase())) + n.getMessage();

		if(Config.STYLE_STRIP_COLOR)
		{
			s = C.stripColor(s);
		}

		p.getP().sendMessage(s);

		return s;
	}

	public static String msgRAI(CommandSender p, String msg)
	{
		syncColor();
		String s = TXT.makeTag(themeColor, darkColor, textColor, "RAI") + msg; //$NON-NLS-1$

		if(Config.STYLE_STRIP_COLOR)
		{
			s = C.stripColor(s);
		}

		if(p.equals(Bukkit.getConsoleSender()))
		{
			console(s);
			return s;
		}

		p.sendMessage(s);

		return s;
	}

	public static GList<CommandSender> broadcastReactUsers()
	{
		GList<CommandSender> s = new GList<CommandSender>();

		for(Player i : Bukkit.getOnlinePlayers())
		{
			if(Permissable.ACCESS.has(i))
			{
				s.add(i);
			}
		}

		s.add(Bukkit.getConsoleSender());

		return s;
	}

	public static String msgSuccess(CommandSender p, String msg)
	{
		String s = msg(p, C.GREEN + "\u2714 " + C.GRAY + msg); //$NON-NLS-1$
		if(p instanceof Player)
		{
			if(snd > 0 && Config.SOUNDS && !Gate.safe)
			{
				((Player) p).playSound(((Player) p).getLocation(), MSound.SUCCESSFUL_HIT.bs(), 0.25f, 1.9f);
				snd--;
			}
		}

		return s;
	}

	public static String msgError(CommandSender p, String msg)
	{
		String s = msg(p, C.RED + "\u2718 " + C.GRAY + msg); //$NON-NLS-1$

		if(p instanceof Player)
		{
			if(snd > 0 && Config.SOUNDS && !Gate.safe)
			{
				((Player) p).playSound(((Player) p).getLocation(), MSound.ENDERDRAGON_HIT.bs(), 0.25f, 1.9f);
				snd--;
			}
		}

		return s;
	}

	public static String msgActing(CommandSender p, String msg)
	{
		String s = msg(p, C.GOLD + "\u26A0 " + C.GRAY + msg); //$NON-NLS-1$

		if(p instanceof Player)
		{
			if(snd > 0 && Config.SOUNDS && !Gate.safe)
			{
				((Player) p).playSound(((Player) p).getLocation(), Config.USE_MEOW ? MSound.CAT_MEOW.bs() : MSound.CHICKEN_EGG_POP.bs(), 0.15f, 1.9f);
				snd--;
//Java7-BS
			}
		}

		return s;
	}

	public static boolean isBadForUnloading()
	{
		return Flavor.getHostFlavor().equals(Flavor.PAPER_SPIGOT) && Protocol.EARLIEST.to(Protocol.R1_8_9).contains(Protocol.getProtocolVersion());
	}

	public static boolean canUnload(World w, int x, int z)
	{
		return !isBadForUnloading();
	}

	public static boolean unloadChunk(Chunk c)
	{
		if(Config.SAFE_MODE_CHUNK)
		{
			return false;
		}

		if(!Config.getWorldConfig(c.getWorld()).allowChunkPurging)
		{
			return false;
		}

		try
		{
			if(!Config.getWorldConfig(c.getWorld()).allowActions)
			{
				return false;
			}

			if(canUnload(c.getWorld(), c.getX(), c.getZ()))
			{
				if(!P.isWithinViewDistance(c))
				{
					return c.unload();
				}
			}

			return false;
		}

		catch(Throwable e)
		{
			Ex.t(e);
			return false;
		}
	}

	public static void unloadChunk(World w, int x, int z)
	{
		if(!canUnload(w, x, z))
		{
			return;
		}

		if(!Config.getWorldConfig(w).allowChunkPurging)
		{
			return;
		}

		if(!Config.getWorldConfig(w).allowActions)
		{
			return;
		}

		w.unloadChunk(x, z);
	}

	private static void removeEntityQuickly(Entity e)
	{
		if(e.getType().equals(EntityType.ARMOR_STAND) && Config.ARMORSTAND_DANGER)
		{
			return;
		}

		if(e instanceof Player)
		{
			return;
		}

		if(Config.getWorldConfig(e.getWorld()).assumeNoSideEffectsEntities.contains(e.getType().toString()))
		{
			return;
		}

		markForDeath(e, 1);
	}

	private static void removeEntity(Entity e)
	{
		if(e.getType().equals(EntityType.ARMOR_STAND) && Config.ARMORSTAND_DANGER)
		{
			return;
		}

		if(e instanceof Player)
		{
			return;
		}

		if(Config.getWorldConfig(e.getWorld()).assumeNoSideEffectsEntities.contains(e.getType().toString()))
		{
			return;
		}

		markForDeath(e, Config.ENTITY_MARK_TIME);
	}

	public static void purgeEntity(Entity e)
	{
		purgeEntity(e, false);
	}

	public static boolean isDangerous(EntityType e)
	{
		if(ed == null)
		{
			ed = new GList<EntityType>();
			ed.add(EntityType.PLAYER);

			if(Config.ARMORSTAND_DANGER)
			{
				ed.add(EntityType.ARMOR_STAND);
			}

			if(Config.ITEMFRAME_DANGER)
			{
				ed.add(EntityType.ITEM_FRAME);
			}
		}

		return ed.contains(e);
	}

	public static void purgeEntity(Entity e, boolean b)
	{
		if(b && !(e instanceof Player))
		{
			removeEntityQuickly(e);
			return;
		}

		if(e instanceof Item && !Config.PURGE_DROPS)
		{
			return;
		}

		if(EntityFlag.NAMED.is(e) && !Config.PURGE_NAMED)
		{
			return;
		}

		if(EntityFlag.TAMED.is(e) && !Config.PURGE_TAMED)
		{
			return;
		}

		if(isMythic(e) && Config.PURGE_MYTHIC_MOBS)
		{
			removeEntityQuickly(e);
			return;
		}

		if(e.getType().equals(EntityType.ARMOR_STAND) && !b && Config.ARMORSTAND_DANGER)
		{
			return;
		}

		if(e.getType().equals(EntityType.ITEM_FRAME) && !b && Config.ITEMFRAME_DANGER)
		{
			return;
		}

		if(Config.getWorldConfig(e.getWorld()).assumeNoSideEffectsEntities.contains(e.getType().toString()) && !b)
		{
			return;
		}

		if(!Config.ALLOW_PURGE.contains(e.getType().toString()))
		{
			return;
		}

		if(e.getType().equals(EntityType.PLAYER))
		{
			return;
		}

		removeEntityQuickly(e);
	}

	public static void cullEntity(Entity e)
	{
		if(!Config.CULLING_ENABLED)
		{
			return;
		}

		if(isMythic(e) && Config.CULL_MYTHIC_MOBS)
		{
			removeEntity(e);
			return;
		}

		if(e.getType().equals(EntityType.ITEM_FRAME) && Config.ITEMFRAME_DANGER)
		{
			return;
		}

		if(e.getType().equals(EntityType.ARMOR_STAND) && Config.ARMORSTAND_DANGER)
		{
			return;
		}

		if(Config.getWorldConfig(e.getWorld()).assumeNoSideEffectsEntities.contains(e.getType().toString()))
		{
			return;
		}

		if(!Config.ALLOW_CULL.contains(e.getType().toString()))
		{
			return;
		}

		if(Config.CULLING_AUTO)
		{
			removeEntityQuickly(e);
		}

		else
		{
			removeEntity(e);
		}
	}

	public static void updateBlock(Block block)
	{
		React.instance.featureController.updateBlock(block);
	}

	public static String header(String string, C color)
	{
		int maxLength = 48;
		int left = string.length() + 2;
		int of = (maxLength - left) / 2;

		return TXT.line(color, of) + C.RESET + " " + string + " " + C.RESET + TXT.line(color, of); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public static String header(C color)
	{
		return TXT.line(color, 48);
	}

	@SuppressWarnings("deprecation")
	public static void updateFluid(Block block)
	{
		int id = block.getTypeId();
		byte byt = block.getData();
		block.setTypeIdAndData(block.getTypeId(), (byte) 0, false);
		block.setTypeIdAndData(id, byt, true);
	}

	public static void refresh(Chunk chunk)
	{
	}

	@SuppressWarnings("deprecation")
	public static void refreshChunks()
	{
		refresh.clear();

		if(Config.SAFE_MODE_NMS)
		{
			return;
		}

		for(Player j : Bukkit.getOnlinePlayers())
		{
			for(Location i : destroy)
			{
				if(j.getWorld().equals(i.getWorld()))
				{
					if(j.getLocation().distanceSquared(i) <= Math.pow(Bukkit.getViewDistance() * 16, 2))
					{
						j.sendBlockChange(i, 0, (byte) 0);
					}
				}
			}
		}

		destroy.clear();
	}

	public static void sendBlockChange(Location l)
	{
		if(Config.SAFE_MODE_NMS)
		{
			return;
		}

		destroy.add(l);
	}

	public static void benchmark(GSet<Object> possibilities, IActionSource source, int ticks, Runnable finished)
	{
		DataCluster cv = new DataCluster();
		GList<Chunk> lax = new GList<Chunk>();

		for(Object i : possibilities)
		{
			Chunk cc = (Chunk) i;
			lax.add(cc);
		}

		Task t1 = new Task("chbench", 0)
		{
			@Override
			public void run()
			{
				LagMap m = EventController.map;

				for(Chunk i : lax)
				{
					LagMapChunk c = m.getChunks().get(i);
					log(cv, "Total Entities", i.getEntities().length);

					if(c != null)
					{
						for(ChunkIssue j : c.getMS().k())
						{
							log(cv, F.capitalizeWords(j.name().toLowerCase().replaceAll("-", " ") + " MS"), c.getMS().get(j));
						}
					}
				}
			}
		};

		new TaskLater("cdel", ticks)
		{
			@Override
			public void run()
			{
				t1.cancel();

				for(String i : cv.keys())
				{
					source.sendResponseSuccess(C.WHITE + i + ": " + C.GRAY + (i.endsWith("MS") ? F.time(cv.getDouble(i), 2) : F.f(cv.getDouble(i), 2)));
				}

				finished.run();
			}
		};
	}

	public static void pullTimingsReport(long time, Callback<String> url)
	{
		if(Config.SAFE_MODE_NMS)
		{
			url.run("http://SAFE-MODE-NMS-ENABLED");
			return;
		}

		NobodySender s = new NobodySender();
		Bukkit.dispatchCommand(s, "timings on");
		s.dump();

		new TaskLater("timings-waiter", (int) (time / 50))
		{
			@Override
			public void run()
			{
				Bukkit.dispatchCommand(s, "timings paste");

				new TaskLater("timings-waitress", 20)
				{
					@Override
					public void run()
					{
						String v = s.pump();
						Bukkit.dispatchCommand(s, "timings off");
						s.dump();
						String m = "";

						for(int i = 0; i < v.length(); i++)
						{
							if(v.substring(i).startsWith("http://") || v.substring(i).startsWith("https://"))
							{
								try
								{
									new URL(v.substring(i));
									m = v.substring(i);
								}

								catch(MalformedURLException e)
								{
									System.out.println("ERROR: " + v.substring(i) + " is not url?");
									return;
								}
							}
						}

						url.run(m);
					}
				};
			}
		};
	}

	public static void log(DataCluster cc, String k, double v)
	{
		if(!cc.contains(k))
		{
			cc.set(k, v);
		}

		else
		{
			cc.set(k, ((cc.getDouble(k) * 19.0) + v) / 20.0);
		}
	}

	public static boolean isBasicallyDead(Entity i)
	{
		return markedForDeath.containsKey(i.getEntityId());
	}

	public static JSONObject dump()
	{
		JSONObject js = new JSONObject();
		JSONObject react = new JSONObject();
		JSONObject rplugin = new JSONObject();
		rplugin.put("react-version", Bukkit.getPluginManager().getPlugin("React").getDescription().getVersion());
		JSONObject capabilities = new JSONObject();
		JSONArray capable = new JSONArray();
		JSONArray notcapable = new JSONArray();
		JSONObject controllers = new JSONObject();
		GList<IController> con = new GList<IController>();
		for(Field j : React.class.getFields())
		{
			if(j.isAnnotationPresent(Control.class))
			{
				try
				{
					con.add((IController) j.get(React.instance));
				}

				catch(Throwable e)
				{
					Ex.t(e);
				}
			}
		}

		controllers.put("active", con.size());
		JSONArray activeCon = new JSONArray();

		for(IController i : con)
		{
			JSONObject jcon = new JSONObject();
			jcon.put("name", i.getClass().getSimpleName());
			jcon.put("tick", F.time(i.getTime(), 5));
			JSONObject prop = new JSONObject();
			i.dump(prop);
			jcon.put("properties", prop);
			activeCon.put(jcon);
		}

		for(Capability i : Capability.capabilities)
		{
			if(i.isCapable())
			{
				capable.put(i.getName());
			}

			else
			{
				notcapable.put(i.getName());
			}
		}

		capabilities.put("capable", capable);
		capabilities.put("not-capable", notcapable);
		react.put("plugin", rplugin);
		react.put("controllers", activeCon);
		react.put("capabilities", capabilities);
		JSONObject timings = new JSONObject();

		for(String i : I.m.k().sortCopy())
		{
			JSONObject tx = new JSONObject();

			double perTick = (double) I.h.get(i) / (double) I.hit;
			String w = "";

			if(perTick > 0.3 && I.m.get(i).getAverage() > 4)
			{
				w = " WARNING";
			}

			tx.put("Average", F.f(I.m.get(i).getAverage(), 5) + " (over " + I.m.get(i).size() + " ticks)");
			tx.put("Hits", F.f(I.h.get(i)) + " hits across " + F.f(I.hit) + " total ticks (about " + F.f(perTick, 2) + " per tick)");
			tx.put("Time", F.f(I.y.get(i), 6) + " Total execution time");

			if(w.length() > 0)
			{
				tx.put("WARNING", "Tick time and hits per tick for " + i + " is high");
			}

			timings.put(i, tx);
		}

		react.put("timings", timings);
		js.put("react", react);

		return js;
	}

	public static void pulse()
	{
		ReactPlugin.i.getPool().tickSyncQueue(true);
	}
}
