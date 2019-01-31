package com.volmit.react.util.nmp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_9_R2.CraftChunk;
import org.bukkit.craftbukkit.v1_9_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_9_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import com.volmit.react.ReactPlugin;
import com.volmit.react.sched.J;

import net.minecraft.server.v1_9_R2.Block;
import net.minecraft.server.v1_9_R2.BlockPosition;
import net.minecraft.server.v1_9_R2.EntityHuman.EnumChatVisibility;
import net.minecraft.server.v1_9_R2.EnumMainHand;
import net.minecraft.server.v1_9_R2.IChatBaseComponent;
import net.minecraft.server.v1_9_R2.NextTickListEntry;
import net.minecraft.server.v1_9_R2.Packet;
import net.minecraft.server.v1_9_R2.PacketPlayInSettings;
import net.minecraft.server.v1_9_R2.PacketPlayOutAnimation;
import net.minecraft.server.v1_9_R2.PacketPlayOutBlockAction;
import net.minecraft.server.v1_9_R2.PacketPlayOutBlockBreakAnimation;
import net.minecraft.server.v1_9_R2.PacketPlayOutBlockChange;
import net.minecraft.server.v1_9_R2.PacketPlayOutChat;
import net.minecraft.server.v1_9_R2.PacketPlayOutGameStateChange;
import net.minecraft.server.v1_9_R2.PacketPlayOutMapChunk;
import net.minecraft.server.v1_9_R2.PacketPlayOutTitle;
import net.minecraft.server.v1_9_R2.PacketPlayOutTitle.EnumTitleAction;
import net.minecraft.server.v1_9_R2.PacketPlayOutUnloadChunk;

public class Catalyst94 extends CatalystPacketListener implements CatalystHost
{
	private Map<Player, PlayerSettings> playerSettings = new HashMap<>();

	@Override
	public void sendAdvancement(Player p, FrameType type, ItemStack is, String text)
	{
		AdvancementHolder94 a = new AdvancementHolder94(UUID.randomUUID().toString());
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

	// START PACKETS
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
		new V(p).set("d", Block.getById(blocktype));

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
		return new PacketPlayOutChat(IChatBaseComponent.ChatSerializer.a("{\"text\": \"" + subtitle + "\"}"), (byte) 2);
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
	// END PACKETS

	private BlockPosition toBlockPos(Location location)
	{
		return new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
	}

	@Override
	public String getServerVersion()
	{
		return "1_12_R1";
	}

	@Override
	public String getVersion()
	{
		return "1.12.X";
	}

	@Override
	public void start()
	{
		openListener();
		Bukkit.getPluginManager().registerEvents(this, ReactPlugin.i);
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
					playerSettings.put(player, new PlayerSettings(new V(s).get("a"), new V(s).get("b"), ChatMode.values()[((EnumChatVisibility) new V(s).get("c")).ordinal()], new V(s).get("d"), new V(s).get("e"), ((EnumMainHand) new V(s).get("f")).equals(EnumMainHand.RIGHT)));
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
		return new ShadowChunk94(at);
	}

	@Override
	public Set<Object> getTickList(World world)
	{
		return new V(((CraftWorld) world).getHandle()).get("nextTickList");
	}

	@Override
	public org.bukkit.block.Block getBlock(World world, Object tickListEntry)
	{
		BlockPosition pos = ((NextTickListEntry) tickListEntry).a;
		return world.getBlockAt(pos.getX(), pos.getY(), pos.getZ());
	}
}
