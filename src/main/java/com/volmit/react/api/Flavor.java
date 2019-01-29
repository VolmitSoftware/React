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

	public static Flavor getHostFlavor()
	{
		String v = Bukkit.getVersion().toLowerCase();

		if(v.contains("paper"))
		{
			return Flavor.PAPER_SPIGOT;
		}

		if(v.contains("taco"))
		{
			return Flavor.TACO_SPIGOT;
		}

		if(v.contains("torch"))
		{
			return Flavor.TORCH_SPIGOT;
		}

		if(v.contains("thermos"))
		{
			return Flavor.FORGE_HACK;
		}

		if(v.contains("cauldron"))
		{
			return Flavor.FORGE_HACK;
		}

		if(v.contains("sponge"))
		{
			return Flavor.SPONGE_HACK;
		}

		if(v.contains("spigot"))
		{
			return Flavor.SPIGOT;
		}

		if(v.contains("craftbukkit"))
		{
			return Flavor.CRAFT_BUKKIT;
		}

		if(v.contains("bukkit"))
		{
			return Flavor.BUKKIT;
		}

		return Flavor.SAFE_MODE;
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
