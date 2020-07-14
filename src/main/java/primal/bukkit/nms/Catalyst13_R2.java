package primal.bukkit.nms;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_13_R2.CraftChunk;
import org.bukkit.craftbukkit.v1_13_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_13_R2.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_13_R2.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import net.minecraft.server.v1_13_R2.Block;
import net.minecraft.server.v1_13_R2.BlockPosition;
import net.minecraft.server.v1_13_R2.DataWatcher.Item;
import net.minecraft.server.v1_13_R2.DataWatcherObject;
import net.minecraft.server.v1_13_R2.DataWatcherRegistry;
import net.minecraft.server.v1_13_R2.EntityHuman.EnumChatVisibility;
import net.minecraft.server.v1_13_R2.EnumChatFormat;
import net.minecraft.server.v1_13_R2.EnumMainHand;
import net.minecraft.server.v1_13_R2.IChatBaseComponent;
import net.minecraft.server.v1_13_R2.IScoreboardCriteria.EnumScoreboardHealthDisplay;
import net.minecraft.server.v1_13_R2.NextTickListEntry;
import net.minecraft.server.v1_13_R2.Packet;
import net.minecraft.server.v1_13_R2.PacketPlayInEntityAction;
import net.minecraft.server.v1_13_R2.PacketPlayInFlying;
import net.minecraft.server.v1_13_R2.PacketPlayInSettings;
import net.minecraft.server.v1_13_R2.PacketPlayOutAnimation;
import net.minecraft.server.v1_13_R2.PacketPlayOutBlockAction;
import net.minecraft.server.v1_13_R2.PacketPlayOutBlockBreakAnimation;
import net.minecraft.server.v1_13_R2.PacketPlayOutBlockChange;
import net.minecraft.server.v1_13_R2.PacketPlayOutEntity;
import net.minecraft.server.v1_13_R2.PacketPlayOutEntity.PacketPlayOutRelEntityMove;
import net.minecraft.server.v1_13_R2.PacketPlayOutEntityDestroy;
import net.minecraft.server.v1_13_R2.PacketPlayOutEntityMetadata;
import net.minecraft.server.v1_13_R2.PacketPlayOutEntityTeleport;
import net.minecraft.server.v1_13_R2.PacketPlayOutGameStateChange;
import net.minecraft.server.v1_13_R2.PacketPlayOutHeldItemSlot;
import net.minecraft.server.v1_13_R2.PacketPlayOutMapChunk;
import net.minecraft.server.v1_13_R2.PacketPlayOutMount;
import net.minecraft.server.v1_13_R2.PacketPlayOutPlayerListHeaderFooter;
import net.minecraft.server.v1_13_R2.PacketPlayOutScoreboardDisplayObjective;
import net.minecraft.server.v1_13_R2.PacketPlayOutScoreboardObjective;
import net.minecraft.server.v1_13_R2.PacketPlayOutScoreboardScore;
import net.minecraft.server.v1_13_R2.PacketPlayOutScoreboardTeam;
import net.minecraft.server.v1_13_R2.PacketPlayOutSetSlot;
import net.minecraft.server.v1_13_R2.PacketPlayOutSpawnEntity;
import net.minecraft.server.v1_13_R2.PacketPlayOutTitle;
import net.minecraft.server.v1_13_R2.PacketPlayOutTitle.EnumTitleAction;
import net.minecraft.server.v1_13_R2.PacketPlayOutUnloadChunk;
import net.minecraft.server.v1_13_R2.ScoreboardServer;
import net.minecraft.server.v1_13_R2.TickListServer;
import net.minecraft.server.v1_13_R2.WorldServer;
import primal.bukkit.plugin.PrimalPlugin;
import primal.bukkit.sched.J;
import primal.bukkit.world.MaterialBlock;
import primal.lang.collection.GList;
import primal.lang.collection.GSet;
import primal.util.text.C;

public class Catalyst13_R2 extends CatalystPacketListener implements CatalystHost
{
	private Map<Player, PlayerSettings> playerSettings = new HashMap<>();

	@Override
	public void sendAdvancement(Player p, FrameType type, ItemStack is, String text)
	{
		AdvancementHolder13_R2 a = new AdvancementHolder13_R2(UUID.randomUUID().toString());
		a.withToast(true);
		a.withDescription("?");
		a.withFrame(type);
		a.withAnnouncement(false);
		a.withTitle(text);
		a.withTrigger("minecraft:impossible");
		a.withIcon(is.getData());
		a.withBackground("minecraft:textures/blocks/bedrock.png");
		a.loadAdvancement();
		a.sendPlayer(p);
		J.s(() -> a.delete(p), 5);
	}

	@Override
	public Object packetChunkUnload(int x, int z)
	{
		return new PacketPlayOutUnloadChunk(x, z);
	}

	@Override
	public Object packetChunkFullSend(Chunk chunk)
	{
		return new PacketPlayOutMapChunk(((CraftChunk) chunk).getHandle(), 65535);
	}

	@Override
	public Object packetBlockChange(Location block, int blockId, byte blockData)
	{
		PacketPlayOutBlockChange p = new PacketPlayOutBlockChange();
		new V(p).set("a", toBlockPos(block));
		new V(p).set("b", Block.getByCombinedId(blockId << 4 | (blockData & 15)));

		return p;
	}

	@Override
	public Object packetBlockAction(Location block, int action, int param, int blocktype)
	{
		PacketPlayOutBlockAction p = new PacketPlayOutBlockAction();
		new V(p).set("a", toBlockPos(block));
		new V(p).set("b", action);
		new V(p).set("c", param);
		new V(p).set("d", Block.getByCombinedId(blocktype).getBlock());

		return p;
	}

	@Override
	public Object packetAnimation(int eid, int animation)
	{
		PacketPlayOutAnimation p = new PacketPlayOutAnimation();
		new V(p).set("a", eid);
		new V(p).set("b", animation);

		return p;
	}

	@Override
	public Object packetBlockBreakAnimation(int eid, Location location, byte damage)
	{
		return new PacketPlayOutBlockBreakAnimation(eid, toBlockPos(location), damage);
	}

	@Override
	public Object packetGameState(int mode, float value)
	{
		return new PacketPlayOutGameStateChange(mode, value);
	}

	@Override
	public Object packetTitleMessage(String title)
	{
		return new PacketPlayOutTitle(EnumTitleAction.TITLE, IChatBaseComponent.ChatSerializer.a("{\"text\": \"" + title + "\"}"));
	}

	@Override
	public Object packetSubtitleMessage(String subtitle)
	{
		return new PacketPlayOutTitle(EnumTitleAction.SUBTITLE, IChatBaseComponent.ChatSerializer.a("{\"text\": \"" + subtitle + "\"}"));
	}

	@Override
	public Object packetActionBarMessage(String subtitle)
	{
		return new PacketPlayOutTitle(EnumTitleAction.ACTIONBAR, IChatBaseComponent.ChatSerializer.a("{\"text\": \"" + subtitle + "\"}"));
	}

	@Override
	public Object packetResetTitle()
	{
		return new PacketPlayOutTitle(EnumTitleAction.RESET, null);
	}

	@Override
	public Object packetClearTitle()
	{
		return new PacketPlayOutTitle(EnumTitleAction.CLEAR, null);
	}

	@Override
	public Object packetTimes(int in, int stay, int out)
	{
		return new PacketPlayOutTitle(in, stay, out);
	}

	private BlockPosition toBlockPos(Location location)
	{
		return new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
	}

	@Override
	public String getServerVersion()
	{
		return "1_13_R2";
	}

	@Override
	public String getVersion()
	{
		return "1.13.X";
	}

	@Override
	public void start()
	{
		openListener();
		Bukkit.getPluginManager().registerEvents(this, PrimalPlugin.instance);
	}

	@Override
	public void stop()
	{
		closeListener();
		HandlerList.unregisterAll(this);
	}

	@Override
	public void onOpened()
	{
		addGlobalIncomingListener(new PacketHandler<Object>()
		{
			@Override
			public Object onPacket(Player player, Object packet)
			{
				if(packet instanceof PacketPlayInSettings)
				{
					PacketPlayInSettings s = (PacketPlayInSettings) packet;
					playerSettings.put(player, new PlayerSettings(new V(s).get("a"), new V(s).get("viewDistance"), ChatMode.values()[((EnumChatVisibility) new V(s).get("c")).ordinal()], new V(s).get("d"), new V(s).get("e"), ((EnumMainHand) new V(s).get("f")).equals(EnumMainHand.RIGHT)));
				}

				return packet;
			}
		});
	}

	@Override
	public void sendPacket(Player p, Object o)
	{
		((CraftPlayer) p).getHandle().playerConnection.sendPacket((Packet<?>) o);
	}

	@Override
	public void sendRangedPacket(double radius, Location l, Object o)
	{
		for(Player i : l.getWorld().getPlayers())
		{
			if(canSee(l, i) && l.distanceSquared(i.getLocation()) <= radius * radius)
			{
				sendPacket(i, o);
			}
		}
	}

	@Override
	public void sendGlobalPacket(World w, Object o)
	{
		for(Player i : w.getPlayers())
		{
			sendPacket(i, o);
		}
	}

	@Override
	public void sendUniversalPacket(Object o)
	{
		for(Player i : Bukkit.getOnlinePlayers())
		{
			sendPacket(i, o);
		}
	}

	@Override
	public void sendViewDistancedPacket(Chunk c, Object o)
	{
		for(Player i : getObservers(c))
		{
			sendPacket(i, o);
		}
	}

	@Override
	public boolean canSee(Chunk c, Player p)
	{
		return isWithin(p.getLocation().getChunk(), c, getViewDistance(p));
	}

	@Override
	public boolean canSee(Location l, Player p)
	{
		return canSee(l.getChunk(), p);
	}

	@Override
	public int getViewDistance(Player p)
	{
		try
		{
			return getSettings(p).getViewDistance();
		}

		catch(Throwable e)
		{

		}

		return Bukkit.getServer().getViewDistance();
	}

	public boolean isWithin(Chunk center, Chunk check, int viewDistance)
	{
		return Math.abs(center.getX() - check.getX()) <= viewDistance && Math.abs(center.getZ() - check.getZ()) <= viewDistance;
	}

	@Override
	public List<Player> getObservers(Chunk c)
	{
		List<Player> p = new ArrayList<>();

		for(Player i : c.getWorld().getPlayers())
		{
			if(canSee(c, i))
			{
				p.add(i);
			}
		}

		return p;
	}

	@Override
	public List<Player> getObservers(Location l)
	{
		return getObservers(l.getChunk());
	}

	@EventHandler
	public void on(PlayerQuitEvent e)
	{
		playerSettings.remove(e.getPlayer());
	}

	@Override
	public PlayerSettings getSettings(Player p)
	{
		return playerSettings.get(p);
	}

	@Override
	public ShadowChunk shadowCopy(Chunk at)
	{
		return new ShadowChunk13_R2(at);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Set<Object> getTickList(World world)
	{
		try
		{
			Field f = WorldServer.class.getDeclaredField("nextTickListBlock");
			Field ff = TickListServer.class.getDeclaredField("nextTickList");
			f.setAccessible(true);
			ff.setAccessible(true);
			TickListServer<?> l = (TickListServer<?>) f.get(((CraftWorld) world).getHandle());
			return (Set<Object>) ff.get(l);
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}

		return new GSet<>();
	}

	@SuppressWarnings("unchecked")
	@Override
	public Set<Object> getTickListFluid(World world)
	{
		try
		{
			Field f = WorldServer.class.getDeclaredField("nextTickListFluid");
			Field ff = TickListServer.class.getDeclaredField("nextTickList");
			f.setAccessible(true);
			ff.setAccessible(true);
			TickListServer<?> l = (TickListServer<?>) f.get(((CraftWorld) world).getHandle());
			return (Set<Object>) ff.get(l);
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}

		return new GSet<>();
	}

	@Override
	public org.bukkit.block.Block getBlock(World world, Object tickListEntry)
	{
		BlockPosition pos = ((NextTickListEntry<?>) tickListEntry).a;
		return world.getBlockAt(pos.getX(), pos.getY(), pos.getZ());
	}

	@Override
	public Object packetTabHeaderFooter(String h, String f)
	{
		PacketPlayOutPlayerListHeaderFooter p = new PacketPlayOutPlayerListHeaderFooter();
		new V(p).set("header", IChatBaseComponent.ChatSerializer.a("{\"text\": \"" + h + "\"}"));
		new V(p).set("footer", IChatBaseComponent.ChatSerializer.a("{\"text\": \"" + f + "\"}"));

		return p;
	}

	@Override
	public void scroll(Player sender, int previous)
	{
		sendPacket(sender, new PacketPlayOutHeldItemSlot(previous));
	}

	@Override
	public int getAction(Object packetIn)
	{
		return ((PacketPlayInEntityAction) packetIn).c().ordinal();
	}

	@Override
	public Vector getDirection(Object packet)
	{
		float yaw = 0;
		float pitch = 0;

		try
		{
			Field a = PacketPlayInFlying.class.getDeclaredField("yaw");
			Field b = PacketPlayInFlying.class.getDeclaredField("pitch");
			a.setAccessible(true);
			b.setAccessible(true);
			yaw = (float) a.get(packet);
			pitch = (float) b.get(packet);
		}

		catch(Exception e)
		{

		}

		double pitchRadians = Math.toRadians(-pitch);
		double yawRadians = Math.toRadians(-yaw);
		double sinPitch = Math.sin(pitchRadians);
		double cosPitch = Math.cos(pitchRadians);
		double sinYaw = Math.sin(yawRadians);
		double cosYaw = Math.cos(yawRadians);
		Vector v = new Vector(-cosPitch * sinYaw, sinPitch, -cosPitch * cosYaw);
		return new Vector(-v.getX(), v.getY(), -v.getZ());
	}

	@Override
	public void spawnFallingBlock(int eid, UUID id, Location l, Player player, MaterialBlock mb)
	{
		@SuppressWarnings("deprecation")
		int bid = mb.getMaterial().getId() + (mb.getData() << 12);
		PacketPlayOutSpawnEntity m = new PacketPlayOutSpawnEntity();
		new V(m).set("a", eid);
		new V(m).set("b", id);
		new V(m).set("c", l.getX());
		new V(m).set("d", l.getY());
		new V(m).set("e", l.getZ());
		new V(m).set("f", 0);
		new V(m).set("g", 0);
		new V(m).set("h", 0);
		new V(m).set("i", 0);
		new V(m).set("j", 0);
		new V(m).set("k", 70);
		new V(m).set("l", bid);
		sendPacket(player, m);
	}

	@Override
	public void removeEntity(int eid, Player p)
	{
		PacketPlayOutEntityDestroy d = new PacketPlayOutEntityDestroy(eid);
		sendPacket(p, d);
	}

	@Override
	public void moveEntityRelative(int eid, Player p, double x, double y, double z, boolean onGround)
	{
		try
		{
			PacketPlayOutRelEntityMove r = new PacketPlayOutRelEntityMove();
			Field a = PacketPlayOutEntity.class.getDeclaredField("a");
			Field b = PacketPlayOutEntity.class.getDeclaredField("b");
			Field c = PacketPlayOutEntity.class.getDeclaredField("c");
			Field d = PacketPlayOutEntity.class.getDeclaredField("d");
			Field e = PacketPlayOutEntity.class.getDeclaredField("e");
			Field f = PacketPlayOutEntity.class.getDeclaredField("f");
			Field g = PacketPlayOutEntity.class.getDeclaredField("g");
			Field h = PacketPlayOutEntity.class.getDeclaredField("h");
			a.setAccessible(true);
			b.setAccessible(true);
			c.setAccessible(true);
			d.setAccessible(true);
			e.setAccessible(true);
			f.setAccessible(true);
			g.setAccessible(true);
			h.setAccessible(true);
			a.set(r, eid);
			b.set(r, (int) (x * 4096));
			c.set(r, (int) (y * 4096));
			d.set(r, (int) (z * 4096));
			e.set(r, (byte) 0);
			f.set(r, (byte) 0);
			g.set(r, onGround);
			h.set(r, onGround);
			sendPacket(p, r);
		}

		catch(NoSuchFieldException e)
		{
			e.printStackTrace();
		}

		catch(SecurityException e)
		{
			e.printStackTrace();
		}

		catch(IllegalArgumentException e1)
		{
			e1.printStackTrace();
		}

		catch(IllegalAccessException e1)
		{
			e1.printStackTrace();
		}
	}

	@Override
	public void teleportEntity(int eid, Player p, Location l, boolean onGround)
	{
		PacketPlayOutEntityTeleport t = new PacketPlayOutEntityTeleport();
		new V(t).set("a", eid);
		new V(t).set("b", l.getX());
		new V(t).set("c", l.getY());
		new V(t).set("d", l.getZ());
		new V(t).set("e", (byte) 0);
		new V(t).set("f", (byte) 0);
		new V(t).set("g", onGround);
		sendPacket(p, t);
	}

	@Override
	public void spawnArmorStand(int eid, UUID id, Location l, int data, Player player)
	{
		PacketPlayOutSpawnEntity m = new PacketPlayOutSpawnEntity();
		new V(m).set("a", eid);
		new V(m).set("b", id);
		new V(m).set("c", l.getX());
		new V(m).set("d", l.getY());
		new V(m).set("e", l.getZ());
		new V(m).set("f", 0);
		new V(m).set("g", 0);
		new V(m).set("h", 0);
		new V(m).set("i", 0);
		new V(m).set("j", 0);
		new V(m).set("k", 78);
		new V(m).set("l", 0);
		sendPacket(player, m);
	}

	private IChatBaseComponent s(String s)
	{
		return IChatBaseComponent.ChatSerializer.a("{\"text\": \"" + s + "\"}");
	}

	@Override
	public void sendTeam(Player p, String id, String name, String prefix, String suffix, C color, int mode)
	{
		PacketPlayOutScoreboardTeam k = new PacketPlayOutScoreboardTeam();
		new V(k).set("a", id);
		new V(k).set("b", s(name));
		new V(k).set("i", mode); // 0 = new, 1 = remove, 2 = update, 3 = addplayer, 4 = removeplayer
		new V(k).set("c", s(prefix));
		new V(k).set("d", s(suffix));
		new V(k).set("j", 0);
		new V(k).set("f", "never");
		new V(k).set("e", "always");
		new V(k).set("g", EnumChatFormat.valueOf(color.name().replaceAll("MAGIC", "OBFUSCATED")));
		sendPacket(p, k);
	}

	@Override
	public void addTeam(Player p, String id, String name, String prefix, String suffix, C color)
	{
		sendTeam(p, id, name, prefix, suffix, color, 0);
	}

	@Override
	public void updateTeam(Player p, String id, String name, String prefix, String suffix, C color)
	{
		sendTeam(p, id, name, prefix, suffix, color, 2);
	}

	@Override
	public void removeTeam(Player p, String id)
	{
		sendTeam(p, id, "", "", "", C.WHITE, 1);
	}

	@Override
	public void addToTeam(Player p, String id, String... entities)
	{
		PacketPlayOutScoreboardTeam k = new PacketPlayOutScoreboardTeam();
		new V(k).set("a", id);
		new V(k).set("i", 3);
		Collection<String> h = new V(k).get("h");
		h.addAll(new GList<String>(entities));
		sendPacket(p, k);
	}

	@Override
	public void removeFromTeam(Player p, String id, String... entities)
	{
		PacketPlayOutScoreboardTeam k = new PacketPlayOutScoreboardTeam();
		new V(k).set("a", id);
		new V(k).set("i", 4);
		Collection<String> h = new V(k).get("h");
		h.addAll(new GList<String>(entities));
		sendPacket(p, k);
	}

	@Override
	public void displayScoreboard(Player p, int slot, String id)
	{
		PacketPlayOutScoreboardDisplayObjective k = new PacketPlayOutScoreboardDisplayObjective();
		new V(k).set("a", slot);
		new V(k).set("b", id);
		sendPacket(p, k);
	}

	@Override
	public void displayScoreboard(Player p, C slot, String id)
	{
		displayScoreboard(p, 3 + slot.getMeta(), id);
	}

	@Override
	public void sendNewObjective(Player p, String id, String name)
	{
		PacketPlayOutScoreboardObjective k = new PacketPlayOutScoreboardObjective();
		new V(k).set("d", 0);
		new V(k).set("a", id);
		new V(k).set("b", s(name));
		new V(k).set("c", EnumScoreboardHealthDisplay.INTEGER);
		sendPacket(p, k);
	}

	@Override
	public void sendDeleteObjective(Player p, String id)
	{
		PacketPlayOutScoreboardObjective k = new PacketPlayOutScoreboardObjective();
		new V(k).set("d", 1);
		new V(k).set("a", id);
		new V(k).set("b", s("memes"));
		new V(k).set("c", EnumScoreboardHealthDisplay.INTEGER);
		sendPacket(p, k);
	}

	@Override
	public void sendEditObjective(Player p, String id, String name)
	{
		PacketPlayOutScoreboardObjective k = new PacketPlayOutScoreboardObjective();
		new V(k).set("d", 2);
		new V(k).set("a", id);
		new V(k).set("b", s(name));
		new V(k).set("c", EnumScoreboardHealthDisplay.INTEGER);
		sendPacket(p, k);
	}

	@Override
	public void sendScoreUpdate(Player p, String name, String objective, int score)
	{
		PacketPlayOutScoreboardScore k = new PacketPlayOutScoreboardScore();
		new V(k).set("a", name);
		new V(k).set("b", objective);
		new V(k).set("c", score);
		new V(k).set("d", ScoreboardServer.Action.CHANGE);
		sendPacket(p, k);
	}

	@Override
	public void sendScoreRemove(Player p, String name, String objective)
	{
		PacketPlayOutScoreboardScore k = new PacketPlayOutScoreboardScore();
		new V(k).set("a", name);
		new V(k).set("b", objective);
		new V(k).set("c", 0);
		new V(k).set("d", ScoreboardServer.Action.REMOVE);
		sendPacket(p, k);
	}

	@Override
	public void sendRemoveGlowingColorMetaEntity(Player p, UUID glowing)
	{
		String c = teamCache.get(p.getUniqueId() + "-" + glowing);

		if(c != null)
		{
			teamCache.remove(p.getUniqueId() + "-" + glowing);
			removeFromTeam(p, c, glowing.toString());
			removeTeam(p, c);
		}
	}

	@Override
	public void sendRemoveGlowingColorMetaPlayer(Player p, UUID glowing, String name)
	{
		String c = teamCache.get(p.getUniqueId() + "-" + glowing);

		if(c != null)
		{
			teamCache.remove(p.getUniqueId() + "-" + glowing);
			removeFromTeam(p, c, name);
			removeTeam(p, c);
		}
	}

	@Override
	public void sendGlowingColorMeta(Player p, Entity glowing, C color)
	{
		if(glowing instanceof Player)
		{
			sendGlowingColorMetaName(p, p.getName(), color);
		}

		else
		{
			sendGlowingColorMetaEntity(p, glowing.getUniqueId(), color);
		}
	}

	@Override
	public void sendGlowingColorMetaEntity(Player p, UUID euid, C color)
	{
		sendGlowingColorMetaName(p, euid.toString(), color);
	}

	@Override
	public void sendGlowingColorMetaName(Player p, String euid, C color)
	{
		String c = teamCache.get(p.getUniqueId() + "-" + euid);

		if(c != null)
		{
			updateTeam(p, c, c, color.toString(), C.RESET.toString(), color);
			sendEditObjective(p, c, c);
		}

		else
		{
			c = "v" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 15);
			teamCache.put(p.getUniqueId() + "-" + euid, c);

			addTeam(p, c, c, color.toString(), C.RESET.toString(), color);
			updateTeam(p, c, c, color.toString(), C.RESET.toString(), color);

			addToTeam(p, c, euid.toString());
		}
	}

	@Override
	public void sendRemoveGlowingColorMeta(Player p, Entity glowing)
	{
		String c = teamCache.get(p.getUniqueId() + "-" + glowing.getUniqueId());

		if(c != null)
		{
			teamCache.remove(p.getUniqueId() + "-" + glowing.getUniqueId());
			removeFromTeam(p, c, glowing instanceof Player ? glowing.getName() : glowing.getUniqueId().toString());
			removeTeam(p, c);
		}
	}

	@Override
	public void updatePassengers(Player p, int vehicle, int... passengers)
	{
		PacketPlayOutMount mount = new PacketPlayOutMount();
		new V(mount).set("a", vehicle);
		new V(mount).set("b", passengers);
		sendPacket(p, mount);
	}

	@Override
	public void sendEntityMetadata(Player p, int eid, Object... objects)
	{
		PacketPlayOutEntityMetadata md = new PacketPlayOutEntityMetadata();
		new V(md).set("a", eid);
		List<Item<?>> items = new GList<Item<?>>();

		for(Object i : objects)
		{
			items.add((Item<?>) i);
		}

		new V(md).set("b", items);
		sendPacket(p, md);
	}

	@Override
	public void sendEntityMetadata(Player p, int eid, List<Object> objects)
	{
		sendEntityMetadata(p, eid, objects.toArray(new Object[objects.size()]));
	}

	@Override
	public Object getMetaEntityRemainingAir(int airTicksLeft)
	{
		return new Item<Integer>(new DataWatcherObject<>(1, DataWatcherRegistry.b), airTicksLeft);
	}

	@Override
	public Object getMetaEntityCustomName(String name)
	{
		Optional<IChatBaseComponent> c = Optional.ofNullable(s(name));
		return new Item<Optional<IChatBaseComponent>>(new DataWatcherObject<>(2, DataWatcherRegistry.f), c);
	}

	@Override
	public Object getMetaEntityProperties(boolean onFire, boolean crouched, boolean sprinting, boolean swimming, boolean invisible, boolean glowing, boolean flyingElytra)
	{
		byte bits = 0;
		bits += onFire ? 1 : 0;
		bits += crouched ? 2 : 0;
		bits += sprinting ? 8 : 0;
		bits += swimming ? 10 : 0;
		bits += invisible ? 20 : 0;
		bits += glowing ? 40 : 0;
		bits += flyingElytra ? 80 : 0;

		return new Item<Byte>(new DataWatcherObject<>(0, DataWatcherRegistry.a), bits);
	}

	@Override
	public Object getMetaEntityGravity(boolean gravity)
	{
		return new Item<Boolean>(new DataWatcherObject<>(5, DataWatcherRegistry.i), gravity);
	}

	@Override
	public Object getMetaEntitySilenced(boolean silenced)
	{
		return new Item<Boolean>(new DataWatcherObject<>(4, DataWatcherRegistry.i), silenced);
	}

	@Override
	public Object getMetaEntityCustomNameVisible(boolean visible)
	{
		return new Item<Boolean>(new DataWatcherObject<>(3, DataWatcherRegistry.i), visible);
	}

	@Override
	public Object getMetaArmorStandProperties(boolean isSmall, boolean hasArms, boolean noBasePlate, boolean marker)
	{
		byte bits = 0;
		bits += isSmall ? 1 : 0;
		bits += hasArms ? 2 : 0;
		bits += noBasePlate ? 8 : 0;
		bits += marker ? 10 : 0;

		return new Item<Byte>(new DataWatcherObject<>(11, DataWatcherRegistry.a), bits);
	}

	@Override
	public void sendItemStack(Player p, ItemStack is, int slot)
	{
		sendPacket(p, new PacketPlayOutSetSlot(((CraftPlayer) p).getHandle().activeContainer.windowId, slot, CraftItemStack.asNMSCopy(is)));
	}
}