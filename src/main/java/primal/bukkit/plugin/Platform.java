package primal.bukkit.plugin;

public class Platform
{
	public static boolean isBukkit()
	{
		try
		{
			Class.forName("org.bukkit.Bukkit");
			return true;
		}

		catch(Throwable e)
		{
		}
		return false;
	}

	public static boolean isBungeecord()
	{
		try
		{
			Class.forName("net.md_5.bungee.api.plugin.Plugin");
			return true;
		}

		catch(Throwable e)
		{

		}
		return false;
	}
}
