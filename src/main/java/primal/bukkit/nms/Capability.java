package primal.bukkit.nms;

public enum Capability
{
	SEND_TITLE;

	private NMSVersion minVersion;
	private NMSVersion maxVersion;

	private Capability(NMSVersion v)
	{
		this(v, NMSVersion.getMaximum());
	}

	private Capability(NMSVersion min, NMSVersion max)
	{
		this.minVersion = min;
		this.maxVersion = max;
	}

	private Capability()
	{
		this(NMSVersion.getMinimum(), NMSVersion.getMaximum());
	}

	public NMSVersion getMinVersion()
	{
		return minVersion;
	}

	public NMSVersion getMaxVersion()
	{
		return maxVersion;
	}

	public boolean isCapable()
	{
		try
		{
			return getMinVersion().betweenInclusive(maxVersion).contains(NMSVersion.current());
		}

		catch(Throwable e)
		{

		}

		return false;
	}
}
