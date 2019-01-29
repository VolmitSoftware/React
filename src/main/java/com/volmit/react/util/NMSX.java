package com.volmit.react.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.volmit.react.api.Capability;

public class NMSX
{
	public static NMSX bountifulAPI;
	private static boolean useOldMethods;
	public static String nmsver;
	public static Object eTimes = null;
	public static Object eTitle = null;
	public static Object eSubtitle = null;

	public static String getVersion()
	{
		final String name = Bukkit.getServer().getClass().getPackage().getName();
		final String version = name.substring(name.lastIndexOf('.') + 1) + ".";
		return version;
	}

	public static void setAi(LivingEntity e, boolean ai)
	{
		try
		{
			if(VersionBukkit.uc())
			{
				e.setAI(ai);
			}
		}

		catch(Throwable ex)
		{
			Ex.t(ex);
		}
	}

	/**
	 * Get the nms class
	 *
	 * @param className
	 *            the class name
	 * @return returns "net.minecraft.server." + version + "." + className
	 */
	public static Class<?> getCBNMSClass(String className)
	{
		final String fullName = "net.minecraft.server." + getVersion() + className;

		Class<?> clazz = null;

		try
		{
			clazz = Class.forName(fullName);
		}

		catch(Throwable e)
		{

		}

		return clazz;
	}

	public static Class<?> getCBClass(String className)
	{
		final String fullName = "org.bukkit.craftbukkit." + getVersion() + className;

		Class<?> clazz = null;

		try
		{
			clazz = Class.forName(fullName);
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}

		return clazz;
	}

	public static Object serializeChat(String msg)
	{
		try
		{
			return NMSClass.ChatSerializer.getDeclaredMethod("a", String.class).invoke(null, msg);
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}

		return null;
	}

	public static Field setAccessible(Field f) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException
	{
		f.setAccessible(true);
		Field modifiersField = Field.class.getDeclaredField("modifiers");
		modifiersField.setAccessible(true);
		modifiersField.setInt(f, f.getModifiers() & 0xFFFFFFEF);

		return f;
	}

	public static Method setAccessible(Method m) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException
	{
		m.setAccessible(true);

		return m;
	}

	/**
	 * Get the bukkit version
	 *
	 * @return the package sub for the nms version
	 */
	public static String getBukkitVersion()
	{
		return Bukkit.getServer().getClass().getPackage().getName().substring(23);
	}

	/**
	 * Get the base nms package
	 *
	 * @return the nms package
	 */
	public static String nmsPackage()
	{
		return "net.minecraft.server." + getBukkitVersion();
	}

	/**
	 * Get the crafting package
	 *
	 * @return the entire package
	 */
	public static String craftPackage()
	{
		return "org.bukkit.craftbukkit." + getBukkitVersion();
	}

	/**
	 * Pick up something
	 *
	 * @param entity
	 *            the entity
	 * @param pick
	 *            the item
	 */
	public static void showPickup(Player player, Entity entity, Entity pick)
	{
		try
		{
			Class<?> craftPlayer = Class.forName(craftPackage() + ".entity.CraftPlayer");
			Class<?> packetGameStateChange = Class.forName(nmsPackage() + ".PacketPlayOutCollect");
			Object handle = craftPlayer.getMethod("getHandle").invoke(player);
			Object packet = packetGameStateChange.getConstructor(int.class, int.class).newInstance(pick.getEntityId(), entity.getEntityId());
			Object playerConnection = handle.getClass().getDeclaredField("playerConnection").get(handle);
			playerConnection.getClass().getMethod("sendPacket", Class.forName(nmsPackage() + ".Packet")).invoke(playerConnection, packet);
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}

	public static void sendPacket(Player player, Object packet)
	{
		try
		{
			Object handle = player.getClass().getMethod("getHandle", new Class[0]).invoke((Object) player, new Object[0]);
			Object playerConnection = handle.getClass().getField("playerConnection").get(handle);
			playerConnection.getClass().getMethod("sendPacket", NMSX.getNMSClass("Packet")).invoke(playerConnection, packet);
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}

	public static Class<?> getNMSClass(String name)
	{
		String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];

		try
		{
			return Class.forName("net.minecraft.server." + version + "." + name);
		}

		catch(Throwable e)
		{
			Ex.t(e);
			return null;
		}
	}

	public static void sendTitle(Player player, Integer fadeIn, Integer stay, Integer fadeOut, String title, String subtitle)
	{
		if(title.trim().isEmpty() && subtitle.trim().isEmpty())
		{
			return;
		}

		try
		{
			Object e;

			if(eTimes == null)
			{
				eTimes = NMSX.getNMSClass("PacketPlayOutTitle").getDeclaredClasses()[0].getField("TIMES").get(null);
			}

			if(eTitle == null)
			{
				eTitle = NMSX.getNMSClass("PacketPlayOutTitle").getDeclaredClasses()[0].getField("TITLE").get(null);
			}

			if(eSubtitle == null)
			{
				eSubtitle = NMSX.getNMSClass("PacketPlayOutTitle").getDeclaredClasses()[0].getField("SUBTITLE").get(null);
			}

			Constructor<?> subtitleConstructor;

			if(title != null)
			{
				title = ChatColor.translateAlternateColorCodes((char) '&', (String) title);
				title = title.replaceAll("%player%", player.getDisplayName());
				e = eTimes;
				Object chatTitle = NMSX.getNMSClass("IChatBaseComponent").getDeclaredClasses()[0].getMethod("a", String.class).invoke(null, "{\"text\":\"" + title + "\"}");
				subtitleConstructor = NMSX.getNMSClass("PacketPlayOutTitle").getConstructor(NMSX.getNMSClass("PacketPlayOutTitle").getDeclaredClasses()[0], NMSX.getNMSClass("IChatBaseComponent"), Integer.TYPE, Integer.TYPE, Integer.TYPE);
				Object titlePacket = subtitleConstructor.newInstance(e, chatTitle, fadeIn, stay, fadeOut);
				NMSX.sendPacket(player, titlePacket);
				e = eTitle;
				chatTitle = NMSX.getNMSClass("IChatBaseComponent").getDeclaredClasses()[0].getMethod("a", String.class).invoke(null, "{\"text\":\"" + title + "\"}");
				subtitleConstructor = NMSX.getNMSClass("PacketPlayOutTitle").getConstructor(NMSX.getNMSClass("PacketPlayOutTitle").getDeclaredClasses()[0], NMSX.getNMSClass("IChatBaseComponent"));
				titlePacket = subtitleConstructor.newInstance(e, chatTitle);
				NMSX.sendPacket(player, titlePacket);
			}

			if(subtitle != null)
			{
				subtitle = ChatColor.translateAlternateColorCodes((char) '&', (String) subtitle);
				subtitle = subtitle.replaceAll("%player%", player.getDisplayName());

				e = eTimes;

				Object chatSubtitle = NMSX.getNMSClass("IChatBaseComponent").getDeclaredClasses()[0].getMethod("a", String.class).invoke(null, "{\"text\":\"" + title + "\"}");
				subtitleConstructor = NMSX.getNMSClass("PacketPlayOutTitle").getConstructor(NMSX.getNMSClass("PacketPlayOutTitle").getDeclaredClasses()[0], NMSX.getNMSClass("IChatBaseComponent"), Integer.TYPE, Integer.TYPE, Integer.TYPE);
				Object subtitlePacket = subtitleConstructor.newInstance(e, chatSubtitle, fadeIn, stay, fadeOut);
				NMSX.sendPacket(player, subtitlePacket);

				e = eSubtitle;
				chatSubtitle = NMSX.getNMSClass("IChatBaseComponent").getDeclaredClasses()[0].getMethod("a", String.class).invoke(null, "{\"text\":\"" + subtitle + "\"}");
				subtitleConstructor = NMSX.getNMSClass("PacketPlayOutTitle").getConstructor(NMSX.getNMSClass("PacketPlayOutTitle").getDeclaredClasses()[0], NMSX.getNMSClass("IChatBaseComponent"), Integer.TYPE, Integer.TYPE, Integer.TYPE);
				subtitlePacket = subtitleConstructor.newInstance(e, chatSubtitle, fadeIn, stay, fadeOut);
				NMSX.sendPacket(player, subtitlePacket);
			}
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}

	public static void clearTitle(Player player)
	{
		NMSX.sendTitle(player, 0, 0, 0, "", "");
	}

	public static void sendTabTitle(Player player, String header, String footer)
	{
		if(header == null)
		{
			header = "";
		}

		header = ChatColor.translateAlternateColorCodes((char) '&', (String) header);

		if(footer == null)
		{
			footer = "";
		}

		footer = ChatColor.translateAlternateColorCodes((char) '&', (String) footer);
		header = header.replaceAll("%player%", player.getDisplayName());
		footer = footer.replaceAll("%player%", player.getDisplayName());

		try
		{
			Object tabHeader = NMSX.getNMSClass("IChatBaseComponent").getDeclaredClasses()[0].getMethod("a", String.class).invoke(null, "{\"text\":\"" + header + "\"}");
			Object tabFooter = NMSX.getNMSClass("IChatBaseComponent").getDeclaredClasses()[0].getMethod("a", String.class).invoke(null, "{\"text\":\"" + footer + "\"}");
			Constructor<?> titleConstructor = NMSX.getNMSClass("PacketPlayOutPlayerListHeaderFooter").getConstructor(NMSX.getNMSClass("IChatBaseComponent"));
			Object packet = titleConstructor.newInstance(tabHeader);
			Field field = packet.getClass().getDeclaredField("b");
			field.setAccessible(true);
			field.set(packet, tabFooter);
			NMSX.sendPacket(player, packet);
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}

	public static void sendActionBar(Player player, String message)
	{
		if(!Capability.ACTION_BAR.isCapable())
		{
			return;
		}

		if(!VersionBukkit.tc())
		{
			return;
		}

		try
		{
			Object ppoc;
			Class<?> c3;
			Class<?> c2;
			Class<?> c1 = Class.forName("org.bukkit.craftbukkit." + nmsver + ".entity.CraftPlayer");
			Object p = c1.cast((Object) player);
			Class<?> c4 = Class.forName("net.minecraft.server." + nmsver + ".PacketPlayOutChat");
			Class<?> c5 = Class.forName("net.minecraft.server." + nmsver + ".Packet");

			if(useOldMethods)
			{
				c2 = Class.forName("net.minecraft.server." + nmsver + ".ChatSerializer");
				c3 = Class.forName("net.minecraft.server." + nmsver + ".IChatBaseComponent");
				Method m3 = c2.getDeclaredMethod("a", String.class);
				Object cbc = c3.cast(m3.invoke(c2, "{\"text\": \"" + message + "\"}"));
				ppoc = c4.getConstructor(c3, Byte.TYPE).newInstance(cbc, Byte.valueOf((byte) 2));
			}

			else
			{
				c2 = Class.forName("net.minecraft.server." + nmsver + ".ChatComponentText");
				c3 = Class.forName("net.minecraft.server." + nmsver + ".IChatBaseComponent");
				Object o = c2.getConstructor(String.class).newInstance(message);

				if(VersionBukkit.get().equals(VersionBukkit.V113))
				{
					Class<?> c6 = Class.forName("net.minecraft.server." + nmsver + ".ChatMessageType");
					Object type = c6.getMethod("valueOf", String.class).invoke(null, "GAME_INFO");
					ppoc = c4.getConstructor(c3, c6).newInstance(o, type);
				}

				else if(VersionBukkit.get().equals(VersionBukkit.V112))
				{
					Class<?> c6 = Class.forName("net.minecraft.server." + nmsver + ".ChatMessageType");
					Object type = c6.getMethod("valueOf", String.class).invoke(null, "GAME_INFO");
					ppoc = c4.getConstructor(c3, c6).newInstance(o, type);
				}

				else
				{
					ppoc = c4.getConstructor(c3, Byte.TYPE).newInstance(o, Byte.valueOf((byte) 2));
				}
			}

			Method m1 = c1.getDeclaredMethod("getHandle", new Class[0]);
			Object h = m1.invoke(p, new Object[0]);
			Field f1 = h.getClass().getDeclaredField("playerConnection");
			Object pc = f1.get(h);
			Method m5 = pc.getClass().getDeclaredMethod("sendPacket", c5);
			m5.invoke(pc, ppoc);
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}

	public static void sendActionBar(final Player player, final String message, int duration)
	{
		NMSX.sendActionBar(player, message);
		if(duration >= 0)
		{
			new BukkitRunnable()
			{
				@Override
				public void run()
				{
					NMSX.sendActionBar(player, "");
				}
			}.runTaskLater((Plugin) bountifulAPI, (long) (duration + 1));
		}

		while(duration > 60)
		{
			int sched = (duration -= 60) % 60;
			new BukkitRunnable()
			{

				@Override
				public void run()
				{
					NMSX.sendActionBar(player, message);
				}
			}.runTaskLater((Plugin) bountifulAPI, (long) sched);
		}
	}

	public static void sendActionBarToAllPlayers(String message)
	{
		NMSX.sendActionBarToAllPlayers(message, -1);
	}

	public static void sendActionBarToAllPlayers(String message, int duration)
	{
		for(Player p : Bukkit.getOnlinePlayers())
		{
			NMSX.sendActionBar(p, message, duration);
		}
	}

	public static String getEntityName(Entity e)
	{
		if(!VersionBukkit.tc())
		{
			return null;
		}

		return e.getCustomName();
	}

	public static int ping(Player player)
	{
		try
		{
			String bukkitversion = Bukkit.getServer().getClass().getPackage().getName().substring(23);
			Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit." + bukkitversion + ".entity.CraftPlayer");
			Object handle = craftPlayer.getMethod("getHandle").invoke(player);
			Integer ping = (Integer) handle.getClass().getDeclaredField("ping").get(handle);

			return ping.intValue();
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}

		return -1;
	}

	static
	{
		nmsver = Bukkit.getServer().getClass().getPackage().getName();
		nmsver = nmsver.substring(nmsver.lastIndexOf(".") + 1);

		if(nmsver.equalsIgnoreCase("v1_8_R1") || nmsver.equalsIgnoreCase("v1_7_"))
		{
			useOldMethods = true;
		}
	}
}
