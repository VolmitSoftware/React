package primal.bukkit.plugin;

import java.io.File;

import primal.compute.math.M;

/**
 * Server platform tools
 *
 * @author cyberpwn
 *
 */
public class PlatformServer
{
	/**
	 * Get the time when the server started up (uses server.properties modification
	 * date)
	 *
	 * @return the time when the server fully started up.
	 */
	public static long getStartupTime()
	{
		return new File("server.properties").lastModified();
	}

	/**
	 * Returns the time the server has been online. Reloading does not reset this.
	 *
	 * @return the ACTAUL server uptime
	 */
	public static long getUpTime()
	{
		return M.ms() - getStartupTime();
	}
}
