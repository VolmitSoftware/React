package primal.bukkit.nms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.netty.channel.Channel;
import org.bukkit.entity.Player;

import primal.bukkit.plugin.PrimalPlugin;
import primal.lang.collection.GMap;

public abstract class CatalystPacketListener implements PacketListener
{
	private TinyProtocol protocol;
	protected GMap<String, String> teamCache;
	private Map<Class<?>, List<PacketHandler<?>>> inHandlers = new HashMap<>();
	private Map<Class<?>, List<PacketHandler<?>>> outHandlers = new HashMap<>();
	private List<PacketHandler<?>> inGlobal = new ArrayList<>();
	private List<PacketHandler<?>> outGlobal = new ArrayList<>();

	public CatalystPacketListener()
	{
		teamCache = new GMap<>();
		PacketCache.reset();
	}

	@Override
	public void openListener()
	{
		if(protocol != null)
		{
			throw new RuntimeException("Listener is already open");
		}

		try
		{
			protocol = new TinyProtocol(PrimalPlugin.instance)
			{
				@Override
				public Object onPacketOutAsync(Player reciever, Channel channel, Object packet)
				{
					Object p = packet;

					for(PacketHandler<?> i : outGlobal)
					{
						p = i.onPacket(reciever, p);

						if(p == null)
						{
							break;
						}
					}

					if(p != null && outHandlers.containsKey(packet.getClass()))
					{
						for(PacketHandler<?> i : outHandlers.get(packet.getClass()))
						{
							p = i.onPacket(reciever, p);

							if(p == null)
							{
								break;
							}
						}
					}

					return p;
				}

				@Override
				public Object onPacketInAsync(Player sender, Channel channel, Object packet)
				{
					Object p = packet;

					for(PacketHandler<?> i : inGlobal)
					{
						p = i.onPacket(sender, p);

						if(p == null)
						{
							break;
						}
					}

					if(p != null && inHandlers.containsKey(packet.getClass()))
					{
						for(PacketHandler<?> i : inHandlers.get(packet.getClass()))
						{
							p = i.onPacket(sender, p);

							if(p == null)
							{
								break;
							}
						}
					}

					return p;
				}
			};
		}

		catch(Throwable e)
		{
			// Derp
		}

		onOpened();
	}

	@Override
	public void closeListener()
	{
		if(protocol == null)
		{
			// Nobody cares
			return;
		}
	
		try
		{
			protocol.close();
		}
	
		catch(Throwable e)
		{
			// Nobody cares
		}
	
		inHandlers.clear();
		outHandlers.clear();
		inGlobal.clear();
		outGlobal.clear();
		protocol = null;
	}

	@Override
	public <T> void addOutgoingListener(Class<? extends T> packetType, PacketHandler<T> handler)
	{
		if(!outHandlers.containsKey(packetType))
		{
			outHandlers.put(packetType, new ArrayList<PacketHandler<?>>());
		}

		outHandlers.get(packetType).add(handler);
	}

	@Override
	public void addGlobalOutgoingListener(PacketHandler<?> handler)
	{
		outGlobal.add(handler);
	}

	@Override
	public <T> void addIncomingListener(Class<? extends T> packetType, PacketHandler<T> handler)
	{
		if(!inHandlers.containsKey(packetType))
		{
			inHandlers.put(packetType, new ArrayList<PacketHandler<?>>());
		}

		inHandlers.get(packetType).add(handler);
	}

	@Override
	public void addGlobalIncomingListener(PacketHandler<?> handler)
	{
		inGlobal.add(handler);
	}

	@Override
	public void removeOutgoingPacketListeners(Class<?> c)
	{
		outHandlers.remove(c);
	}

	@Override
	public void removeOutgoingPacketListeners()
	{
		outHandlers.clear();
	}

	@Override
	public void removeIncomingPacketListeners(Class<?> c)
	{
		inHandlers.remove(c);
	}

	@Override
	public void removeIncomingPacketListeners()
	{
		inHandlers.clear();
	}
}
