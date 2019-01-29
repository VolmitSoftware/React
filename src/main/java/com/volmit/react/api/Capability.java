package com.volmit.react.api;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import com.volmit.react.Gate;
import com.volmit.react.Lang;
import com.volmit.react.util.C;
import com.volmit.react.util.Protocol;
import com.volmit.volume.lang.collections.GList;

public class Capability
{
	public static final Capability MYTHIC_MOBS = new Capability("Mythic Mobs Support", Protocol.R1_8, "MythicMobs");
	public static final Capability DUAL_WEILD = new Capability(Lang.getString("react.capabilities.dual-wield"), Protocol.R1_9); //$NON-NLS-1$
	public static final Capability PLACEHOLDERS = new Capability("Placeholders", Protocol.R1_7_10, "PlaceholderAPI"); //$NON-NLS-1$
	public static final Capability SCOREBOARD = new Capability(Lang.getString("react.capabilities.scoreboard"), Protocol.B1_5).max(Protocol.R1_12_2); //$NON-NLS-1$
	public static final Capability SCOREBOARD_ADVANCED = new Capability(Lang.getString("react.capabilities.adv-scoreboard"), Protocol.R1_8).max(Protocol.R1_12_2); //$NON-NLS-1$
	public static final Capability HOPPER = new Capability(Lang.getString("react.capabilities.hopper"), Protocol.B1_5); //$NON-NLS-1$
	public static final Capability ENTITY_ATTRIBUTES = new Capability(Lang.getString("react.capabilities.entity-att"), Protocol.R1_12); //$NON-NLS-1$
	public static final Capability ENTITY_AI = new Capability(Lang.getString("react.capabilities.entity-ai"), Protocol.R1_9); //$NON-NLS-1$
	public static final Capability ACTION_BAR = new Capability(Lang.getString("react.capabilities.action-bars"), Protocol.R1_8); //$NON-NLS-1$
	public static final Capability ENTITY_NAMES = new Capability(Lang.getString("react.capabilities.entity-naming"), Protocol.R1_8); //$NON-NLS-1$
	public static final Capability TITLE_BAR = new Capability(Lang.getString("react.capabilities.title-bar"), Protocol.R1_7_10); //$NON-NLS-1$
	public static final Capability CHUNK_RELIGHTING = new Capability(Lang.getString("react.capabilities.chunk-relighting"), Lang.getString("react.capabilities.fawe")).max(Protocol.R1_12_2); //$NON-NLS-1$ //$NON-NLS-2$
	public static final Capability MONITOR_SUBMISSIVENESS = new Capability(Lang.getString("react.capabilities.monitor-submissive"), Protocol.R1_7_1, Lang.getString("react.capabilities.proto")); //$NON-NLS-1$ //$NON-NLS-2$
	public static final Capability ACCELERATED_PING = new Capability(Lang.getString("react.capabilities.ping"), Protocol.R1_8, Lang.getString("react.capabilities.proto")); //$NON-NLS-1$ //$NON-NLS-2$
	public static final Capability STREAM_PROFILING = new Capability("Stream Profiling", Protocol.R1_8, Lang.getString("react.capabilities.proto")); //$NON-NLS-1$ //$NON-NLS-2$
	public static final Capability ENTITY_THROTTLING = new Capability(Lang.getString("react.capabilities.throttling"), Protocol.R1_8, Flavor.SOGGY_SPIGOT); //$NON-NLS-1$
	public static final Capability TILE_THROTTLING = new Capability(Lang.getString("react.capabilities.tile-throttling"), Protocol.R1_8, Flavor.SOGGY_SPIGOT); //$NON-NLS-1$
	public static final Capability PASSENGERS = new Capability(Lang.getString("react.capabilities.passengers"), Protocol.R1_11); //$NON-NLS-1$
	public static final Capability FAST_MAPPING = new Capability("Fast Mapping", Protocol.R1_8).max(Protocol.R1_12_2); //$NON-NLS-1$
	public static final Capability ARROW_OWNER = new Capability("Arrow Owner", Protocol.R1_12); //$NON-NLS-1$
	public static final GList<Capability> capabilities = new GList<Capability>();

	static
	{
		capabilities.add(DUAL_WEILD);
		capabilities.add(PLACEHOLDERS);
		capabilities.add(SCOREBOARD);
		capabilities.add(SCOREBOARD_ADVANCED);
		capabilities.add(HOPPER);
		capabilities.add(ENTITY_ATTRIBUTES);
		capabilities.add(ENTITY_AI);
		capabilities.add(ACTION_BAR);
		capabilities.add(ENTITY_NAMES);
		capabilities.add(TITLE_BAR);
		capabilities.add(CHUNK_RELIGHTING);
		capabilities.add(STREAM_PROFILING);
		capabilities.add(MONITOR_SUBMISSIVENESS);
		capabilities.add(ACCELERATED_PING);
		capabilities.add(ENTITY_THROTTLING);
		capabilities.add(TILE_THROTTLING);
		capabilities.add(PASSENGERS);
		capabilities.add(ARROW_OWNER);
		capabilities.add(FAST_MAPPING);
		capabilities.add(MYTHIC_MOBS);
	}

	private final Protocol version;
	private final Flavor flavor;
	private final String plugin;
	private final String name;
	private Protocol max;

	public Capability(String name, Protocol version, Flavor flavor, String plugin)
	{
		max = Protocol.LATEST;
		this.name = name;
		this.version = version;
		this.flavor = flavor;
		this.plugin = plugin;
	}

	public Capability max(Protocol p)
	{
		max = p;
		return this;
	}

	public Capability(String name, Protocol version, Flavor flavor)
	{
		this(name, version, flavor, null);
	}

	public Capability(String name, Protocol version, String plugin)
	{
		this(name, version, Flavor.ANY, plugin);
	}

	public Capability(String name, String plugin)
	{
		this(name, Protocol.EARLIEST, Flavor.ANY, plugin);
	}

	public Capability(String name, Flavor flavor)
	{
		this(name, Protocol.EARLIEST, flavor, null);
	}

	public Capability(String name, Protocol version)
	{
		this(name, version, Flavor.ANY);
	}

	public boolean isPluginCapable()
	{
		if(plugin != null)
		{
			return Bukkit.getPluginManager().getPlugin(plugin) != null;
		}

		return true;
	}

	public boolean isFlavorCapable()
	{
		return Flavor.getHostFlavor().compatableWith(getFlavor());
	}

	public boolean isVersionCapable()
	{
		return version.to(max).contains(Protocol.getProtocolVersion());
	}

	public boolean isCapable()
	{
		return isPluginCapable() && isVersionCapable() && isFlavorCapable();
	}

	public Protocol getVersion()
	{
		return version;
	}

	public Flavor getFlavor()
	{
		return flavor;
	}

	public String getPlugin()
	{
		return plugin;
	}

	public String getName()
	{
		return name;
	}

	public void sendNotCapable(CommandSender sender)
	{
		if(!this.isFlavorCapable())
		{
			Gate.msgError(sender, C.RED + this.getName() + C.GRAY + Lang.getString("react.capabilities.requires") + C.WHITE + this.getFlavor().fancyName() + C.GRAY + ")"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		else if(!this.isPluginCapable())
		{
			Gate.msgError(sender, C.RED + this.getName() + C.GRAY + Lang.getString("react.capabilities.requires") + C.WHITE + this.getPlugin() + C.GRAY + ")"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		else if(!this.isVersionCapable())
		{
			if(!max.equals(Protocol.LATEST))
			{
				Gate.msgError(sender, C.RED + this.getName() + C.GRAY + Lang.getString("react.capabilities.requires") + C.WHITE + this.getVersion().getVersionString() + " - " + this.max.getVersionString() + C.GRAY + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}

			else
			{
				Gate.msgError(sender, C.RED + this.getName() + C.GRAY + Lang.getString("react.capabilities.requires") + C.WHITE + this.getVersion().getVersionString() + "+" + C.GRAY + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
		}
	}

	public void sendNotCapable(IActionSource source)
	{
		if(!this.isFlavorCapable())
		{
			source.sendResponseError(C.RED + this.getName() + C.GRAY + Lang.getString("react.capabilities.requires") + C.WHITE + this.getFlavor().fancyName() + C.GRAY + ")"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		else if(!this.isPluginCapable())
		{
			source.sendResponseError(C.RED + this.getName() + C.GRAY + Lang.getString("react.capabilities.requires") + C.WHITE + this.getPlugin() + C.GRAY + ")"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		else if(!this.isVersionCapable())
		{
			if(!max.equals(Protocol.LATEST))
			{
				source.sendResponseError(this.getName() + C.GRAY + Lang.getString("react.capabilities.requires") + C.WHITE + this.getVersion().getVersionString() + " - " + this.max.getVersionString() + C.GRAY + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}

			else
			{
				source.sendResponseError(this.getName() + C.GRAY + Lang.getString("react.capabilities.requires") + C.WHITE + this.getVersion().getVersionString() + "+" + C.GRAY + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
		}
	}
}
