package primal.bukkit.nms;

public enum Capability
{
	SEND_TITLE;

	private final NMSVersion minVersion;
	private final NMSVersion maxVersion;

	Capability(NMSVersion v)
	{
		this(v, NMSVersion.getMaximum());
	}

	Capability(NMSVersion min, NMSVersion max)
	{
		this.minVersion = min;
		this.maxVersion = max;
	}

	Capability()
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
