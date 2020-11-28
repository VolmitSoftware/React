package com.volmit.react.api;

import org.bukkit.Bukkit;

import com.volmit.react.util.F;

public enum Flavor
{
	ANY,
	SAFE_MODE,
	CRAFT_BUKKIT,
	BUKKIT,
	SPIGOT,
	SOGGY_SPIGOT,
	PAPER_SPIGOT,
	TACO_SPIGOT,
	TORCH_SPIGOT,
	FORGE_HACK,
	SPONGE_HACK;

	private static Flavor flavorCache = null;

	public static Flavor getHostFlavor()
	{
		if (flavorCache != null) return flavorCache;

		String v = Bukkit.getVersion().toLowerCase();

		if(v.contains("paper"))
		{
			return flavorCache = Flavor.PAPER_SPIGOT;
		}

		if(v.contains("taco"))
		{
			return flavorCache = Flavor.TACO_SPIGOT;
		}

		if(v.contains("torch"))
		{
			return flavorCache = Flavor.TORCH_SPIGOT;
		}

		if(v.contains("thermos"))
		{
			return flavorCache = Flavor.FORGE_HACK;
		}

		if(v.contains("cauldron"))
		{
			return flavorCache = Flavor.FORGE_HACK;
		}

		if(v.contains("sponge"))
		{
			return flavorCache = Flavor.SPONGE_HACK;
		}

		if(v.contains("spigot"))
		{
			return flavorCache = Flavor.SPIGOT;
		}

		if(v.contains("craftbukkit"))
		{
			return flavorCache = Flavor.CRAFT_BUKKIT;
		}

		if(v.contains("bukkit"))
		{
			return flavorCache = Flavor.BUKKIT;
		}

		return flavorCache = Flavor.SAFE_MODE;
	}

	public boolean compatableWith(Flavor flavor)
	{
		if(flavor.equals(SOGGY_SPIGOT) && (getHostFlavor().equals(PAPER_SPIGOT) || getHostFlavor().equals(SPIGOT)))
		{
			return true;
		}

		return getHostFlavor().equals(flavor) || flavor.equals(ANY);
	}

	public String fancyName()
	{
		return F.capitalizeWords(name().replaceAll("_", " ").toLowerCase());
	}
}
